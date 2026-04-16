package com.dryrun.brogres.mapper;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.model.WorkoutResponseDtos.WorkoutExerciseViewDto;
import com.dryrun.brogres.model.WorkoutResponseDtos.WorkoutSummaryDto;
import com.dryrun.brogres.data.WorkoutSet;
import com.dryrun.brogres.data.WorkoutSetStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring")
public interface WorkoutSummaryMapper {

    @Mapping(target = "bodyPart", expression = "java(toBodyParts(workout))")
    @Mapping(target = "exercisePlan", expression = "java(toExercisePlan(workout))")
    WorkoutSummaryDto toSummary(Workout workout);

    /** Executed sets ({@link WorkoutSetStatus#DONE}) — list / history. */
    default List<WorkoutExerciseViewDto> toBodyParts(Workout workout) {
        return mapSetsToExerciseViews(orderedSets(workout, false), null);
    }

    /** Today’s plan slice ({@link WorkoutSetStatus#PLANNED}). */
    default List<WorkoutExerciseViewDto> toExercisePlan(Workout workout) {
        return mapSetsToExerciseViews(orderedSets(workout, true), null);
    }

    /**
     * Last session’s executed sets as a synthetic plan (DTO rows forced to {@link WorkoutSetStatus#PLANNED}).
     * Used for prefill when there is no workout row for today yet (progress bar starts at the top).
     */
    default List<WorkoutExerciseViewDto> toPrefillFromPreviousSession(Workout workout) {
        return mapSetsToExerciseViews(orderedSets(workout, false), WorkoutSetStatus.PLANNED);
    }

    /**
     * All sets for today’s workout (DONE + PLANNED), for GET /workout/prefill when today’s session exists.
     * FE derives progress-bar position from the count of leading DONE rows.
     */
    default List<WorkoutExerciseViewDto> toPrefillTodayFullWorkout(Workout workout) {
        List<WorkoutSet> all = workout.getSets().stream()
                .sorted(Comparator.comparingInt(WorkoutSet::getLineOrder).thenComparing(WorkoutSet::getId))
                .toList();
        return mapSetsToExerciseViews(all, null);
    }

    private List<WorkoutSet> orderedSets(Workout workout, boolean planSlice) {
        return workout.getSets().stream()
                .filter(s -> planSlice == (s.getStatus() == WorkoutSetStatus.PLANNED))
                .sorted(Comparator.comparingInt(WorkoutSet::getLineOrder).thenComparing(WorkoutSet::getId))
                .toList();
    }

    /**
     * @param statusOverride if non-null, every DTO row gets this status (e.g. synthetic prefill from last session);
     *                       if null, status comes from the entity.
     */
    private List<WorkoutExerciseViewDto> mapSetsToExerciseViews(List<WorkoutSet> sets, WorkoutSetStatus statusOverride) {
        return sets.stream()
                .map(set -> {
                    WorkoutSetStatus rowStatus = statusOverride != null ? statusOverride : set.getStatus();
                    String part = set.getBodyPart() != null ? set.getBodyPart() : "";
                    return new WorkoutExerciseViewDto(
                            part,
                            set.getExercise().getName(),
                            set.getExercise().getId(),
                            set.getLineOrder(),
                            set.getWeight(),
                            set.getRepetitions(),
                            rowStatus);
                })
                .toList();
    }
}
