package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutPrefillDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutSummaryDto;
import com.dryrun.brogres.data.WorkoutSubmitRequestDto;
import com.dryrun.brogres.data.WorkoutSet;
import com.dryrun.brogres.mapper.WorkoutSummaryMapper;
import com.dryrun.brogres.repo.WorkoutRepository;
import com.dryrun.brogres.repo.WorkoutSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkoutService {

    private final WorkoutFactory workoutFactory;
    private final WorkoutRepository workoutRepository;
    private final WorkoutSetRepository workoutSetRepository;
    private final WorkoutSummaryMapper workoutSummaryMapper;

    @Transactional
    public Workout createWorkout(WorkoutSubmitRequestDto request) {
        LocalDate today = LocalDate.now();

        Optional<Workout> existing = workoutRepository.findByWorkoutDate(today);
        Workout workout;
        int nextLineOrder;
        if (existing.isPresent()) {
            workout = existing.get();
            nextLineOrder = workoutSetRepository.findMaxLineOrderIndex(workout.getId()) + 1;
        } else {
            workout = workoutFactory.createWorkout();
            workout.setWorkoutDate(today);
            workout = workoutRepository.save(workout);
            copyPreviousSessionAsPlannedSets(workout);
            nextLineOrder = workoutSetRepository.findMaxLineOrderIndex(workout.getId()) + 1;
        }

        List<WorkoutSet> setsToSave = new ArrayList<>();
        for (WorkoutSubmitRequestDto.WorkoutBodyPartDto bodyPartDto : request.bodyPart()) {
            for (WorkoutSubmitRequestDto.WorkoutExerciseDto exerciseDto : bodyPartDto.exercises()) {
                WorkoutSet workoutSet = new WorkoutSet();
                workoutSet.setWorkout(workout);
                workoutSet.setBodyPart(bodyPartDto.bodyPartName());
                workoutSet.setExercise(exerciseDto.name());
                workoutSet.setWeight(exerciseDto.weight());
                workoutSet.setRepetitions(exerciseDto.reps());
                workoutSet.setLineOrder(nextLineOrder++);
                workoutSet.setPlanned(false);
                setsToSave.add(workoutSet);
            }
        }

        workoutSetRepository.saveAll(setsToSave);
        return workout;
    }

    /**
     * Pierwszy zapis dnia: snapshot ostatniej sesji (tylko wykonane serie) jako {@link WorkoutSet} z {@code planned=true}.
     */
    private void copyPreviousSessionAsPlannedSets(Workout workout) {
        Optional<Workout> previous = workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(workout.getWorkoutDate());
        if (previous.isEmpty()) {
            return;
        }
        List<WorkoutSet> executed = previous.get().getSets().stream()
                .filter(s -> !s.isPlanned())
                .sorted(Comparator.comparingInt(WorkoutSet::getLineOrder).thenComparing(WorkoutSet::getId))
                .toList();
        if (executed.isEmpty()) {
            return;
        }
        List<WorkoutSet> plannedRows = new ArrayList<>();
        for (WorkoutSet src : executed) {
            WorkoutSet row = new WorkoutSet();
            row.setWorkout(workout);
            row.setBodyPart(src.getBodyPart());
            row.setExercise(src.getExercise());
            row.setWeight(src.getWeight());
            row.setRepetitions(src.getRepetitions());
            row.setLineOrder(src.getLineOrder());
            row.setPlanned(true);
            plannedRows.add(row);
        }
        workoutSetRepository.saveAll(plannedRows);
    }

    @Transactional(readOnly = true)
    public WorkoutPrefillDto prefillWorkout() {
        LocalDate today = LocalDate.now();
        if (workoutRepository.existsByWorkoutDate(today)) {
            return workoutRepository.findByWorkoutDate(today)
                    .map(w -> new WorkoutPrefillDto(workoutSummaryMapper.toExercisePlan(w)))
                    .orElseGet(WorkoutPrefillDto::empty);
        }
        return workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today)
                .map(w -> new WorkoutPrefillDto(workoutSummaryMapper.toPrefillFromPreviousSession(w)))
                .orElseGet(WorkoutPrefillDto::empty);
    }

    @Transactional(readOnly = true)
    public List<WorkoutSummaryDto> listWorkouts() {
        return workoutRepository.findAllByOrderByWorkoutDateDesc().stream()
                .map(workoutSummaryMapper::toSummary)
                .toList();
    }
}
