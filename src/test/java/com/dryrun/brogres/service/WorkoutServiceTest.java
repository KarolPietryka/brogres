package com.dryrun.brogres.service;

import com.dryrun.brogres.data.AppUser;
import com.dryrun.brogres.data.Exercise;
import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.model.WorkoutResponseDtos.WorkoutExerciseViewDto;
import com.dryrun.brogres.model.WorkoutResponseDtos.WorkoutPrefillDto;
import com.dryrun.brogres.data.WorkoutSet;
import com.dryrun.brogres.data.WorkoutSetStatus;
import com.dryrun.brogres.model.WorkoutSubmitRequestDto;
import com.dryrun.brogres.mapper.WorkoutSummaryMapper;
import com.dryrun.brogres.repo.AppUserRepository;
import com.dryrun.brogres.repo.ExerciseRepository;
import com.dryrun.brogres.repo.WorkoutSetRepository;
import com.dryrun.brogres.repo.WorkoutRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Testy {@link WorkoutService}. Przy każdym {@code when}, {@code verify} i {@code assert*} jest komentarz intencji
 * (reguła workspace: {@code unit-test-intent-comments.mdc}).
 */
@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    private static final long USER_ID = 1L;

    private final AtomicLong exerciseIdSeq = new AtomicLong(4000L);

    @Mock
    WorkoutFactory workoutFactory;

    @Mock
    AppUserRepository appUserRepository;

    @Mock
    ExerciseRepository exerciseRepository;

    @Mock
    WorkoutRepository workoutRepository;

    @Mock
    WorkoutSetRepository workoutSetRepository;

    @Spy
    private WorkoutSummaryMapper workoutSummaryMapper = Mappers.getMapper(WorkoutSummaryMapper.class);

    @InjectMocks
    WorkoutService workoutService;

    @BeforeEach
    void stubCurrentUser() {
        AppUser user = new AppUser();
        user.setId(USER_ID);
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(appUserRepository.getReferenceById(USER_ID)).thenReturn(user);
        // When: no pre-existing Exercise row — resolve falls through to save() for a new user-owned definition.
        when(exerciseRepository.findByUser_IdAndBodyPartAndName(eq(USER_ID), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(exerciseRepository.findByUserIsNullAndBodyPartAndName(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(invocation -> {
            Exercise ex = invocation.getArgument(0);
            if (ex.getId() == null) {
                ex.setId(exerciseIdSeq.incrementAndGet());
            }
            return ex;
        });
    }

    @Captor
    ArgumentCaptor<Workout> workoutCaptor;

    @Captor
    ArgumentCaptor<List<WorkoutSet>> setsCaptor;

    /**
     * When no workout exists for today: creates a {@link Workout}, persists only POST rows as {@link WorkoutSetStatus#DONE}
     * (no DB snapshot from previous sessions — prefill is read-side only).
     */
    @Test
    void createWorkout_whenDtoProvided_savesWorkoutAndSetsWithExpectedValues() {
        LocalDate today = LocalDate.now();

        WorkoutSubmitRequestDto request = new WorkoutSubmitRequestDto(List.of(
                new WorkoutSubmitRequestDto.WorkoutExerciseDto("chest", "Bench Press", null, new BigDecimal("60.0"), 8, null),
                new WorkoutSubmitRequestDto.WorkoutExerciseDto("chest", "Bench Press", null, new BigDecimal("65.0"), 6, null),
                new WorkoutSubmitRequestDto.WorkoutExerciseDto("back", "Pull-ups", null, null, 10, null)
        ));

        Workout factoryWorkout = new Workout();
        when(workoutFactory.createWorkout()).thenReturn(factoryWorkout);
        when(workoutRepository.findByWorkoutDateAndUser_Id(today, USER_ID)).thenReturn(Optional.empty());
        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> {
            Workout w = invocation.getArgument(0);
            w.setId(123L);
            return w;
        });

        Workout result = workoutService.createWorkout(USER_ID, request);

        verify(workoutRepository).findByWorkoutDateAndUser_Id(today, USER_ID);
        verify(workoutRepository).save(workoutCaptor.capture());
        Workout savedWorkout = workoutCaptor.getValue();
        assertThat(savedWorkout.getWorkoutDate()).isEqualTo(today);
        assertThat(savedWorkout.getId()).isEqualTo(123L);

        verify(workoutSetRepository, never()).deleteAllByWorkoutId(any());
        verify(workoutSetRepository).saveAll(setsCaptor.capture());
        List<WorkoutSet> savedWorkoutSets = setsCaptor.getValue();

        assertThat(savedWorkoutSets).hasSize(3);
        assertThat(savedWorkoutSets).allSatisfy(s -> {
            assertThat(s.getWorkout()).isSameAs(savedWorkout);
            assertThat(s.getExercise().getName()).isNotBlank();
            assertThat(s.getRepetitions()).isPositive();
            assertThat(s.getStatus()).isEqualTo(WorkoutSetStatus.DONE);
        });

        assertThat(savedWorkoutSets.get(0).getExercise().getName()).isEqualTo("Bench Press");
        assertThat(savedWorkoutSets.get(0).getBodyPart()).isEqualTo("chest");
        assertThat(savedWorkoutSets.get(0).getWeight()).isEqualByComparingTo(new BigDecimal("60.0"));
        assertThat(savedWorkoutSets.get(0).getRepetitions()).isEqualTo(8);

        assertThat(savedWorkoutSets.get(1).getExercise().getName()).isEqualTo("Bench Press");
        assertThat(savedWorkoutSets.get(1).getBodyPart()).isEqualTo("chest");
        assertThat(savedWorkoutSets.get(1).getWeight()).isEqualByComparingTo(new BigDecimal("65.0"));
        assertThat(savedWorkoutSets.get(1).getRepetitions()).isEqualTo(6);

        assertThat(savedWorkoutSets.get(2).getExercise().getName()).isEqualTo("Pull-ups");
        assertThat(savedWorkoutSets.get(2).getBodyPart()).isEqualTo("back");
        assertThat(savedWorkoutSets.get(2).getWeight()).isNull();
        assertThat(savedWorkoutSets.get(2).getRepetitions()).isEqualTo(10);

        assertThat(savedWorkoutSets.get(0).getLineOrder()).isZero();
        assertThat(savedWorkoutSets.get(1).getLineOrder()).isEqualTo(1);
        assertThat(savedWorkoutSets.get(2).getLineOrder()).isEqualTo(2);

        assertThat(result).isSameAs(savedWorkout);

        verify(workoutRepository, never()).findFirstByUser_IdAndWorkoutDateLessThanOrderByWorkoutDateDesc(any(), any());
        verifyNoMoreInteractions(workoutRepository, workoutSetRepository, workoutFactory, exerciseRepository);
    }

    /**
     * POST is a full replace: previous sessions are not copied into the DB on create; only the submitted rows are stored.
     */
    @Test
    void createWorkout_whenNewDayAndPreviousWorkoutExists_savesOnlyPostRows() {
        LocalDate today = LocalDate.now();

        WorkoutSubmitRequestDto request = new WorkoutSubmitRequestDto(List.of(
                new WorkoutSubmitRequestDto.WorkoutExerciseDto("chest", "Bench Press", null, new BigDecimal("60.0"), 8, null)
        ));

        Workout factoryWorkout = new Workout();
        when(workoutFactory.createWorkout()).thenReturn(factoryWorkout);
        when(workoutRepository.findByWorkoutDateAndUser_Id(today, USER_ID)).thenReturn(Optional.empty());
        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> {
            Workout w = invocation.getArgument(0);
            w.setId(123L);
            return w;
        });

        workoutService.createWorkout(USER_ID, request);

        verify(workoutSetRepository, times(1)).saveAll(setsCaptor.capture());
        assertThat(setsCaptor.getValue()).hasSize(1);
        assertThat(setsCaptor.getValue().get(0).getExercise().getName()).isEqualTo("Bench Press");
        assertThat(setsCaptor.getValue().get(0).getStatus()).isEqualTo(WorkoutSetStatus.DONE);
        verify(workoutRepository, never()).findFirstByUser_IdAndWorkoutDateLessThanOrderByWorkoutDateDesc(any(), any());
    }

    /**
     * Client-sent statuses pass through as-is: PLANNED stays PLANNED, DONE stays DONE, {@code null} defaults to DONE.
     * This is the new progress-bar contract — no NEXT→DONE mapping anymore.
     */
    @Test
    void createWorkout_persistsClientStatusesAsIsAndDefaultsNullToDone() {
        LocalDate today = LocalDate.now();

        WorkoutSubmitRequestDto request = new WorkoutSubmitRequestDto(List.of(
                new WorkoutSubmitRequestDto.WorkoutExerciseDto("chest", "Done row", null, BigDecimal.TEN, 8, WorkoutSetStatus.DONE),
                new WorkoutSubmitRequestDto.WorkoutExerciseDto("chest", "Planned row", null, BigDecimal.TEN, 8, WorkoutSetStatus.PLANNED),
                new WorkoutSubmitRequestDto.WorkoutExerciseDto("chest", "Legacy null row", null, BigDecimal.TEN, 8, null)
        ));

        when(workoutFactory.createWorkout()).thenReturn(new Workout());
        when(workoutRepository.findByWorkoutDateAndUser_Id(today, USER_ID)).thenReturn(Optional.empty());
        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> {
            Workout w = invocation.getArgument(0);
            w.setId(1L);
            return w;
        });

        workoutService.createWorkout(USER_ID, request);

        // Then: persisted rows keep the exact sent status; null defaults to DONE (legacy fallback).
        verify(workoutSetRepository).saveAll(setsCaptor.capture());
        List<WorkoutSet> saved = setsCaptor.getValue();
        assertThat(saved).hasSize(3);
        assertThat(saved.get(0).getStatus()).isEqualTo(WorkoutSetStatus.DONE);
        assertThat(saved.get(1).getStatus()).isEqualTo(WorkoutSetStatus.PLANNED);
        assertThat(saved.get(2).getStatus()).isEqualTo(WorkoutSetStatus.DONE);
    }

    /**
     * When today’s workout already exists: delete all its sets, then insert the POST snapshot as DONE from line 0.
     */
    @Test
    void createWorkout_whenWorkoutForTodayAlreadyExists_replacesAllSets() {
        LocalDate today = LocalDate.now();

        WorkoutSubmitRequestDto request = new WorkoutSubmitRequestDto(List.of(
                new WorkoutSubmitRequestDto.WorkoutExerciseDto("chest", "Bench Press", null, new BigDecimal("60.0"), 8, null)
        ));

        Workout existing = new Workout();
        existing.setId(99L);
        existing.setWorkoutDate(today);

        when(workoutRepository.findByWorkoutDateAndUser_Id(today, USER_ID)).thenReturn(Optional.of(existing));

        Workout result = workoutService.createWorkout(USER_ID, request);

        verify(workoutRepository).findByWorkoutDateAndUser_Id(today, USER_ID);
        verify(workoutSetRepository).deleteAllByWorkoutId(99L);
        verify(workoutSetRepository).flush();
        verifyNoInteractions(workoutFactory);
        verify(workoutRepository, never()).save(any(Workout.class));

        verify(workoutSetRepository).saveAll(setsCaptor.capture());
        List<WorkoutSet> saved = setsCaptor.getValue();
        assertThat(saved).hasSize(1);
        WorkoutSet persisted = saved.get(0);
        assertThat(persisted.getWorkout()).isSameAs(existing);
        assertThat(persisted.getBodyPart()).isEqualTo("chest");
        assertThat(persisted.getExercise().getName()).isEqualTo("Bench Press");
        assertThat(persisted.getWeight()).isEqualByComparingTo(new BigDecimal("60.0"));
        assertThat(persisted.getRepetitions()).isEqualTo(8);
        assertThat(persisted.getLineOrder()).isZero();
        assertThat(persisted.getStatus()).isEqualTo(WorkoutSetStatus.DONE);
        assertThat(result).isSameAs(existing);

        verifyNoMoreInteractions(workoutRepository, workoutSetRepository, workoutFactory, exerciseRepository);
    }

    /**
     * Today's prefill returns each set with its persisted status — no NEXT marker, no DONE→PLANNED rewrite.
     * The FE derives the progress-bar position from the count of leading DONE rows.
     */
    @Test
    void prefillWorkout_whenWorkoutExistsForToday_returnsSetsWithPersistedStatus() {
        LocalDate today = LocalDate.now();
        when(workoutRepository.existsByWorkoutDateAndUser_Id(today, USER_ID)).thenReturn(true);

        Workout todayW = new Workout();
        todayW.setId(5L);
        todayW.setWorkoutDate(today);
        WorkoutSet planned = new WorkoutSet();
        planned.setId(1L);
        planned.setWorkout(todayW);
        planned.setBodyPart("chest");
        planned.setExercise(ex(501L, "chest", "Bench"));
        planned.setWeight(new BigDecimal("50"));
        planned.setRepetitions(5);
        planned.setLineOrder(0);
        planned.setStatus(WorkoutSetStatus.PLANNED);
        todayW.getSets().add(planned);

        when(workoutRepository.findByWorkoutDateAndUser_Id(today, USER_ID)).thenReturn(Optional.of(todayW));

        WorkoutPrefillDto result = workoutService.prefillWorkout(USER_ID);

        // Then: the single PLANNED row is returned as PLANNED (bar at the top on FE — zero DONEs).
        assertThat(result.bodyPart()).containsExactly(
                new WorkoutExerciseViewDto("chest", "Bench", 501L, 0, new BigDecimal("50"), 5, WorkoutSetStatus.PLANNED));

        verify(workoutRepository).existsByWorkoutDateAndUser_Id(today, USER_ID);
        verify(workoutRepository).findByWorkoutDateAndUser_Id(today, USER_ID);
        verify(workoutRepository, never()).findFirstByUser_IdAndWorkoutDateLessThanOrderByWorkoutDateDesc(any(), any());
        verifyNoInteractions(workoutFactory, workoutSetRepository);
        verifyNoMoreInteractions(workoutRepository);
    }

    /**
     * Today's prefill returns DONE and PLANNED rows as-is (mixed sessions): no status rewriting on the BE.
     */
    @Test
    void prefillWorkout_whenWorkoutExistsForToday_returnsDoneAndPlannedWithoutRewrite() {
        LocalDate today = LocalDate.now();
        when(workoutRepository.existsByWorkoutDateAndUser_Id(today, USER_ID)).thenReturn(true);

        Workout todayW = new Workout();
        todayW.setId(5L);
        todayW.setWorkoutDate(today);

        WorkoutSet done = new WorkoutSet();
        done.setId(1L);
        done.setWorkout(todayW);
        done.setBodyPart("chest");
        done.setExercise(ex(502L, "chest", "Squat"));
        done.setWeight(new BigDecimal("100"));
        done.setRepetitions(5);
        done.setLineOrder(0);
        done.setStatus(WorkoutSetStatus.DONE);

        WorkoutSet planned = new WorkoutSet();
        planned.setId(2L);
        planned.setWorkout(todayW);
        planned.setBodyPart("chest");
        planned.setExercise(ex(501L, "chest", "Bench"));
        planned.setWeight(new BigDecimal("50"));
        planned.setRepetitions(5);
        planned.setLineOrder(1);
        planned.setStatus(WorkoutSetStatus.PLANNED);

        todayW.getSets().addAll(List.of(done, planned));
        when(workoutRepository.findByWorkoutDateAndUser_Id(today, USER_ID)).thenReturn(Optional.of(todayW));

        WorkoutPrefillDto result = workoutService.prefillWorkout(USER_ID);

        // Then: DONE and PLANNED pass through untouched — FE places the bar after the leading DONE rows.
        assertThat(result.bodyPart()).containsExactly(
                new WorkoutExerciseViewDto("chest", "Squat", 502L, 0, new BigDecimal("100"), 5, WorkoutSetStatus.DONE),
                new WorkoutExerciseViewDto("chest", "Bench", 501L, 1, new BigDecimal("50"), 5, WorkoutSetStatus.PLANNED));
    }

    @Test
    void prefillWorkout_whenWorkoutExistsForTodayButPlanEmpty_returnsEmptyBodyPart() {
        LocalDate today = LocalDate.now();
        when(workoutRepository.existsByWorkoutDateAndUser_Id(today, USER_ID)).thenReturn(true);

        Workout todayW = new Workout();
        todayW.setId(5L);
        todayW.setWorkoutDate(today);
        when(workoutRepository.findByWorkoutDateAndUser_Id(today, USER_ID)).thenReturn(Optional.of(todayW));

        WorkoutPrefillDto result = workoutService.prefillWorkout(USER_ID);
        assertThat(result.bodyPart()).isEmpty();

        verify(workoutRepository).existsByWorkoutDateAndUser_Id(today, USER_ID);
        verify(workoutRepository).findByWorkoutDateAndUser_Id(today, USER_ID);
        verifyNoInteractions(workoutFactory, workoutSetRepository);
    }

    @Test
    void prefillWorkout_whenNoWorkoutTodayButEarlierWorkoutExists_mapsExecutedSetsToFlatExerciseList() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        Workout previous = new Workout();
        previous.setId(7L);
        previous.setWorkoutDate(yesterday);

        WorkoutSet s1 = new WorkoutSet();
        s1.setId(10L);
        s1.setWorkout(previous);
        s1.setBodyPart("chest");
        s1.setExercise(ex(601L, "chest", "Bench Press"));
        s1.setWeight(new BigDecimal("60.0"));
        s1.setRepetitions(8);
        s1.setLineOrder(0);
        s1.setStatus(WorkoutSetStatus.DONE);

        WorkoutSet s2 = new WorkoutSet();
        s2.setId(11L);
        s2.setWorkout(previous);
        s2.setBodyPart("chest");
        s2.setExercise(ex(601L, "chest", "Bench Press"));
        s2.setWeight(new BigDecimal("65.0"));
        s2.setRepetitions(6);
        s2.setLineOrder(1);
        s2.setStatus(WorkoutSetStatus.DONE);

        WorkoutSet s3 = new WorkoutSet();
        s3.setId(12L);
        s3.setWorkout(previous);
        s3.setBodyPart("back");
        s3.setExercise(ex(602L, "back", "Pull-ups"));
        s3.setWeight(null);
        s3.setRepetitions(10);
        s3.setLineOrder(2);
        s3.setStatus(WorkoutSetStatus.DONE);

        previous.getSets().addAll(List.of(s3, s1, s2));

        when(workoutRepository.existsByWorkoutDateAndUser_Id(today, USER_ID)).thenReturn(false);
        when(workoutRepository.findFirstByUser_IdAndWorkoutDateLessThanOrderByWorkoutDateDesc(USER_ID, today))
                .thenReturn(Optional.of(previous));

        WorkoutPrefillDto result = workoutService.prefillWorkout(USER_ID);

        // Then: every row from the previous session is flattened to PLANNED — bar sits at the top for a fresh session.
        assertThat(result.bodyPart()).containsExactly(
                new WorkoutExerciseViewDto("chest", "Bench Press", 601L, 0, new BigDecimal("60.0"), 8, WorkoutSetStatus.PLANNED),
                new WorkoutExerciseViewDto("chest", "Bench Press", 601L, 1, new BigDecimal("65.0"), 6, WorkoutSetStatus.PLANNED),
                new WorkoutExerciseViewDto("back", "Pull-ups", 602L, 2, null, 10, WorkoutSetStatus.PLANNED));

        verify(workoutRepository).existsByWorkoutDateAndUser_Id(today, USER_ID);
        verify(workoutRepository).findFirstByUser_IdAndWorkoutDateLessThanOrderByWorkoutDateDesc(USER_ID, today);
        verifyNoInteractions(workoutFactory, workoutSetRepository);
        verifyNoMoreInteractions(workoutRepository);
    }

    @Test
    void prefillWorkout_whenNoWorkoutTodayAndRepositoryFindsNoEarlierWorkout_returnsEmptyBodyPartList() {
        LocalDate today = LocalDate.now();
        when(workoutRepository.existsByWorkoutDateAndUser_Id(today, USER_ID)).thenReturn(false);
        when(workoutRepository.findFirstByUser_IdAndWorkoutDateLessThanOrderByWorkoutDateDesc(USER_ID, today))
                .thenReturn(Optional.empty());

        WorkoutPrefillDto result = workoutService.prefillWorkout(USER_ID);
        assertThat(result.bodyPart()).isEmpty();

        verify(workoutRepository).existsByWorkoutDateAndUser_Id(today, USER_ID);
        verify(workoutRepository).findFirstByUser_IdAndWorkoutDateLessThanOrderByWorkoutDateDesc(USER_ID, today);
        verifyNoInteractions(workoutFactory, workoutSetRepository);
        verifyNoMoreInteractions(workoutRepository);
    }

    @Test
    void prefillWorkout_whenEarlierWorkoutHasNoSets_returnsEmptyBodyPartList() {
        LocalDate today = LocalDate.now();

        Workout shell = new Workout();
        shell.setId(1L);
        shell.setWorkoutDate(today.minusDays(3));

        when(workoutRepository.existsByWorkoutDateAndUser_Id(today, USER_ID)).thenReturn(false);
        when(workoutRepository.findFirstByUser_IdAndWorkoutDateLessThanOrderByWorkoutDateDesc(USER_ID, today))
                .thenReturn(Optional.of(shell));

        WorkoutPrefillDto result = workoutService.prefillWorkout(USER_ID);
        assertThat(result.bodyPart()).isEmpty();

        verify(workoutRepository).existsByWorkoutDateAndUser_Id(today, USER_ID);
        verify(workoutRepository).findFirstByUser_IdAndWorkoutDateLessThanOrderByWorkoutDateDesc(USER_ID, today);
        verifyNoInteractions(workoutFactory, workoutSetRepository);
        verifyNoMoreInteractions(workoutRepository);
    }

    /**
     * New day POST persists only the request body; no implicit copy from a prior workout.
     */
    @Test
    void createWorkout_whenNewDayButPreviousHasOnlyPlannedRows_savesOnlyPost() {
        LocalDate today = LocalDate.now();

        WorkoutSubmitRequestDto request = new WorkoutSubmitRequestDto(List.of(
                new WorkoutSubmitRequestDto.WorkoutExerciseDto("chest", "X", null, BigDecimal.ONE, 1, null)
        ));

        when(workoutFactory.createWorkout()).thenReturn(new Workout());
        when(workoutRepository.findByWorkoutDateAndUser_Id(today, USER_ID)).thenReturn(Optional.empty());
        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> {
            Workout w = invocation.getArgument(0);
            w.setId(123L);
            return w;
        });

        workoutService.createWorkout(USER_ID, request);

        verify(workoutSetRepository, times(1)).saveAll(anyList());
        verify(workoutRepository, never()).findFirstByUser_IdAndWorkoutDateLessThanOrderByWorkoutDateDesc(any(), any());
    }

    private static Exercise ex(long id, String bodyPart, String name) {
        Exercise e = new Exercise();
        e.setId(id);
        e.setBodyPart(bodyPart);
        e.setName(name);
        e.setSortOrder(0);
        return e;
    }
}
