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
}
