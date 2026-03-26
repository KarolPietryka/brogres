package com.dryrun.brogres.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class WorkoutResponseDtos {

    private WorkoutResponseDtos() {
    }

    public record WorkoutSummaryDto(long id, LocalDate workoutDate, List<WorkoutBodyPartViewDto> bodyPart) {
    }

    public record WorkoutBodyPartViewDto(String bodyPartName, List<WorkoutExerciseViewDto> exercises) {
    }

    public record WorkoutExerciseViewDto(String name, BigDecimal weight, int reps) {
    }

    /**
     * {@code GET /workout/prefill} — same shape as one element of {@code GET /workout} when present,
     * so the frontend can reuse mapping to modal draft lines.
     */
    public record PrefillWorkoutResponseDto(WorkoutSummaryDto lastWorkout) {
    }
}
