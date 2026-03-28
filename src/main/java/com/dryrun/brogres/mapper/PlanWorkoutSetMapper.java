package com.dryrun.brogres.mapper;

import com.dryrun.brogres.data.PlanWorkoutSet;
import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutSet;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PlanWorkoutSetMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "workout", ignore = true)
    @Mapping(target = "bodyPart", expression = "java(set.getBodyPart() != null ? set.getBodyPart() : \"\")")
    PlanWorkoutSet fromWorkoutSet(WorkoutSet set, @Context Workout workout);

    @AfterMapping
    default void linkWorkout(@MappingTarget PlanWorkoutSet row, @Context Workout workout) {
        row.setWorkout(workout);
    }

    default List<PlanWorkoutSet> fromWorkoutSets(List<WorkoutSet> sets, Workout workout) {
        return sets.stream().map(s -> fromWorkoutSet(s, workout)).toList();
    }
}
