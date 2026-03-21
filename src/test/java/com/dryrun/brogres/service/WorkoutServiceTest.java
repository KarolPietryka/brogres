package com.dryrun.brogres.service;

import com.dryrun.brogres.data.WorkoutSet;
import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutSubmitRequestDto;
import com.dryrun.brogres.model.ExcerciseEnum;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
               "bodyPartName": "CHEST",
               "exercises": [
                 { "name": "OVERHEAD_BB", "weight": 60.0, "reps": 8 },
                 { "name": "OVERHEAD_BB", "weight": 65.0, "reps": 6 }
               ]
             },
             {
               "bodyPartName": "BACK",
               "exercises": [
                 { "name": "OVERHEAD_BB", "weight": null, "reps": 10 }
               ]
             }
           ]
         }
        */

        LocalDate today = LocalDate.now();

        WorkoutSubmitRequestDto request = new WorkoutSubmitRequestDto(List.of(
                new WorkoutSubmitRequestDto.WorkoutBodyPartDto("CHEST", List.of(
                        new WorkoutSubmitRequestDto.WorkoutExerciseDto(ExcerciseEnum.OVERHEAD_BB, new BigDecimal("60.0"), 8),
                        new WorkoutSubmitRequestDto.WorkoutExerciseDto(ExcerciseEnum.OVERHEAD_BB, new BigDecimal("65.0"), 6)
                )),
                new WorkoutSubmitRequestDto.WorkoutBodyPartDto("BACK", List.of(
                        new WorkoutSubmitRequestDto.WorkoutExerciseDto(ExcerciseEnum.OVERHEAD_BB, null, 10)
                ))
        ));

        Workout factoryWorkout = new Workout();
        when(workoutFactory.createWorkout()).thenReturn(factoryWorkout);

        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(false);

        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> {
            Workout w = invocation.getArgument(0);
            w.setId(123L);
            return w;
        });

        Workout result = workoutService.createWorkout(request);

        verify(workoutRepository).existsByWorkoutDate(today);

        verify(workoutRepository).save(workoutCaptor.capture());
        Workout savedWorkout = workoutCaptor.getValue();
        assertThat(savedWorkout.getWorkoutDate()).isEqualTo(today);
        assertThat(savedWorkout.getId()).isEqualTo(123L);

        verify(workoutSetRepository).saveAll(setsCaptor.capture());
        List<WorkoutSet> savedWorkoutSets = setsCaptor.getValue();

        assertThat(savedWorkoutSets).hasSize(3);
        assertThat(savedWorkoutSets).allSatisfy(s -> {
            assertThat(s.getWorkout()).isSameAs(savedWorkout);
            assertThat(s.getExercise()).isNotNull();
            assertThat(s.getRepetitions()).isPositive();
        });

        assertThat(savedWorkoutSets.get(0).getExercise()).isEqualTo(ExcerciseEnum.OVERHEAD_BB);
        assertThat(savedWorkoutSets.get(0).getRepetitions()).isEqualTo(8);

        assertThat(savedWorkoutSets.get(1).getExercise()).isEqualTo(ExcerciseEnum.OVERHEAD_BB);
        assertThat(savedWorkoutSets.get(1).getRepetitions()).isEqualTo(6);

        assertThat(savedWorkoutSets.get(2).getExercise()).isEqualTo(ExcerciseEnum.OVERHEAD_BB);
        assertThat(savedWorkoutSets.get(2).getRepetitions()).isEqualTo(10);

        assertThat(result).isSameAs(savedWorkout);

        verifyNoMoreInteractions(workoutRepository, workoutSetRepository, workoutFactory);
    }

    @Test
    void createWorkout_whenWorkoutForTodayAlreadyExists_doesNotSaveAnything() {
        LocalDate today = LocalDate.now();

        WorkoutSubmitRequestDto request = new WorkoutSubmitRequestDto(List.of(
                new WorkoutSubmitRequestDto.WorkoutBodyPartDto("CHEST", List.of(
                        new WorkoutSubmitRequestDto.WorkoutExerciseDto(ExcerciseEnum.OVERHEAD_BB, new BigDecimal("60.0"), 8)
                ))
        ));

        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(true);

        assertThatThrownBy(() -> workoutService.createWorkout(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Workout for current day already exists");

        verify(workoutRepository).existsByWorkoutDate(today);

        verifyNoInteractions(workoutFactory);
        verify(workoutRepository, never()).save(any(Workout.class));
        verifyNoInteractions(workoutSetRepository);

        verifyNoMoreInteractions(workoutRepository);
    }
}