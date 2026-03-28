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

    /** {@code planned}: wiersz planu na dziś vs wykonana seria (styling na FE). */
    public record WorkoutExerciseViewDto(String name, int orderId, BigDecimal weight, int reps, boolean planned) {
    }

    /** GET /workout/prefill — same shape as {@code bodyPart} in {@link WorkoutSubmitRequestDto}. */
    public record WorkoutPrefillDto(List<WorkoutBodyPartViewDto> bodyPart) {
        public static WorkoutPrefillDto empty() {
            return new WorkoutPrefillDto(List.of());
        }
    }
}
