package com.dryrun.brogres.service;

import com.dryrun.brogres.data.WorkoutSet;
import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutSubmitRequestDto;
import com.dryrun.brogres.repo.WorkoutSetRepository;
import com.dryrun.brogres.repo.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkoutService {

    private final WorkoutFactory workoutFactory;
    private final WorkoutRepository workoutRepository;
    private final WorkoutSetRepository workoutSetRepository;

    @Transactional
    public Workout createWorkout(WorkoutSubmitRequestDto request) {
        LocalDate today = LocalDate.now();

        if (workoutRepository.existsByWorkoutDate(today)) {
            throw new IllegalStateException("Workout for current day already exists");
        }

        Workout workout = workoutFactory.createWorkout();
        workout.setWorkoutDate(today);
        workout = workoutRepository.save(workout);

        List<WorkoutSet> setsToSave = new ArrayList<>();
        for (WorkoutSubmitRequestDto.WorkoutBodyPartDto bodyPartDto : request.bodyPart()) {
            for (WorkoutSubmitRequestDto.WorkoutExerciseDto exerciseDto : bodyPartDto.exercises()) {
                WorkoutSet workoutSet = new WorkoutSet();
                workoutSet.setWorkout(workout);
                workoutSet.setExercise(exerciseDto.name());
                workoutSet.setRepetitions(exerciseDto.reps());
                setsToSave.add(workoutSet);
            }
        }

        workoutSetRepository.saveAll(setsToSave);
        return workout;
    }

    // ... existing code ...
}
