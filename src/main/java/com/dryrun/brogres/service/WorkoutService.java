package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.model.WorkoutResponseDtos.RecentPlanTemplateDto;
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
import com.dryrun.brogres.util.WorkoutPlanUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkoutService {

    /**
     * Caps distinct templates per response (limits payload size; extra historical patterns are dropped after sort).
     */
    private static final int RECENT_PLAN_TEMPLATES_LIMIT = 12;

    private final WorkoutFactory workoutFactory;
    private final AppUserRepository appUserRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutRepository workoutRepository;
    private final WorkoutSetRepository workoutSetRepository;
    private final WorkoutSummaryMapper workoutSummaryMapper;

    /**
     * Full replace for today: delete all existing sets for the day’s workout, then insert the POST payload.
     * Statuses are stored as-sent ({@link WorkoutSetStatus#PLANNED} / {@link WorkoutSetStatus#DONE});
     * {@code null} defaults to {@link WorkoutSetStatus#DONE} (legacy clients that didn’t send a status).
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
     * Full replace of sets for an existing workout (any date). Used when editing a session from summary,
     * and also called on every progress-bar / exercise drop on the FE (each drop = new snapshot).
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
            // DB requires non-null weight; treat missing payload as zero (bodyweight-style logging).
            workoutSet.setWeight(exerciseDto.weight() != null ? exerciseDto.weight() : BigDecimal.ZERO);
            workoutSet.setRepetitions(exerciseDto.reps());
            workoutSet.setLineOrder(lineOrder++);
            // Null status is treated as DONE for legacy clients (pre–progress-bar).
            workoutSet.setStatus(exerciseDto.status() != null ? exerciseDto.status() : WorkoutSetStatus.DONE);
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
     * Prefill for the next session: returns the actual per-set status ({@link WorkoutSetStatus#PLANNED}
     * / {@link WorkoutSetStatus#DONE}) for today’s workout if one exists, or the previous session’s
     * rows flattened to {@link WorkoutSetStatus#PLANNED} (progress bar at the top) when there is no
     * workout for today yet.
     */
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

            // Today's sets are returned with their persisted statuses — the bar position is derived on FE
            // from the count of leading DONE rows.
            Workout todayWorkout = todayWorkoutOpt.get();
            var fullToday = workoutSummaryMapper.toPrefillTodayFullWorkout(todayWorkout);
            return new WorkoutPrefillDto(fullToday);
        }

        // No workout today: clone previous session as a pure plan (everything PLANNED, bar at the top).
        Optional<Workout> previousWorkoutOpt =
                workoutRepository.findFirstByUser_IdAndWorkoutDateLessThanOrderByWorkoutDateDesc(userId, today);
        if (previousWorkoutOpt.isEmpty()) {
            return WorkoutPrefillDto.empty();
        }

        Workout previousWorkout = previousWorkoutOpt.get();
        var previousSessionAsPlan = workoutSummaryMapper.toPrefillFromPreviousSession(previousWorkout);
        return new WorkoutPrefillDto(previousSessionAsPlan);
    }

    /**
     * Removes today’s workout for the current user: deletes all {@link WorkoutSet} lines then the {@link Workout} row.
     * Only a workout with {@code workoutDate == today} (server calendar) and owned by {@code userId} may be deleted.
     */
    @Transactional
    public void deleteTodaysWorkout(Long userId, Long workoutId) {
        Workout workout = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found"));
        if (!workout.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found");
        }
        LocalDate today = LocalDate.now();
        if (!workout.getWorkoutDate().equals(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only today's workout can be deleted");
        }
        workoutSetRepository.deleteAllByWorkoutId(workoutId);
        workoutSetRepository.flush();
        workoutRepository.deleteById(workoutId);
        log.info("Workout deleted: workoutId={}, date={}", workoutId, today);
    }

    @Transactional(readOnly = true)
    public List<WorkoutSummaryDto> listWorkouts(Long userId) {
        List<WorkoutSummaryDto> result = workoutRepository.findAllByUser_IdOrderByWorkoutDateDesc(userId).stream()
                .map(workoutSummaryMapper::toSummary)
                .toList();
        log.info("Listed workouts: count={}", result.size());
        return result;
    }

    /**
     * Past workouts only ({@code workoutDate} &lt; today), grouped by full-session exercise-id signature
     * (see {@link com.dryrun.brogres.util.WorkoutPlanUtils#calcSignature(Workout)}); per signature the latest session;
     * sorted by that session date descending; capped.
     */
    @Transactional(readOnly = true)
    public List<RecentPlanTemplateDto> listRecentPlanTemplates(Long userId) {
        // Today defines the cutoff: carousel is built from closed history only (strictly before this date).
        LocalDate today = LocalDate.now();
        // Newest past sessions first so the first time we see a signature we already hold its latest workout.
        List<Workout> pastNewestFirst =
                workoutRepository.findAllByUser_IdAndWorkoutDateLessThanOrderByWorkoutDateDesc(userId, today);

        List<Workout> ordered = WorkoutPlanUtils.latestWorkoutPerPlanKeyDescending(pastNewestFirst);

        // Map each winning session to a DTO: bodyPart matches global prefill-from-history (all PLANNED rows for the editor).
        List<RecentPlanTemplateDto> out = ordered.stream()
                .limit(RECENT_PLAN_TEMPLATES_LIMIT)
                .map(w -> {
                    String planKey = WorkoutPlanUtils.calcSignature(w);
                    var bodyPart = workoutSummaryMapper.toPrefillFromPreviousSession(w);
                    return new RecentPlanTemplateDto(
                            planKey,
                            w.getWorkoutDate(),
                            w.getId(),
                            null,
                            bodyPart);
                })
                .toList();
        log.info("Recent plan templates: userId={}, count={}", userId, out.size());
        return out;
    }
}
