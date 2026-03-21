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
        @NotEmpty @Valid List<WorkoutBodyPartDto> bodyPart
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkoutBodyPartDto(
            @NotBlank String bodyPartName,
            @NotEmpty @Valid List<WorkoutExerciseDto> exercises
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkoutExerciseDto(
            @NotBlank String name,
            BigDecimal weight,
            @NotNull @Min(1) Integer reps
    ) {
    }
}
