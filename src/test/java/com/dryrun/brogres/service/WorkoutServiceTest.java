package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.Set;
import com.dryrun.brogres.model.ExcerciseEnum;
import com.dryrun.brogres.repo.SetRepository;
import com.dryrun.brogres.repo.WorkoutRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    @Mock private WorkoutRepository workoutRepository;
    @Mock private SetRepository setRepository;
    @Mock private WorkoutFactory workoutFactory;

    @InjectMocks private WorkoutService workoutService;

    @Test
    void testAddExerciseToWorkout_createsAndSavesSet() {
        // given
        ExcerciseEnum exercise = ExcerciseEnum.OVERHEAD_BB;
        Workout workout = new Workout();
        when(workoutFactory.createWorkout()).thenReturn(workout);
        when(workoutRepository.findById(1L)).thenReturn(Optional.of(workout));

        // when
        Workout result = workoutService.addExerciseToWorkout(1L, exercise);

        // then
        assertNotNull(result);
        verify(setRepository, times(1)).save(any(Set.class));

        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        verify(setRepository).save(captor.capture());
        Set savedSet = captor.getValue();

        assertEquals(workout, savedSet.getWorkout());
        assertEquals(exercise, savedSet.getExercise());
        assertEquals(1, savedSet.getRepetitions());
    }
}