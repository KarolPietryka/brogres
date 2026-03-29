package com.dryrun.brogres.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class WorkoutResponseDtos {

    private WorkoutResponseDtos() {
    }

    public record WorkoutSummaryDto(
            long id,
            LocalDate workoutDate,
            List<WorkoutBodyPartViewDto> bodyPart,
            List<WorkoutBodyPartViewDto> exercisePlan) {
    }

    public record WorkoutBodyPartViewDto(String bodyPartName, List<WorkoutExerciseViewDto> exercises) {
    }

    /** {@link WorkoutSetStatus} for FE styling (PLANNED / NEXT / DONE). */
    public record WorkoutExerciseViewDto(String name, int orderId, BigDecimal weight, int reps, WorkoutSetStatus status) {
    }

    /**
     * GET /workout/prefill — same shape as {@code bodyPart} in {@link WorkoutSubmitRequestDto}.
     * When today’s workout exists: service maps persisted {@code DONE} rows to {@code PLANNED} in the DTO, then picks one {@code NEXT}.
     * When it does not: synthetic plan from the last session (see service).
     */
    public record WorkoutPrefillDto(List<WorkoutBodyPartViewDto> bodyPart) {
        public static WorkoutPrefillDto empty() {
            return new WorkoutPrefillDto(List.of());
        }
    }
}
