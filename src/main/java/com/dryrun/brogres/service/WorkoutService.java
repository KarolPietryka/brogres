package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Set;
import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutSubmitRequestDto;
import com.dryrun.brogres.repo.SetRepository;
import com.dryrun.brogres.repo.WorkoutRepository;
import lombok.RequiredArgsConstructor;
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
    private final SetRepository setRepository;

    @Transactional
    public Workout createWorkout(WorkoutSubmitRequestDto request) {
        LocalDate today = LocalDate.now();

        if (workoutRepository.existsByWorkoutDate(today)) {
            throw new IllegalStateException("Workout for current day already exists");
        }

        Workout workout = workoutFactory.createWorkout();
        workout.setWorkoutDate(today);
        workout = workoutRepository.save(workout);

        List<Set> setsToSave = new ArrayList<>();
        for (WorkoutSubmitRequestDto.WorkoutBodyPartDto bodyPartDto : request.bodyPart()) {
            for (WorkoutSubmitRequestDto.WorkoutExerciseDto exerciseDto : bodyPartDto.exercises()) {
                Set set = new Set();
                set.setWorkout(workout);
                set.setExercise(exerciseDto.name());
                set.setRepetitions(exerciseDto.reps());
                setsToSave.add(set);
            }
        }

        setRepository.saveAll(setsToSave);
        return workout;
    }

    // ... existing code ...
}
