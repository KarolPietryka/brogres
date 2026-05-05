package com.dryrun.brogres.controller;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.model.WorkoutResponseDtos.RecentPlanTemplateDto;
import com.dryrun.brogres.model.WorkoutResponseDtos.WorkoutPrefillDto;
import com.dryrun.brogres.model.WorkoutResponseDtos.WorkoutSummaryDto;
import com.dryrun.brogres.model.ExerciseDtos.CreateExerciseRequest;
import com.dryrun.brogres.model.ExerciseDtos.ExercisePickerDto;
import com.dryrun.brogres.model.ExerciseDtos.ExerciseRefDto;
import com.dryrun.brogres.model.ExerciseSeriesChartDtos.ExerciseSeriesPointDto;
import com.dryrun.brogres.model.ExerciseSeriesChartDtos.ExerciseSeriesRequest;
import com.dryrun.brogres.model.WorkoutSubmitRequestDto;
import com.dryrun.brogres.security.SecurityUtils;
import com.dryrun.brogres.service.ExercisePickerService;
import com.dryrun.brogres.service.ExerciseSeriesChartService;
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
    private final ExerciseSeriesChartService exerciseSeriesChartService;

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

    /** Full replace of sets for the given workout (same body as POST /workout). */
    @PutMapping("/{workoutId}")
    public Workout replaceWorkout(
            @PathVariable Long workoutId,
            @Valid @RequestBody WorkoutSubmitRequestDto request) {
        return workoutService.replaceWorkout(SecurityUtils.requireUserId(), workoutId, request);
    }

    /**
     * Deletes the workout row and all its sets. Only the authenticated user’s **today** workout
     * ({@code workoutDate} = server “today”) may be removed (other ids → 400).
     */
    @DeleteMapping("/{workoutId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTodaysWorkout(@PathVariable("workoutId") Long workoutId) {
        workoutService.deleteTodaysWorkout(SecurityUtils.requireUserId(), workoutId);
    }

    /**
     * Prefill for the next session: flat {@code bodyPart} list (same per-row shape as POST {@code exercises}); empty when nothing to clone.
     */
    @GetMapping("/prefill")
    public WorkoutPrefillDto prefillWorkout() {
        return workoutService.prefillWorkout(SecurityUtils.requireUserId());
    }

    /**
     * GET /workout/recent-plan-templates — carousel data for Today: distinct past signatures, each with a full
     * {@code bodyPart} snapshot (same row shape as {@code GET /workout/prefill}). Caller is the authenticated user;
     * no query parameters on MVP.
     */
    @GetMapping("/recent-plan-templates")
    public List<RecentPlanTemplateDto> recentPlanTemplates() {
        return workoutService.listRecentPlanTemplates(SecurityUtils.requireUserId());
    }

    /**
     * Charts: per workout-day sums of weight and repetitions for one exercise (only DONE sets; filters in body).
     */
    @PostMapping("/graph/exercise-series")
    public List<ExerciseSeriesPointDto> exerciseSeriesChart(@Valid @RequestBody ExerciseSeriesRequest request) {
        return exerciseSeriesChartService.seriesPoints(SecurityUtils.requireUserId(), request);
    }

}
