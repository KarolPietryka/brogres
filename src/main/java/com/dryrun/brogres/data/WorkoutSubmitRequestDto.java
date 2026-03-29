package com.dryrun.brogres.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutSubmitRequestDto(
        @NotEmpty @Valid List<WorkoutExerciseDto> exercises
) {

    /**
     * One row per {@link WorkoutSet}: {@code bodyPartName} on each line, same as persistence.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkoutExerciseDto(
            @NotBlank String bodyPartName,
            @NotBlank String name,
            BigDecimal weight,
            @NotNull @Min(1) Integer reps,
            WorkoutSetStatus status
    ) {
    }
}
