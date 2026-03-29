package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutExerciseViewDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutPrefillDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutSummaryDto;
import com.dryrun.brogres.data.WorkoutSubmitRequestDto;
import com.dryrun.brogres.data.WorkoutSet;
import com.dryrun.brogres.data.WorkoutSetStatus;
import com.dryrun.brogres.mapper.WorkoutSummaryMapper;
import com.dryrun.brogres.repo.WorkoutRepository;
import com.dryrun.brogres.repo.WorkoutSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkoutService {

    private final WorkoutFactory workoutFactory;
    private final WorkoutRepository workoutRepository;
    private final WorkoutSetRepository workoutSetRepository;
    private final WorkoutSummaryMapper workoutSummaryMapper;

    /**
     * Full replace for today: delete all existing sets for the day’s workout, then insert the POST payload.
     * Status comes from the client except {@link WorkoutSetStatus#NEXT}, which is stored as {@link WorkoutSetStatus#DONE}
     * (the “current” set was just completed).
     */
    @Transactional
    public Workout createWorkout(WorkoutSubmitRequestDto request) {
        LocalDate today = LocalDate.now();

        Optional<Workout> existing = workoutRepository.findByWorkoutDate(today);
        Workout workout;
        if (existing.isPresent()) {
            workout = existing.get();
            workoutSetRepository.deleteAllByWorkoutId(workout.getId());
            workoutSetRepository.flush();
        } else {
            workout = workoutFactory.createWorkout();
            workout.setWorkoutDate(today);
            workout = workoutRepository.save(workout);
        }

        int lineOrder = 0;
        List<WorkoutSet> setsToSave = new ArrayList<>();
        for (WorkoutSubmitRequestDto.WorkoutExerciseDto exerciseDto : request.exercises()) {
            WorkoutSet workoutSet = new WorkoutSet();
            workoutSet.setWorkout(workout);
            workoutSet.setBodyPart(exerciseDto.bodyPartName());
            workoutSet.setExercise(exerciseDto.name());
            workoutSet.setWeight(exerciseDto.weight());
            workoutSet.setRepetitions(exerciseDto.reps());
            workoutSet.setLineOrder(lineOrder++);
            workoutSet.setStatus(persistStatusFromSubmit(exerciseDto.status()));
            setsToSave.add(workoutSet);
        }

        workoutSetRepository.saveAll(setsToSave);
        return workout;
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
    public WorkoutPrefillDto prefillWorkout() {
        LocalDate today = LocalDate.now();

        boolean hasTodayWorkout = workoutRepository.existsByWorkoutDate(today);
        if (hasTodayWorkout) {
            Optional<Workout> todayWorkoutOpt = workoutRepository.findByWorkoutDate(today);
            if (todayWorkoutOpt.isEmpty()) {
                return WorkoutPrefillDto.empty();
            }

            Workout todayWorkout = todayWorkoutOpt.get();
            var fullToday = workoutSummaryMapper.toPrefillTodayFullWorkout(todayWorkout);
            var withNextMarked = markPrefillHeadAsNext(fullToday);

            return new WorkoutPrefillDto(withNextMarked);
        }

        Optional<Workout> previousWorkoutOpt =
                workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today);
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
                                e.bodyPartName(), e.name(), e.orderId(), e.weight(), e.reps(), WorkoutSetStatus.PLANNED)
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
                    return new WorkoutExerciseViewDto(e.bodyPartName(), e.name(), e.orderId(), e.weight(), e.reps(), s);
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
                && Objects.equals(a.weight(), b.weight())
                && a.reps() == b.reps();
    }

    @Transactional(readOnly = true)
    public List<WorkoutSummaryDto> listWorkouts() {
        return workoutRepository.findAllByOrderByWorkoutDateDesc().stream()
                .map(workoutSummaryMapper::toSummary)
                .toList();
    }
}
