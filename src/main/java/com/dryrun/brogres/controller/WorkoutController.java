package com.dryrun.brogres.controller;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutResponseDtos.PrefillWorkoutResponseDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutSummaryDto;
import com.dryrun.brogres.data.WorkoutSubmitRequestDto;
import com.dryrun.brogres.service.ExerciseCatalogService;
import com.dryrun.brogres.service.WorkoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/workout")
@RequiredArgsConstructor
public class WorkoutController {

    private final WorkoutService workoutService;
    private final ExerciseCatalogService exerciseCatalogService;

    @GetMapping("/exercise-catalog")
    public Map<String, List<String>> exerciseCatalog() {
        return exerciseCatalogService.exercisesByDisplayGroup();
    }

    @GetMapping
    public List<WorkoutSummaryDto> listWorkouts() {
        return workoutService.listWorkouts();
    }

    @PostMapping
    public Workout createWorkout(@Valid @RequestBody WorkoutSubmitRequestDto request) {
        return workoutService.createWorkout(request);
    }

    /**
     * Baseline for the next session: last saved workout day, same JSON shape as one item from {@code GET /workout}.
     */
    @GetMapping("/prefill")
    public PrefillWorkoutResponseDto prefillWorkout() {
        return workoutService.prefillFromLastWorkout();
    }

    // Nowy endpoint: dodaje pojedyncze ćwiczenie do istniejącego Workout
//    @PostMapping("/{workoutId}/exercises")
//    public Workout addExerciseToWorkout(@PathVariable Long workoutId, @RequestBody String exerciseName) {
//        // ...
//       // return workoutService.addExerciseToWorkout(workoutId, exercise);
//    }
}
