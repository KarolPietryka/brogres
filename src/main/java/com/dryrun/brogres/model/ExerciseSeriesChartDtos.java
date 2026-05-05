package com.dryrun.brogres.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request/response for {@code POST /workout/graph/exercise-series} — per-day sums of weight and reps for one exercise.
 */
public final class ExerciseSeriesChartDtos {

    private ExerciseSeriesChartDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExerciseSeriesRequest(
            @NotNull Long exerciseId,
            LocalDate fromDate,
            LocalDate toDate,
            Integer repMin,
            Integer repMax,
            BigDecimal weightMin,
            BigDecimal weightMax
    ) {
    }

    public record ExerciseSeriesPointDto(
            LocalDate workoutDay,
            BigDecimal totalWeight,
            long totalReps
    ) {
    }
}
