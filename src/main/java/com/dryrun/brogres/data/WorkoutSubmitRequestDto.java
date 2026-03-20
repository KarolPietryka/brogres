package com.dryrun.brogres.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutSubmitRequestDto(List<WorkoutBodyPartDto> bodyPart) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkoutBodyPartDto(String bodyPartName, List<WorkoutExerciseDto> exercises) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkoutExerciseDto(String name, BigDecimal weight, Integer reps) {
    }
}
