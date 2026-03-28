package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutExerciseViewDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutPrefillDto;
import com.dryrun.brogres.data.WorkoutSet;
import com.dryrun.brogres.data.WorkoutSubmitRequestDto;
import com.dryrun.brogres.mapper.WorkoutSummaryMapper;
import com.dryrun.brogres.repo.WorkoutSetRepository;
import com.dryrun.brogres.repo.WorkoutRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Testy {@link WorkoutService}. Przy każdym {@code when}, {@code verify} i {@code assert*} jest komentarz intencji
 * (reguła workspace: {@code unit-test-intent-comments.mdc}).
 */
@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    @Mock
    WorkoutFactory workoutFactory;

    @Mock
    WorkoutRepository workoutRepository;

    @Mock
    WorkoutSetRepository workoutSetRepository;

    @Spy
    private WorkoutSummaryMapper workoutSummaryMapper = Mappers.getMapper(WorkoutSummaryMapper.class);

    @InjectMocks
    WorkoutService workoutService;

    @Captor
    ArgumentCaptor<Workout> workoutCaptor;

    @Captor
    ArgumentCaptor<List<WorkoutSet>> setsCaptor;

    /**
     * First workout of the day: creates a new {@link Workout}, persists it, saves all {@link WorkoutSet} rows from the DTO
     * in order, and returns the saved workout.
     */
    @Test
    void createWorkout_whenDtoProvided_savesWorkoutAndSetsWithExpectedValues() {
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
        when(workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today)).thenReturn(Optional.empty());
        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> {
            Workout w = invocation.getArgument(0);
            w.setId(123L);
            return w;
        });
        when(workoutSetRepository.findMaxLineOrderIndex(123L)).thenReturn(-1);

        Workout result = workoutService.createWorkout(request);

        verify(workoutRepository).findByWorkoutDate(today);
        verify(workoutRepository).save(workoutCaptor.capture());
        Workout savedWorkout = workoutCaptor.getValue();
        assertThat(savedWorkout.getWorkoutDate()).isEqualTo(today);
        assertThat(savedWorkout.getId()).isEqualTo(123L);

        verify(workoutSetRepository).findMaxLineOrderIndex(123L);
        verify(workoutSetRepository).saveAll(setsCaptor.capture());
        List<WorkoutSet> savedWorkoutSets = setsCaptor.getValue();

        assertThat(savedWorkoutSets).hasSize(3);
        assertThat(savedWorkoutSets).allSatisfy(s -> {
            assertThat(s.getWorkout()).isSameAs(savedWorkout);
            assertThat(s.getExercise()).isNotBlank();
            assertThat(s.getRepetitions()).isPositive();
            assertThat(s.isPlanned()).isFalse();
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

        assertThat(savedWorkoutSets.get(0).getLineOrder()).isZero();
        assertThat(savedWorkoutSets.get(1).getLineOrder()).isEqualTo(1);
        assertThat(savedWorkoutSets.get(2).getLineOrder()).isEqualTo(2);

        assertThat(result).isSameAs(savedWorkout);

        verify(workoutRepository).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today);
        verifyNoMoreInteractions(workoutRepository, workoutSetRepository, workoutFactory);
    }

    /**
     * First workout of the day with history: copies previous session’s executed sets as {@code planned=true} rows, then saves POST sets with {@code planned=false}.
     */
    @Test
    void createWorkout_whenNewDayAndPreviousWorkoutExists_copiesPreviousExecutedSetsAsPlanned() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        WorkoutSubmitRequestDto request = new WorkoutSubmitRequestDto(List.of(
                new WorkoutSubmitRequestDto.WorkoutBodyPartDto("chest", List.of(
                        new WorkoutSubmitRequestDto.WorkoutExerciseDto("Bench Press", new BigDecimal("60.0"), 8)
                ))
        ));

        Workout factoryWorkout = new Workout();
        when(workoutFactory.createWorkout()).thenReturn(factoryWorkout);
        when(workoutRepository.findByWorkoutDate(today)).thenReturn(Optional.empty());

        Workout previous = new Workout();
        previous.setId(50L);
        previous.setWorkoutDate(yesterday);
        WorkoutSet prevSet = new WorkoutSet();
        prevSet.setId(100L);
        prevSet.setWorkout(previous);
        prevSet.setBodyPart("chest");
        prevSet.setExercise("Fly");
        prevSet.setWeight(new BigDecimal("40"));
        prevSet.setRepetitions(12);
        prevSet.setLineOrder(0);
        prevSet.setPlanned(false);
        previous.getSets().add(prevSet);

        when(workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today)).thenReturn(Optional.of(previous));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> {
            Workout w = invocation.getArgument(0);
            w.setId(123L);
            return w;
        });
        when(workoutSetRepository.findMaxLineOrderIndex(123L)).thenReturn(0);

        workoutService.createWorkout(request);

        verify(workoutSetRepository, times(2)).saveAll(setsCaptor.capture());
        List<List<WorkoutSet>> batches = setsCaptor.getAllValues();
        assertThat(batches).hasSize(2);

        List<WorkoutSet> plannedBatch = batches.get(0);
        assertThat(plannedBatch).hasSize(1);
        assertThat(plannedBatch.get(0).isPlanned()).isTrue();
        assertThat(plannedBatch.get(0).getExercise()).isEqualTo("Fly");
        assertThat(plannedBatch.get(0).getLineOrder()).isZero();
        assertThat(plannedBatch.get(0).getWorkout().getId()).isEqualTo(123L);

        List<WorkoutSet> executedBatch = batches.get(1);
        assertThat(executedBatch).hasSize(1);
        assertThat(executedBatch.get(0).isPlanned()).isFalse();
        assertThat(executedBatch.get(0).getExercise()).isEqualTo("Bench Press");
        assertThat(executedBatch.get(0).getLineOrder()).isEqualTo(1);

        verify(workoutRepository).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today);
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
        when(workoutSetRepository.findMaxLineOrderIndex(99L)).thenReturn(-1);

        Workout result = workoutService.createWorkout(request);

        verify(workoutRepository).findByWorkoutDate(today);
        verify(workoutSetRepository).findMaxLineOrderIndex(99L);
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
        assertThat(persisted.getLineOrder()).isZero();
        assertThat(persisted.isPlanned()).isFalse();
        assertThat(result).isSameAs(existing);

        verifyNoMoreInteractions(workoutRepository, workoutSetRepository, workoutFactory);
    }

    @Test
    void prefillWorkout_whenWorkoutAlreadyExistsForToday_returnsPlannedSetsOnly() {
        LocalDate today = LocalDate.now();
        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(true);

        Workout todayW = new Workout();
        todayW.setId(5L);
        todayW.setWorkoutDate(today);
        WorkoutSet planned = new WorkoutSet();
        planned.setId(1L);
        planned.setWorkout(todayW);
        planned.setBodyPart("chest");
        planned.setExercise("Bench");
        planned.setWeight(new BigDecimal("50"));
        planned.setRepetitions(5);
        planned.setLineOrder(0);
        planned.setPlanned(true);
        todayW.getSets().add(planned);

        when(workoutRepository.findByWorkoutDate(today)).thenReturn(Optional.of(todayW));

        WorkoutPrefillDto result = workoutService.prefillWorkout();

        assertThat(result.bodyPart()).hasSize(1);
        assertThat(result.bodyPart().get(0).bodyPartName()).isEqualTo("chest");
        assertThat(result.bodyPart().get(0).exercises()).containsExactly(
                new WorkoutExerciseViewDto("Bench", 0, new BigDecimal("50"), 5, true));

        verify(workoutRepository).existsByWorkoutDate(today);
        verify(workoutRepository).findByWorkoutDate(today);
        verify(workoutRepository, never()).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(any());
        verifyNoInteractions(workoutFactory, workoutSetRepository);
        verifyNoMoreInteractions(workoutRepository);
    }

    @Test
    void prefillWorkout_whenWorkoutExistsForTodayButPlanEmpty_returnsEmptyBodyPart() {
        LocalDate today = LocalDate.now();
        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(true);

        Workout todayW = new Workout();
        todayW.setId(5L);
        todayW.setWorkoutDate(today);
        when(workoutRepository.findByWorkoutDate(today)).thenReturn(Optional.of(todayW));

        WorkoutPrefillDto result = workoutService.prefillWorkout();
        assertThat(result.bodyPart()).isEmpty();

        verify(workoutRepository).existsByWorkoutDate(today);
        verify(workoutRepository).findByWorkoutDate(today);
        verifyNoInteractions(workoutFactory, workoutSetRepository);
    }

    @Test
    void prefillWorkout_whenNoWorkoutTodayButEarlierWorkoutExists_mapsExecutedSetsToBodyPartStructure() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        Workout previous = new Workout();
        previous.setId(7L);
        previous.setWorkoutDate(yesterday);

        WorkoutSet s1 = new WorkoutSet();
        s1.setId(10L);
        s1.setWorkout(previous);
        s1.setBodyPart("chest");
        s1.setExercise("Bench Press");
        s1.setWeight(new BigDecimal("60.0"));
        s1.setRepetitions(8);
        s1.setLineOrder(0);
        s1.setPlanned(false);

        WorkoutSet s2 = new WorkoutSet();
        s2.setId(11L);
        s2.setWorkout(previous);
        s2.setBodyPart("chest");
        s2.setExercise("Bench Press");
        s2.setWeight(new BigDecimal("65.0"));
        s2.setRepetitions(6);
        s2.setLineOrder(1);
        s2.setPlanned(false);

        WorkoutSet s3 = new WorkoutSet();
        s3.setId(12L);
        s3.setWorkout(previous);
        s3.setBodyPart("back");
        s3.setExercise("Pull-ups");
        s3.setWeight(null);
        s3.setRepetitions(10);
        s3.setLineOrder(2);
        s3.setPlanned(false);

        previous.getSets().addAll(List.of(s3, s1, s2));

        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(false);
        when(workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today))
                .thenReturn(Optional.of(previous));

        WorkoutPrefillDto result = workoutService.prefillWorkout();

        assertThat(result.bodyPart()).hasSize(2);
        assertThat(result.bodyPart().get(0).bodyPartName()).isEqualTo("chest");
        assertThat(result.bodyPart().get(0).exercises()).containsExactly(
                new WorkoutExerciseViewDto("Bench Press", 0, new BigDecimal("60.0"), 8, false),
                new WorkoutExerciseViewDto("Bench Press", 1, new BigDecimal("65.0"), 6, false)
        );
        assertThat(result.bodyPart().get(1).bodyPartName()).isEqualTo("back");
        assertThat(result.bodyPart().get(1).exercises()).containsExactly(
                new WorkoutExerciseViewDto("Pull-ups", 2, null, 10, false)
        );

        verify(workoutRepository).existsByWorkoutDate(today);
        verify(workoutRepository).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today);
        verifyNoInteractions(workoutFactory, workoutSetRepository);
        verifyNoMoreInteractions(workoutRepository);
    }

    @Test
    void prefillWorkout_whenNoWorkoutTodayAndRepositoryFindsNoEarlierWorkout_returnsEmptyBodyPartList() {
        LocalDate today = LocalDate.now();
        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(false);
        when(workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today))
                .thenReturn(Optional.empty());

        WorkoutPrefillDto result = workoutService.prefillWorkout();
        assertThat(result.bodyPart()).isEmpty();

        verify(workoutRepository).existsByWorkoutDate(today);
        verify(workoutRepository).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today);
        verifyNoInteractions(workoutFactory, workoutSetRepository);
        verifyNoMoreInteractions(workoutRepository);
    }

    @Test
    void prefillWorkout_whenEarlierWorkoutHasNoSets_returnsEmptyBodyPartList() {
        LocalDate today = LocalDate.now();

        Workout shell = new Workout();
        shell.setId(1L);
        shell.setWorkoutDate(today.minusDays(3));

        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(false);
        when(workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today))
                .thenReturn(Optional.of(shell));

        WorkoutPrefillDto result = workoutService.prefillWorkout();
        assertThat(result.bodyPart()).isEmpty();

        verify(workoutRepository).existsByWorkoutDate(today);
        verify(workoutRepository).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today);
        verifyNoInteractions(workoutFactory, workoutSetRepository);
        verifyNoMoreInteractions(workoutRepository);
    }

    /**
     * Gdy ostatnia sesja ma tylko wiersze planu ({@code planned=true}), nie klonujemy ich do nowego dnia jako plan.
     */
    @Test
    void createWorkout_whenNewDayButPreviousHasOnlyPlannedRows_doesNotCopyPlanAsSnapshot() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        WorkoutSubmitRequestDto request = new WorkoutSubmitRequestDto(List.of(
                new WorkoutSubmitRequestDto.WorkoutBodyPartDto("chest", List.of(
                        new WorkoutSubmitRequestDto.WorkoutExerciseDto("X", BigDecimal.ONE, 1)
                ))
        ));

        when(workoutFactory.createWorkout()).thenReturn(new Workout());
        when(workoutRepository.findByWorkoutDate(today)).thenReturn(Optional.empty());

        Workout previous = new Workout();
        previous.setId(50L);
        previous.setWorkoutDate(yesterday);
        WorkoutSet onlyPlanned = new WorkoutSet();
        onlyPlanned.setWorkout(previous);
        onlyPlanned.setPlanned(true);
        onlyPlanned.setBodyPart("chest");
        onlyPlanned.setExercise("Ghost");
        onlyPlanned.setRepetitions(1);
        onlyPlanned.setLineOrder(0);
        previous.getSets().add(onlyPlanned);

        when(workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today)).thenReturn(Optional.of(previous));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> {
            Workout w = invocation.getArgument(0);
            w.setId(123L);
            return w;
        });
        when(workoutSetRepository.findMaxLineOrderIndex(123L)).thenReturn(-1);

        workoutService.createWorkout(request);

        verify(workoutSetRepository, times(1)).saveAll(anyList());
    }
}
