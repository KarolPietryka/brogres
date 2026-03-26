package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutExerciseViewDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutPrefillDto;
import com.dryrun.brogres.data.WorkoutSet;
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

    /**
     * First workout of the day: creates a new {@link Workout}, persists it, saves all {@link WorkoutSet} rows from the DTO
     * in order, and returns the saved workout.
     */
    @Test
    void createWorkout_whenDtoProvided_savesWorkoutAndSetsWithExpectedValues() {
        /*
         JSON matching the DTO under test:

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
        // Factory supplies a fresh entity because we are creating the first workout of the day.
        when(workoutFactory.createWorkout()).thenReturn(factoryWorkout);

        // No row for today yet, so the service takes the "new Workout" branch.
        when(workoutRepository.findByWorkoutDate(today)).thenReturn(Optional.empty());

        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> {
            Workout w = invocation.getArgument(0);
            // Mimic DB-generated id (identity column).
            w.setId(123L);
            return w;
        });

        Workout result = workoutService.createWorkout(request);

        // Service always checks whether a workout for today already exists.
        verify(workoutRepository).findByWorkoutDate(today);

        // New workout row must be persisted before sets are written.
        verify(workoutRepository).save(workoutCaptor.capture());
        Workout savedWorkout = workoutCaptor.getValue();
        // Saved workout is stamped with today's date.
        assertThat(savedWorkout.getWorkoutDate()).isEqualTo(today);
        // Id comes back from persistence (mocked identity column).
        assertThat(savedWorkout.getId()).isEqualTo(123L);

        // All sets from the DTO are flushed in one batch, linked to the saved workout.
        verify(workoutSetRepository).saveAll(setsCaptor.capture());
        List<WorkoutSet> savedWorkoutSets = setsCaptor.getValue();

        // DTO expands to three WorkoutSet rows (two chest lines + one back).
        assertThat(savedWorkoutSets).hasSize(3);
        // Every row points at the saved workout and carries minimal valid exercise data.
        assertThat(savedWorkoutSets).allSatisfy(s -> {
            // Foreign key: each set belongs to the workout we just saved.
            assertThat(s.getWorkout()).isSameAs(savedWorkout);
            // Exercise name and reps are always populated for this payload.
            assertThat(s.getExercise()).isNotBlank();
            assertThat(s.getRepetitions()).isPositive();
        });

        // First row: chest, first bench series.
        // Exercise name from the first chest entry in the DTO.
        assertThat(savedWorkoutSets.get(0).getExercise()).isEqualTo("Bench Press");
        assertThat(savedWorkoutSets.get(0).getBodyPart()).isEqualTo("chest");
        // 60 kg for the first ramp step.
        assertThat(savedWorkoutSets.get(0).getWeight()).isEqualByComparingTo(new BigDecimal("60.0"));
        // Eight reps on the lighter step.
        assertThat(savedWorkoutSets.get(0).getRepetitions()).isEqualTo(8);

        // Second row: same body part, second bench series (heavier ramp).
        // Second line under the same chest block in the DTO.
        assertThat(savedWorkoutSets.get(1).getExercise()).isEqualTo("Bench Press");
        assertThat(savedWorkoutSets.get(1).getBodyPart()).isEqualTo("chest");
        // 65 kg for the second ramp step.
        assertThat(savedWorkoutSets.get(1).getWeight()).isEqualByComparingTo(new BigDecimal("65.0"));
        // Six reps on the heavier step.
        assertThat(savedWorkoutSets.get(1).getRepetitions()).isEqualTo(6);

        // Third row: back, body-weight pull-ups.
        // First exercise under the back bodyPart in the DTO.
        assertThat(savedWorkoutSets.get(2).getExercise()).isEqualTo("Pull-ups");
        assertThat(savedWorkoutSets.get(2).getBodyPart()).isEqualTo("back");
        // Bodyweight: no bar load stored.
        assertThat(savedWorkoutSets.get(2).getWeight()).isNull();
        assertThat(savedWorkoutSets.get(2).getRepetitions()).isEqualTo(10);

        assertThat(savedWorkoutSets.get(0).getLineOrder()).isZero();
        assertThat(savedWorkoutSets.get(1).getLineOrder()).isEqualTo(1);
        assertThat(savedWorkoutSets.get(2).getLineOrder()).isEqualTo(2);

        // API returns the same workout instance that was saved.
        assertThat(result).isSameAs(savedWorkout);

        // No other repository or factory calls beyond what this scenario requires.
        verifyNoMoreInteractions(workoutRepository, workoutSetRepository, workoutFactory);
    }

    /**
     * Workout for today already exists: append new sets only—no factory, no second {@code save(Workout)}—and return the
     * existing workout instance.
     */
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

        // A session for today already exists: only append sets, no new Workout row.
        when(workoutRepository.findByWorkoutDate(today)).thenReturn(Optional.of(existing));
        when(workoutSetRepository.findMaxLineOrderIndex(99L)).thenReturn(-1);

        Workout result = workoutService.createWorkout(request);

        // Service still looks up today's workout to decide between create vs append.
        verify(workoutRepository).findByWorkoutDate(today);
        verify(workoutSetRepository).findMaxLineOrderIndex(99L);
        // Existing row means the factory must not run.
        verifyNoInteractions(workoutFactory);
        // Workout entity is not inserted again; only new sets are added.
        verify(workoutRepository, never()).save(any(Workout.class));

        // Exactly the submitted sets are persisted against the existing workout.
        verify(workoutSetRepository).saveAll(setsCaptor.capture());
        List<WorkoutSet> saved = setsCaptor.getValue();
        // Request contained a single exercise → one new WorkoutSet.
        assertThat(saved).hasSize(1);
        WorkoutSet persisted = saved.get(0);
        // Set is attached to the pre-existing workout, not a new parent row.
        assertThat(persisted.getWorkout()).isSameAs(existing);
        // Body part string copied from the request bodyPartName.
        assertThat(persisted.getBodyPart()).isEqualTo("chest");
        // Same exercise name as in the submitted DTO.
        assertThat(persisted.getExercise()).isEqualTo("Bench Press");
        // Matches the submitted DTO weight.
        assertThat(persisted.getWeight()).isEqualByComparingTo(new BigDecimal("60.0"));
        // Reps match the single exercise line in the request.
        assertThat(persisted.getRepetitions()).isEqualTo(8);
        assertThat(persisted.getLineOrder()).isZero();
        // Caller gets back the existing workout reference unchanged.
        assertThat(result).isSameAs(existing);

        // No extra repository or factory calls beyond the append-sets path.
        verifyNoMoreInteractions(workoutRepository, workoutSetRepository, workoutFactory);
    }

    /**
     * Prefill when a workout is already stored for today: empty {@code bodyPart} and no call to load the latest workout
     * before today (avoids duplicating a template while the user continues the same session).
     */
    @Test
    void prefillWorkout_whenWorkoutAlreadyExistsForToday_returnsEmptyBodyPartListAndSkipsHistoryLookup() {
        LocalDate today = LocalDate.now();
        // Workout already stored for today: empty prefill, no history query.
        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(true);

        WorkoutPrefillDto result = workoutService.prefillWorkout();

        // No template to offer when today's session already exists.
        assertThat(result.bodyPart()).isEmpty();

        // Prefill path must still ask whether today exists.
        verify(workoutRepository).existsByWorkoutDate(today);
        // Early exit: do not load "latest workout before today" when today is already filled.
        verify(workoutRepository, never()).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(any());
        // Prefill does not touch workout creation or set persistence.
        verifyNoInteractions(workoutFactory, workoutSetRepository);
        // Repository was only used for existsByWorkoutDate.
        verifyNoMoreInteractions(workoutRepository);
    }

    /**
     * Prefill when there is no workout today but history exists: map the latest workout with {@code workoutDate} strictly
     * before today into {@code bodyPart} groups, preserving set order by {@code lineOrder}.
     */
    @Test
    void prefillWorkout_whenNoWorkoutTodayButEarlierWorkoutExists_mapsSetsToBodyPartStructure() {
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

        WorkoutSet s2 = new WorkoutSet();
        s2.setId(11L);
        s2.setWorkout(previous);
        s2.setBodyPart("chest");
        s2.setExercise("Bench Press");
        s2.setWeight(new BigDecimal("65.0"));
        s2.setRepetitions(6);
        s2.setLineOrder(1);

        WorkoutSet s3 = new WorkoutSet();
        s3.setId(12L);
        s3.setWorkout(previous);
        s3.setBodyPart("back");
        s3.setExercise("Pull-ups");
        s3.setWeight(null);
        s3.setRepetitions(10);
        s3.setLineOrder(2);

        previous.getSets().addAll(List.of(s3, s1, s2));

        // Nothing for today: service will look up the latest session before today.
        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(false);
        // Latest workout with date strictly before today (here: yesterday), including its sets.
        when(workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today))
                .thenReturn(Optional.of(previous));

        WorkoutPrefillDto result = workoutService.prefillWorkout();

        // Grouped view: two body-part buckets (chest then back) from three sets.
        assertThat(result.bodyPart()).hasSize(2);

        // First group label matches the first contiguous bodyPart in the saved sets.
        assertThat(result.bodyPart().get(0).bodyPartName()).isEqualTo("chest");
        // Two bench rows preserved in WorkoutSet id order (60/8 then 65/6).
        assertThat(result.bodyPart().get(0).exercises()).containsExactly(
                new WorkoutExerciseViewDto("Bench Press", 0, new BigDecimal("60.0"), 8),
                new WorkoutExerciseViewDto("Bench Press", 1, new BigDecimal("65.0"), 6)
        );

        // Second group switches to back after the chest block ends.
        assertThat(result.bodyPart().get(1).bodyPartName()).isEqualTo("back");
        // Single pull-up line with null weight.
        assertThat(result.bodyPart().get(1).exercises()).containsExactly(
                new WorkoutExerciseViewDto("Pull-ups", 2, null, 10)
        );

        // No row for today, so we check exists then load the latest session before today.
        verify(workoutRepository).existsByWorkoutDate(today);
        verify(workoutRepository).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today);
        // Read-only prefill: no factory or set writes.
        verifyNoInteractions(workoutFactory, workoutSetRepository);
        // Repository was only used for the two lookups above.
        verifyNoMoreInteractions(workoutRepository);
    }

    /**
     * Prefill cold start (or no row older than today): {@code exists} is false and {@code findFirst...LessThan} returns
     * empty—result is an empty {@code bodyPart} list.
     */
    @Test
    void prefillWorkout_whenNoWorkoutTodayAndRepositoryFindsNoEarlierWorkout_returnsEmptyBodyPartList() {
        LocalDate today = LocalDate.now();
        // No workout today (e.g. cold start).
        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(false);
        // No older session in the database either: nothing to clone.
        when(workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today))
                .thenReturn(Optional.empty());

        WorkoutPrefillDto result = workoutService.prefillWorkout();

        // Nothing to prefill when there is no prior session to clone.
        assertThat(result.bodyPart()).isEmpty();

        // Both checks run: no today, then lookup for any older workout (returns empty).
        verify(workoutRepository).existsByWorkoutDate(today);
        verify(workoutRepository).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today);
        // Read-only prefill: no factory or set writes.
        verifyNoInteractions(workoutFactory, workoutSetRepository);
        // Repository was only used for exists + findFirst.
        verifyNoMoreInteractions(workoutRepository);
    }

    /**
     * Edge case: {@code findFirst...LessThan(today)} returns a {@link Workout} with an empty {@code sets} collection.
     * Prefill is built only from sets, so the response is still {@code bodyPart: []}—same as cold start from the FE’s
     * perspective, but the service did run both repository calls (unlike {@link #prefillWorkout_whenNoWorkoutTodayAndRepositoryFindsNoEarlierWorkout_returnsEmptyBodyPartList}
     * where the second call returns {@link Optional#empty()}). Covers inconsistent data or a future “empty session” row.
     */
    @Test
    void prefillWorkout_whenEarlierWorkoutHasNoSets_returnsEmptyBodyPartList() {
        LocalDate today = LocalDate.now();

        // Workout row exists for a past date, but no child sets (orphan / incomplete session).
        Workout shell = new Workout();
        shell.setId(1L);
        shell.setWorkoutDate(today.minusDays(3));

        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(false);
        // Repo returns a "latest before today" workout, but it has no sets — mapping yields an empty list.
        when(workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today))
                .thenReturn(Optional.of(shell));

        WorkoutPrefillDto result = workoutService.prefillWorkout();

        // Mapping yields no exercises when the shell workout has an empty sets collection.
        assertThat(result.bodyPart()).isEmpty();

        // Same two repository calls as a successful prefill, even when mapped bodyPart is empty.
        verify(workoutRepository).existsByWorkoutDate(today);
        verify(workoutRepository).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today);
        // Read-only prefill: no factory or set writes.
        verifyNoInteractions(workoutFactory, workoutSetRepository);
        // Repository was only used for exists + findFirst.
        verifyNoMoreInteractions(workoutRepository);
    }
}