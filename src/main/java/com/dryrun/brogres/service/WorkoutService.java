package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.model.WorkoutResponseDtos.WorkoutExerciseViewDto;
import com.dryrun.brogres.model.WorkoutResponseDtos.WorkoutPrefillDto;
import com.dryrun.brogres.model.WorkoutResponseDtos.WorkoutSummaryDto;
import com.dryrun.brogres.model.WorkoutSubmitRequestDto;
import com.dryrun.brogres.data.WorkoutSet;
import com.dryrun.brogres.data.WorkoutSetStatus;
import com.dryrun.brogres.mapper.WorkoutSummaryMapper;
import com.dryrun.brogres.data.AppUser;
import com.dryrun.brogres.data.Exercise;
import com.dryrun.brogres.repo.AppUserRepository;
import com.dryrun.brogres.repo.ExerciseRepository;
import com.dryrun.brogres.repo.WorkoutRepository;
import com.dryrun.brogres.repo.WorkoutSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkoutService {

    private final WorkoutFactory workoutFactory;
    private final AppUserRepository appUserRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutRepository workoutRepository;
    private final WorkoutSetRepository workoutSetRepository;
    private final WorkoutSummaryMapper workoutSummaryMapper;

    /**
     * Full replace for today: delete all existing sets for the day’s workout, then insert the POST payload.
     * Status comes from the client except {@link WorkoutSetStatus#NEXT}, which is stored as {@link WorkoutSetStatus#DONE}
     * (the “current” set was just completed).
     */
    @Transactional
    public Workout createWorkout(Long userId, WorkoutSubmitRequestDto request) {
        LocalDate today = LocalDate.now();
        AppUser user = appUserRepository.findById(userId).orElseThrow();

        Optional<Workout> existing = workoutRepository.findByWorkoutDateAndUser_Id(today, userId);
        Workout workout;
        if (existing.isPresent()) {
            workout = existing.get();
            workoutSetRepository.deleteAllByWorkoutId(workout.getId());
            workoutSetRepository.flush();
        } else {
            workout = workoutFactory.createWorkout();
            workout.setWorkoutDate(today);
            workout.setUser(user);
            workout = workoutRepository.save(workout);
        }

        persistSetsForWorkout(workout, userId, request);
        log.info("Workout saved: workoutId={}, date={}, sets={}", workout.getId(), workout.getWorkoutDate(), request.exercises().size());
        return workout;
    }

    /**
     * Full replace of sets for an existing workout (any date). Used when editing a session from summary.
     */
    @Transactional
    public Workout replaceWorkout(Long userId, Long workoutId, WorkoutSubmitRequestDto request) {
        Workout workout = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found"));
        if (!workout.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found");
        }
        workoutSetRepository.deleteAllByWorkoutId(workout.getId());
        workoutSetRepository.flush();
        persistSetsForWorkout(workout, userId, request);
        log.info("Workout replaced: workoutId={}, date={}, sets={}", workout.getId(), workout.getWorkoutDate(), request.exercises().size());
        return workout;
    }

    /** Inserts request rows as {@link WorkoutSet} lines in {@code lineOrder} sequence (workout must be persisted). */
    private void persistSetsForWorkout(Workout workout, Long userId, WorkoutSubmitRequestDto request) {
        int lineOrder = 0;
        List<WorkoutSet> setsToSave = new ArrayList<>();
        for (WorkoutSubmitRequestDto.WorkoutExerciseDto exerciseDto : request.exercises()) {
            WorkoutSet workoutSet = new WorkoutSet();
            workoutSet.setWorkout(workout);
            workoutSet.setBodyPart(exerciseDto.bodyPartName());
            workoutSet.setExercise(resolveExerciseForRow(userId, exerciseDto));
            workoutSet.setWeight(exerciseDto.weight());
            workoutSet.setRepetitions(exerciseDto.reps());
            workoutSet.setLineOrder(lineOrder++);
            workoutSet.setStatus(persistStatusFromSubmit(exerciseDto.status()));
            setsToSave.add(workoutSet);
        }
        workoutSetRepository.saveAll(setsToSave);
    }

    private Exercise resolveExerciseForRow(Long userId, WorkoutSubmitRequestDto.WorkoutExerciseDto dto) {
        // Prefer an explicit exerciseId (strong reference) when the client provides it.
        if (dto.exerciseId() != null) {
            // Fetch and validate that the referenced Exercise exists.
            Exercise e = exerciseRepository.findById(dto.exerciseId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown exercise"));

            // Safety check: prevent mixing an exercise with a different body part than the row declares.
            if (!e.getBodyPart().equals(dto.bodyPartName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exercise does not match body part");
            }

            // Access control: user can reference either catalog exercises (user == null) or their own.
            if (e.getUser() != null && !e.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exercise not accessible");
            }
            return e;
        }

        // Fallback path: resolve by (bodyPart + name), optionally creating a user-owned exercise when missing.
        return resolveExerciseByName(userId, dto.bodyPartName(), dto.name());
    }

    /**
     * Resolves catalog vs user-owned exercise; creates a user row when the name is not found
     * (same {@code bodyPart} + label as the client sent).
     */
    private Exercise resolveExerciseByName(Long userId, String bodyPart, String name) {
        // Normalize the label so lookups and uniqueness behave consistently.
        String trimmed = name.trim();

        // Resolution order:
        // 1) user-owned exact match, 2) global catalog match, 3) create a new user-owned definition.
        return exerciseRepository
                .findByUser_IdAndBodyPartAndName(userId, bodyPart, trimmed)
                .or(() -> exerciseRepository.findByUserIsNullAndBodyPartAndName(bodyPart, trimmed))
                .orElseGet(() -> persistUserOwnedExercise(userId, bodyPart, trimmed));
    }

    private Exercise persistUserOwnedExercise(Long userId, String bodyPart, String name) {
        // Create a minimal Exercise entity owned by the user (no catalog linkage).
        Exercise e = new Exercise();
        e.setUser(appUserRepository.getReferenceById(userId)); // reference proxy is enough for FK
        e.setBodyPart(bodyPart);
        e.setName(name);
        e.setSortOrder(0); // UI-only ordering hint (default)
        return exerciseRepository.save(e);
    }

    /**
     * Maps FE status to DB: only {@code NEXT} becomes {@link WorkoutSetStatus#DONE}; {@code null} defaults to DONE.
     */
    private static WorkoutSetStatus persistStatusFromSubmit(WorkoutSetStatus fromFe) {
        if (fromFe == null) {
            return WorkoutSetStatus.DONE;
        }
        if (fromFe == WorkoutSetStatus.NEXT) {
            return WorkoutSetStatus.DONE;
        }
        return fromFe;
    }

    @Transactional(readOnly = true)
    public WorkoutPrefillDto prefillWorkout(Long userId) {
        log.info("Prefill requested");
        LocalDate today = LocalDate.now();

        boolean hasTodayWorkout = workoutRepository.existsByWorkoutDateAndUser_Id(today, userId);
        if (hasTodayWorkout) {
            Optional<Workout> todayWorkoutOpt = workoutRepository.findByWorkoutDateAndUser_Id(today, userId);
            if (todayWorkoutOpt.isEmpty()) {
                return WorkoutPrefillDto.empty();
            }

            Workout todayWorkout = todayWorkoutOpt.get();
            var fullToday = workoutSummaryMapper.toPrefillTodayFullWorkout(todayWorkout);
            var withNextMarked = markPrefillHeadAsNext(fullToday);

            return new WorkoutPrefillDto(withNextMarked);
        }

        Optional<Workout> previousWorkoutOpt =
                workoutRepository.findFirstByUser_IdAndWorkoutDateLessThanOrderByWorkoutDateDesc(userId, today);
        if (previousWorkoutOpt.isEmpty()) {
            return WorkoutPrefillDto.empty();
        }

        Workout previousWorkout = previousWorkoutOpt.get();
        var previousSessionAsPlan = workoutSummaryMapper.toPrefillFromPreviousSession(previousWorkout);
        var withNextMarked = markPrefillHeadAsNext(previousSessionAsPlan);

        return new WorkoutPrefillDto(withNextMarked);
    }

    /**
     * For today’s workout prefill: rows persisted as {@link WorkoutSetStatus#DONE} are shown as {@link WorkoutSetStatus#PLANNED}
     * in the DTO (queue / plan view), then {@link #markPrefillHeadAsNext} runs.
     */
    private static List<WorkoutExerciseViewDto> mapDoneToPlannedForPrefill(List<WorkoutExerciseViewDto> bodyPart) {
        if (bodyPart == null || bodyPart.isEmpty()) {
            return bodyPart;
        }
        return bodyPart.stream()
                .map(e -> e.status() == WorkoutSetStatus.DONE
                        ? new WorkoutExerciseViewDto(
                                e.bodyPartName(),
                                e.name(),
                                e.exerciseId(),
                                e.orderId(),
                                e.weight(),
                                e.reps(),
                                WorkoutSetStatus.PLANNED)
                        : e)
                .toList();
    }

    /**
     * Among plan rows only (PLANNED / NEXT), the first in global {@code orderId} order becomes {@link WorkoutSetStatus#NEXT}
     * in the DTO; other plan rows become {@link WorkoutSetStatus#PLANNED}. {@link WorkoutSetStatus#DONE} rows are left as-is
     * (today’s prefill should call {@link #mapDoneToPlannedForPrefill} first so DONE does not appear as completed in the modal).
     */
    private static List<WorkoutExerciseViewDto> markPrefillHeadAsNext(List<WorkoutExerciseViewDto> bodyPart) {
        if (bodyPart == null || bodyPart.isEmpty()) {
            return bodyPart;
        }
        Optional<WorkoutExerciseViewDto> head = bodyPart.stream()
                .filter(e -> e.status() == WorkoutSetStatus.PLANNED || e.status() == WorkoutSetStatus.NEXT)
                .min(PREFILL_PLAN_ROW_ORDER);
        if (head.isEmpty()) {
            return bodyPart;
        }
        WorkoutExerciseViewDto h = head.get();
        return bodyPart.stream()
                .map(e -> {
                    if (e.status() == WorkoutSetStatus.DONE) {
                        return e;
                    }
                    // Change only head row to NEXT, all others keep PLANNED.
                    WorkoutSetStatus s = samePlanRow(e, h) ? WorkoutSetStatus.NEXT : WorkoutSetStatus.PLANNED;
                    return new WorkoutExerciseViewDto(
                            e.bodyPartName(), e.name(), e.exerciseId(), e.orderId(), e.weight(), e.reps(), s);
                })
                .toList();
    }

    private static final Comparator<WorkoutExerciseViewDto> PREFILL_PLAN_ROW_ORDER =
            Comparator.comparingInt(WorkoutExerciseViewDto::orderId)
                    .thenComparing(WorkoutExerciseViewDto::bodyPartName)
                    .thenComparing(WorkoutExerciseViewDto::name)
                    .thenComparing(WorkoutExerciseViewDto::weight, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparingInt(WorkoutExerciseViewDto::reps);

    private static boolean samePlanRow(WorkoutExerciseViewDto a, WorkoutExerciseViewDto b) {
        return a.orderId() == b.orderId()
                && a.bodyPartName().equals(b.bodyPartName())
                && a.name().equals(b.name())
                && Objects.equals(a.exerciseId(), b.exerciseId())
                && Objects.equals(a.weight(), b.weight())
                && a.reps() == b.reps();
    }

    @Transactional(readOnly = true)
    public List<WorkoutSummaryDto> listWorkouts(Long userId) {
        List<WorkoutSummaryDto> result = workoutRepository.findAllByUser_IdOrderByWorkoutDateDesc(userId).stream()
                .map(workoutSummaryMapper::toSummary)
                .toList();
        log.info("Listed workouts: count={}", result.size());
        return result;
    }
}
