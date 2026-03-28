package com.dryrun.brogres.mapper;

import com.dryrun.brogres.data.PlanWorkoutSet;
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
    @Mapping(target = "exercisePlan", expression = "java(toBodyPartsFromPlanWorkoutSets(workout.getPlanWorkoutSets()))")
    WorkoutSummaryDto toSummary(Workout workout);

    default List<WorkoutBodyPartViewDto> toBodyParts(Workout workout) {
        List<WorkoutSet> sets = new ArrayList<>(workout.getSets());
        sets.sort(Comparator.comparingInt(WorkoutSet::getLineOrder).thenComparing(WorkoutSet::getId));
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
                    set.getExercise(), set.getLineOrder(), set.getWeight(), set.getRepetitions()));
        }
        bodyParts.add(new WorkoutBodyPartViewDto(currentPart, currentExercises));
        return bodyParts;
    }

    default List<WorkoutBodyPartViewDto> toBodyPartsFromPlanWorkoutSets(List<PlanWorkoutSet> planWorkoutSets) {
        if (planWorkoutSets == null || planWorkoutSets.isEmpty()) {
            return List.of();
        }
        List<PlanWorkoutSet> sorted = new ArrayList<>(planWorkoutSets);
        sorted.sort(Comparator.comparingInt(PlanWorkoutSet::getLineOrder).thenComparing(PlanWorkoutSet::getId));
        List<WorkoutBodyPartViewDto> bodyParts = new ArrayList<>();
        String currentPart = null;
        List<WorkoutExerciseViewDto> currentExercises = null;
        for (PlanWorkoutSet row : sorted) {
            String part = row.getBodyPart() != null ? row.getBodyPart() : "";
            if (currentPart == null || !currentPart.equals(part)) {
                if (currentPart != null) {
                    bodyParts.add(new WorkoutBodyPartViewDto(currentPart, currentExercises));
                }
                currentPart = part;
                currentExercises = new ArrayList<>();
            }
            currentExercises.add(new WorkoutExerciseViewDto(
                    row.getExercise(), row.getLineOrder(), row.getWeight(), row.getRepetitions()));
        }
        bodyParts.add(new WorkoutBodyPartViewDto(currentPart, currentExercises));
        return bodyParts;
    }
}
