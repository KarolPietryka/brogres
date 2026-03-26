package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutResponseDtos.PrefillWorkoutResponseDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutBodyPartViewDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutExerciseViewDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutSummaryDto;
import com.dryrun.brogres.data.WorkoutSet;
import com.dryrun.brogres.data.WorkoutSubmitRequestDto;
import com.dryrun.brogres.repo.WorkoutRepository;
import com.dryrun.brogres.repo.WorkoutSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkoutService {

    /** Latest calendar day first; same-day duplicates tie-break by higher id (newer row). */
    private static final Comparator<Workout> BY_SESSION_RECENCY =
            Comparator.comparing(Workout::getWorkoutDate).reversed()
                    .thenComparing(Comparator.comparing(Workout::getId).reversed());

    private final WorkoutFactory workoutFactory;
    private final WorkoutRepository workoutRepository;
    private final WorkoutSetRepository workoutSetRepository;

    @Transactional
    public Workout createWorkout(WorkoutSubmitRequestDto request) {
        LocalDate today = LocalDate.now();

        Optional<Workout> existing = workoutRepository.findByWorkoutDate(today);
        Workout workout;
        if (existing.isPresent()) {
            workout = existing.get();
        } else {
            workout = workoutFactory.createWorkout();
            workout.setWorkoutDate(today);
            workout = workoutRepository.save(workout);
        }

        List<WorkoutSet> setsToSave = new ArrayList<>();
        for (WorkoutSubmitRequestDto.WorkoutBodyPartDto bodyPartDto : request.bodyPart()) {
            for (WorkoutSubmitRequestDto.WorkoutExerciseDto exerciseDto : bodyPartDto.exercises()) {
                WorkoutSet workoutSet = new WorkoutSet();
                workoutSet.setWorkout(workout);
                workoutSet.setBodyPart(bodyPartDto.bodyPartName());
                workoutSet.setExercise(exerciseDto.name());
                workoutSet.setWeight(exerciseDto.weight());
                workoutSet.setRepetitions(exerciseDto.reps());
                setsToSave.add(workoutSet);
            }
        }

        workoutSetRepository.saveAll(setsToSave);
        return workout;
    }

    @Transactional(readOnly = true)
    public List<WorkoutSummaryDto> listWorkouts() {
        return workoutRepository.findAllByOrderByWorkoutDateDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * Baseline session for prefill: the most recent {@link Workout} that actually has at least one set row,
     * including <strong>every</strong> {@link WorkoutSet} for that workout (same projection as list endpoint).
     * Skips empty workout headers (e.g. placeholder day with no exercises yet). If nothing qualifies, {@code lastWorkout} is null.
     */
    @Transactional(readOnly = true)
    public PrefillWorkoutResponseDto prefillFromLastWorkout() {
        List<Workout> workouts = workoutRepository.findAllByOrderByWorkoutDateDesc();
        return workouts.stream()
                .filter(w -> w.getSets() != null && !w.getSets().isEmpty())
                .max(BY_SESSION_RECENCY)
                .map(w -> new PrefillWorkoutResponseDto(toSummary(w)))
                .orElseGet(() -> new PrefillWorkoutResponseDto(null));
    }

    private WorkoutSummaryDto toSummary(Workout workout) {
        List<WorkoutSet> sets = new ArrayList<>(workout.getSets());
        sets.sort(Comparator.comparing(WorkoutSet::getId));
        if (sets.isEmpty()) {
            return new WorkoutSummaryDto(workout.getId(), workout.getWorkoutDate(), List.of());
        }
        List<WorkoutBodyPartViewDto> bodyParts = new ArrayList<>();
        String currentPart = null;
        List<WorkoutExerciseViewDto> currentExercises = null;
        for (WorkoutSet set : sets) {
            String part = set.getBodyPart() != null ? set.getBodyPart() : "";
            if (currentPart == null || !currentPart.equals(part)) {
                if (currentPart != null) {
                    bodyParts.add(new WorkoutBodyPartViewDto(currentPart, currentExercises));
                }
                currentPart = part;
                currentExercises = new ArrayList<>();
            }
            currentExercises.add(new WorkoutExerciseViewDto(set.getExercise(), set.getWeight(), set.getRepetitions()));
        }
        bodyParts.add(new WorkoutBodyPartViewDto(currentPart, currentExercises));
        return new WorkoutSummaryDto(workout.getId(), workout.getWorkoutDate(), bodyParts);
    }
}
