package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutBodyPartViewDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutExerciseViewDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutPrefillDto;
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

    private final WorkoutFactory workoutFactory;
    private final WorkoutRepository workoutRepository;
    private final WorkoutSetRepository workoutSetRepository;

    @Transactional
    public Workout createWorkout(WorkoutSubmitRequestDto request) {
        LocalDate today = LocalDate.now();

        Optional<Workout> existing = workoutRepository.findByWorkoutDate(today);
        Workout workout;
        int nextLineOrder;
        if (existing.isPresent()) {
            workout = existing.get();
            nextLineOrder = workoutSetRepository.findMaxLineOrderIndex(workout.getId()) + 1;
        } else {
            workout = workoutFactory.createWorkout();
            workout.setWorkoutDate(today);
            workout = workoutRepository.save(workout);
            nextLineOrder = 0;
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
                workoutSet.setLineOrder(nextLineOrder++);
                setsToSave.add(workoutSet);
            }
        }

        workoutSetRepository.saveAll(setsToSave);
        return workout;
    }

    @Transactional(readOnly = true)
    public WorkoutPrefillDto prefillWorkout() {
        LocalDate today = LocalDate.now();
        if (workoutRepository.existsByWorkoutDate(today)) {
            return WorkoutPrefillDto.empty();
        }
        return workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today)
                .map(w -> new WorkoutPrefillDto(toBodyParts(w)))
                .orElseGet(WorkoutPrefillDto::empty);
    }

    @Transactional(readOnly = true)
    public List<WorkoutSummaryDto> listWorkouts() {
        return workoutRepository.findAllByOrderByWorkoutDateDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    private WorkoutSummaryDto toSummary(Workout workout) {
        return new WorkoutSummaryDto(workout.getId(), workout.getWorkoutDate(), toBodyParts(workout));
    }

    private List<WorkoutBodyPartViewDto> toBodyParts(Workout workout) {
        List<WorkoutSet> sets = new ArrayList<>(workout.getSets());
        sets.sort(Comparator.comparingInt(WorkoutSet::getLineOrder).thenComparing(WorkoutSet::getId));
        if (sets.isEmpty()) {
            return List.of();
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
            currentExercises.add(new WorkoutExerciseViewDto(
                    set.getExercise(), set.getLineOrder(), set.getWeight(), set.getRepetitions()));
        }
        bodyParts.add(new WorkoutBodyPartViewDto(currentPart, currentExercises));
        return bodyParts;
    }
}
