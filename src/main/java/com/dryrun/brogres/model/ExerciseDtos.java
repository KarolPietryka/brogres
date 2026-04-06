package com.dryrun.brogres.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ExerciseDtos {

    private ExerciseDtos() {
    }

    public record ExerciseRefDto(long id, String name) {
    }

    /** {@code GET /workout/exercises/picker} — catalog (global) then user-defined for the body part. */
    public record ExercisePickerDto(List<ExerciseRefDto> catalog, List<ExerciseRefDto> custom) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateExerciseRequest(
            @NotBlank @Size(max = 64) String bodyPart,
            @NotBlank @Size(max = 255) String name) {
    }
}
