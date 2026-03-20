package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Set;
import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutSubmitRequestDto;
import com.dryrun.brogres.model.ExcerciseEnum;
import com.dryrun.brogres.repo.SetRepository;
import com.dryrun.brogres.repo.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkoutService {

    private final WorkoutFactory workoutFactory;
    private final WorkoutRepository workoutRepository;
    private final SetRepository setRepository;

    public Workout createWorkout(WorkoutSubmitRequestDto request) {
        throw new UnsupportedOperationException("Not implemented yet: createWorkout(WorkoutSubmitRequestDto)");
    }

    public Workout addExerciseToWorkout(Long workoutId, ExcerciseEnum exercise) {
        Workout workout = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new IllegalArgumentException("Workout not found"));

        Set set = new Set();
        set.setWorkout(workout);
        set.setExercise(exercise);
        set.setRepetitions(1);

        setRepository.save(set);
        return workout;
    }
}
