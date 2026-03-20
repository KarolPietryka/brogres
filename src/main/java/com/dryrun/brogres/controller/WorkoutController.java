package com.dryrun.brogres.controller;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutSubmitRequestDto;
import com.dryrun.brogres.model.ExcerciseEnum;
import com.dryrun.brogres.service.WorkoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workout")
@RequiredArgsConstructor
public class WorkoutController {

    private final WorkoutService workoutService;

    @PostMapping
    public Workout createWorkout(@Valid @RequestBody WorkoutSubmitRequestDto request) {
        return workoutService.createWorkout(request);
    }

    // Nowy endpoint: dodaje pojedyncze ćwiczenie do istniejącego Workout
//    @PostMapping("/{workoutId}/exercises")
//    public Workout addExerciseToWorkout(@PathVariable Long workoutId, @RequestBody ExcerciseEnum exercise) {
//        // Deserializacja enumu z JSON (np. {"exercise":"OVERHEAD_BB"} - przyjmuje prosty string w treści body będzie mapowany do enumu)
//       // return workoutService.addExerciseToWorkout(workoutId, exercise);
//    }
}
