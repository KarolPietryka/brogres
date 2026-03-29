package com.dryrun.brogres.mapper;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutBodyPartViewDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutExerciseViewDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutSummaryDto;
import com.dryrun.brogres.data.WorkoutSet;
import com.dryrun.brogres.data.WorkoutSetStatus;
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

    /** Executed sets ({@link WorkoutSetStatus#DONE}) — list / history. */
    default List<WorkoutBodyPartViewDto> toBodyParts(Workout workout) {
        return bodyPartsFromSets(orderedSets(workout, false), null);
    }

    /** Today’s plan slice ({@link WorkoutSetStatus#PLANNED} / {@link WorkoutSetStatus#NEXT}). */
    default List<WorkoutBodyPartViewDto> toExercisePlan(Workout workout) {
        return bodyPartsFromSets(orderedSets(workout, true), null);
    }

    /**
     * Last session’s executed sets as a synthetic plan (DTO rows start as {@link WorkoutSetStatus#PLANNED}).
     * Used for prefill when there is no workout row for today yet.
     */
    default List<WorkoutBodyPartViewDto> toPrefillFromPreviousSession(Workout workout) {
        return bodyPartsFromSets(orderedSets(workout, false), WorkoutSetStatus.PLANNED);
    }

    /**
     * All sets for today’s workout (DONE + PLANNED + NEXT), for GET /workout/prefill when today’s session exists.
     */
    default List<WorkoutBodyPartViewDto> toPrefillTodayFullWorkout(Workout workout) {
        List<WorkoutSet> all = workout.getSets().stream()
                .sorted(Comparator.comparingInt(WorkoutSet::getLineOrder).thenComparing(WorkoutSet::getId))
                .toList();
        return bodyPartsFromSets(all, null);
    }

    private List<WorkoutSet> orderedSets(Workout workout, boolean planSlice) {
        return workout.getSets().stream()
                .filter(s -> planSlice == isPlanStatus(s.getStatus()))
                .sorted(Comparator.comparingInt(WorkoutSet::getLineOrder).thenComparing(WorkoutSet::getId))
                .toList();
    }

    private static boolean isPlanStatus(WorkoutSetStatus status) {
        return status == WorkoutSetStatus.PLANNED || status == WorkoutSetStatus.NEXT;
    }

    /**
     * @param statusOverride if non-null, every DTO row gets this status (e.g. synthetic prefill from last session);
     *                       if null, status comes from the entity.
     */
    private List<WorkoutBodyPartViewDto> bodyPartsFromSets(List<WorkoutSet> sets, WorkoutSetStatus statusOverride) {
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
            WorkoutSetStatus rowStatus = statusOverride != null ? statusOverride : set.getStatus();
            currentExercises.add(new WorkoutExerciseViewDto(
                    set.getExercise(), set.getLineOrder(), set.getWeight(), set.getRepetitions(), rowStatus));
        }
        bodyParts.add(new WorkoutBodyPartViewDto(currentPart, currentExercises));
        return bodyParts;
    }
}
