package com.dryrun.brogres.mapper;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutBodyPartViewDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutExerciseViewDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutSummaryDto;
import com.dryrun.brogres.data.WorkoutSet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring")
public interface WorkoutSummaryMapper {

    @Mapping(target = "bodyPart", expression = "java(toBodyParts(workout))")
    @Mapping(target = "exercisePlan", expression = "java(toExercisePlan(workout))")
    WorkoutSummaryDto toSummary(Workout workout);

    /** Wykonane serie ({@code planned == false}) — lista / historia. */
    default List<WorkoutBodyPartViewDto> toBodyParts(Workout workout) {
        return bodyPartsFromSets(orderedSets(workout, false), false);
    }

    /** Plan na dziś ({@code planned == true}) — prefill / drawer. */
    default List<WorkoutBodyPartViewDto> toExercisePlan(Workout workout) {
        return bodyPartsFromSets(orderedSets(workout, true), true);
    }

    /**
     * Wykonane serie z poprzedniej sesji jako plan na dziś (wszystkie z {@code planned=true}).
     * Używane przez prefill gdy nie ma jeszcze workoutu na dziś.
     */
    default List<WorkoutBodyPartViewDto> toPrefillFromPreviousSession(Workout workout) {
        return bodyPartsFromSets(orderedSets(workout, false), true);
    }

    private List<WorkoutSet> orderedSets(Workout workout, boolean planned) {
        return workout.getSets().stream()
                .filter(s -> s.isPlanned() == planned)
                .sorted(Comparator.comparingInt(WorkoutSet::getLineOrder).thenComparing(WorkoutSet::getId))
                .toList();
    }

    private List<WorkoutBodyPartViewDto> bodyPartsFromSets(List<WorkoutSet> sets, boolean plannedFlag) {
        if (sets.isEmpty()) {
            return List.of();
        }
        List<WorkoutBodyPartViewDto> bodyParts = new ArrayList<>();
        String currentPart = null;
        List<WorkoutExerciseViewDto> currentExercises = null;
        for (WorkoutSet set : sets) {
            String part = set.getBodyPart() != null ? set.getBodyPart() : "";
            if (currentPart == null || !currentPart.equals(part)) {
                if (currentPart != null) {
                    bodyParts.add(new WorkoutBodyPartViewDto(currentPart, currentExercises));
                }
                currentPart = part;
                currentExercises = new ArrayList<>();
            }
            currentExercises.add(new WorkoutExerciseViewDto(
                    set.getExercise(), set.getLineOrder(), set.getWeight(), set.getRepetitions(), plannedFlag));
        }
        bodyParts.add(new WorkoutBodyPartViewDto(currentPart, currentExercises));
        return bodyParts;
    }
}
