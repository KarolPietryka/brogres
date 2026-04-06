package com.dryrun.brogres.controller;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.model.WorkoutResponseDtos.WorkoutPrefillDto;
import com.dryrun.brogres.model.WorkoutResponseDtos.WorkoutSummaryDto;
import com.dryrun.brogres.model.ExerciseDtos.CreateExerciseRequest;
import com.dryrun.brogres.model.ExerciseDtos.ExercisePickerDto;
import com.dryrun.brogres.model.ExerciseDtos.ExerciseRefDto;
import com.dryrun.brogres.model.WorkoutSubmitRequestDto;
import com.dryrun.brogres.security.SecurityUtils;
import com.dryrun.brogres.service.ExercisePickerService;
import com.dryrun.brogres.service.WorkoutService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/workout")
@RequiredArgsConstructor
public class WorkoutController {

    private final WorkoutService workoutService;
    private final ExercisePickerService exercisePickerService;

    /**
     * Exercises for the add-workout picker: global catalog plus this user’s custom names for the body part.
     */
    @GetMapping("/exercises/picker")
    public ExercisePickerDto exercisePicker(@RequestParam("bodyPart") @NotBlank String bodyPart) {
        return exercisePickerService.pickerForBodyPart(SecurityUtils.requireUserId(), bodyPart.trim());
    }

    /** Creates a user-owned exercise name for a body part (duplicate name → 409). */
    @PostMapping("/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseRefDto createUserExercise(@Valid @RequestBody CreateExerciseRequest request) {
        return exercisePickerService.createUserExercise(SecurityUtils.requireUserId(), request);
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
