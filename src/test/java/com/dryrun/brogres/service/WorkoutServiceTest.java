package com.dryrun.brogres.service;

import com.dryrun.brogres.data.WorkoutSet;
import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutSubmitRequestDto;
import com.dryrun.brogres.repo.WorkoutSetRepository;
import com.dryrun.brogres.repo.WorkoutRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    @Mock
    WorkoutFactory workoutFactory;

    @Mock
    WorkoutRepository workoutRepository;

    @Mock
    WorkoutSetRepository workoutSetRepository;

    @InjectMocks
    WorkoutService workoutService;

    @Captor
    ArgumentCaptor<Workout> workoutCaptor;

    @Captor
    ArgumentCaptor<List<WorkoutSet>> setsCaptor;

    @Test
    void createWorkout_whenDtoProvided_savesWorkoutAndSetsWithExpectedValues() {
        /*
         JSON, który odpowiada testowanemu DTO (WorkoutSubmitRequestDto):

         {
           "bodyPart": [
             {
               "bodyPartName": "chest",
               "exercises": [
                 { "name": "Bench Press", "weight": 60.0, "reps": 8 },
                 { "name": "Bench Press", "weight": 65.0, "reps": 6 }
               ]
             },
             {
               "bodyPartName": "back",
               "exercises": [
                 { "name": "Pull-ups", "weight": null, "reps": 10 }
               ]
             }
           ]
         }
        */

        LocalDate today = LocalDate.now();

        WorkoutSubmitRequestDto request = new WorkoutSubmitRequestDto(List.of(
                new WorkoutSubmitRequestDto.WorkoutBodyPartDto("chest", List.of(
                        new WorkoutSubmitRequestDto.WorkoutExerciseDto("Bench Press", new BigDecimal("60.0"), 8),
                        new WorkoutSubmitRequestDto.WorkoutExerciseDto("Bench Press", new BigDecimal("65.0"), 6)
                )),
                new WorkoutSubmitRequestDto.WorkoutBodyPartDto("back", List.of(
                        new WorkoutSubmitRequestDto.WorkoutExerciseDto("Pull-ups", null, 10)
                ))
        ));

        Workout factoryWorkout = new Workout();
        when(workoutFactory.createWorkout()).thenReturn(factoryWorkout);

        when(workoutRepository.findByWorkoutDate(today)).thenReturn(Optional.empty());

        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> {
            Workout w = invocation.getArgument(0);
            w.setId(123L);
            return w;
        });

        Workout result = workoutService.createWorkout(request);

        verify(workoutRepository).findByWorkoutDate(today);

        verify(workoutRepository).save(workoutCaptor.capture());
        Workout savedWorkout = workoutCaptor.getValue();
        assertThat(savedWorkout.getWorkoutDate()).isEqualTo(today);
        assertThat(savedWorkout.getId()).isEqualTo(123L);

        verify(workoutSetRepository).saveAll(setsCaptor.capture());
        List<WorkoutSet> savedWorkoutSets = setsCaptor.getValue();

        assertThat(savedWorkoutSets).hasSize(3);
        assertThat(savedWorkoutSets).allSatisfy(s -> {
            assertThat(s.getWorkout()).isSameAs(savedWorkout);
            assertThat(s.getExercise()).isNotBlank();
            assertThat(s.getRepetitions()).isPositive();
        });

        assertThat(savedWorkoutSets.get(0).getExercise()).isEqualTo("Bench Press");
        assertThat(savedWorkoutSets.get(0).getBodyPart()).isEqualTo("chest");
        assertThat(savedWorkoutSets.get(0).getWeight()).isEqualByComparingTo(new BigDecimal("60.0"));
        assertThat(savedWorkoutSets.get(0).getRepetitions()).isEqualTo(8);

        assertThat(savedWorkoutSets.get(1).getExercise()).isEqualTo("Bench Press");
        assertThat(savedWorkoutSets.get(1).getBodyPart()).isEqualTo("chest");
        assertThat(savedWorkoutSets.get(1).getWeight()).isEqualByComparingTo(new BigDecimal("65.0"));
        assertThat(savedWorkoutSets.get(1).getRepetitions()).isEqualTo(6);

        assertThat(savedWorkoutSets.get(2).getExercise()).isEqualTo("Pull-ups");
        assertThat(savedWorkoutSets.get(2).getBodyPart()).isEqualTo("back");
        assertThat(savedWorkoutSets.get(2).getWeight()).isNull();
        assertThat(savedWorkoutSets.get(2).getRepetitions()).isEqualTo(10);

        assertThat(result).isSameAs(savedWorkout);

        verifyNoMoreInteractions(workoutRepository, workoutSetRepository, workoutFactory);
    }

    @Test
    void createWorkout_whenWorkoutForTodayAlreadyExists_appendsSetsToExistingWorkout() {
        LocalDate today = LocalDate.now();

        WorkoutSubmitRequestDto request = new WorkoutSubmitRequestDto(List.of(
                new WorkoutSubmitRequestDto.WorkoutBodyPartDto("chest", List.of(
                        new WorkoutSubmitRequestDto.WorkoutExerciseDto("Bench Press", new BigDecimal("60.0"), 8)
                ))
        ));

        Workout existing = new Workout();
        existing.setId(99L);
        existing.setWorkoutDate(today);

        when(workoutRepository.findByWorkoutDate(today)).thenReturn(Optional.of(existing));

        Workout result = workoutService.createWorkout(request);

        verify(workoutRepository).findByWorkoutDate(today);
        verifyNoInteractions(workoutFactory);
        verify(workoutRepository, never()).save(any(Workout.class));

        verify(workoutSetRepository).saveAll(setsCaptor.capture());
        List<WorkoutSet> saved = setsCaptor.getValue();
        assertThat(saved).hasSize(1);
        WorkoutSet persisted = saved.get(0);
        assertThat(persisted.getWorkout()).isSameAs(existing);
        assertThat(persisted.getBodyPart()).isEqualTo("chest");
        assertThat(persisted.getExercise()).isEqualTo("Bench Press");
        assertThat(persisted.getWeight()).isEqualByComparingTo(new BigDecimal("60.0"));
        assertThat(persisted.getRepetitions()).isEqualTo(8);
        assertThat(result).isSameAs(existing);

        verifyNoMoreInteractions(workoutRepository, workoutSetRepository, workoutFactory);
    }
}