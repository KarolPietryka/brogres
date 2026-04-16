package com.dryrun.brogres.model;

import com.dryrun.brogres.data.WorkoutSetStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class WorkoutResponseDtos {

    private WorkoutResponseDtos() {
    }

    /**
     * {@code bodyPart} = executed ({@link WorkoutSetStatus#DONE}); {@code exercisePlan} = planned slice.
     * Each element carries {@code bodyPartName} like {@link com.dryrun.brogres.data.WorkoutSet}.
     */
    public record WorkoutSummaryDto(
            long id,
            LocalDate workoutDate,
            List<WorkoutExerciseViewDto> bodyPart,
            List<WorkoutExerciseViewDto> exercisePlan) {
    }

    /** {@link WorkoutSetStatus} for FE styling (PLANNED / DONE). */
    public record WorkoutExerciseViewDto(
            String bodyPartName,
            String name,
            Long exerciseId,
            int orderId,
            BigDecimal weight,
            int reps,
            WorkoutSetStatus status) {
    }

    /**
     * GET /workout/prefill — flat {@code bodyPart} list (same per-row shape as {@link WorkoutSubmitRequestDto.WorkoutExerciseDto}).
     * When today’s workout exists: rows are returned with their persisted statuses ({@code DONE} / {@code PLANNED}); the FE
     * derives the progress-bar position from the count of leading {@code DONE} rows.
     * When it does not: synthetic plan from the last session (all rows as {@code PLANNED}) — see service.
     */
    public record WorkoutPrefillDto(List<WorkoutExerciseViewDto> bodyPart) {
        public static WorkoutPrefillDto empty() {
            return new WorkoutPrefillDto(List.of());
        }
    }

    /** GET /brogres/graph — one point per workout day in the current focus series (see {@code WorkoutGraphService}). */
    public record GraphVolumePointDto(LocalDate workoutDay, BigDecimal volume) {
    }
}
