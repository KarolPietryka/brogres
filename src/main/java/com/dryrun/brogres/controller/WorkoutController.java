package com.dryrun.brogres.controller;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.model.WorkoutResponseDtos.WorkoutPrefillDto;
import com.dryrun.brogres.model.WorkoutResponseDtos.WorkoutSummaryDto;
import com.dryrun.brogres.model.WorkoutSubmitRequestDto;
import com.dryrun.brogres.security.SecurityUtils;
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
        return workoutService.listWorkouts(SecurityUtils.requireUserId());
    }

    @PostMapping
    public Workout createWorkout(@Valid @RequestBody WorkoutSubmitRequestDto request) {
        return workoutService.createWorkout(SecurityUtils.requireUserId(), request);
    }

    /**
     * Prefill for the next session: flat {@code bodyPart} list (same per-row shape as POST {@code exercises}); empty when nothing to clone.
     */
    @GetMapping("/prefill")
    public WorkoutPrefillDto prefillWorkout() {
        return workoutService.prefillWorkout(SecurityUtils.requireUserId());
    }

    // Nowy endpoint: dodaje pojedyncze ćwiczenie do istniejącego Workout
//    @PostMapping("/{workoutId}/exercises")
//    public Workout addExerciseToWorkout(@PathVariable Long workoutId, @RequestBody String exerciseName) {
//        // ...
//       // return workoutService.addExerciseToWorkout(workoutId, exercise);
//    }
}
