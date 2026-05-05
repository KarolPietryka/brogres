package com.dryrun.brogres.service;

import com.dryrun.brogres.data.AppUser;
import com.dryrun.brogres.data.Exercise;
import com.dryrun.brogres.data.WorkoutSetStatus;
import com.dryrun.brogres.model.ExerciseSeriesChartDtos.ExerciseSeriesPointDto;
import com.dryrun.brogres.model.ExerciseSeriesChartDtos.ExerciseSeriesRequest;
import com.dryrun.brogres.repo.ExerciseRepository;
import com.dryrun.brogres.repo.WorkoutSetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciseSeriesChartServiceTest {

    private static final long USER_ID = 1L;
    private static final long EXERCISE_ID = 42L;

    @Mock
    ExerciseRepository exerciseRepository;

    @Mock
    WorkoutSetRepository workoutSetRepository;

    @InjectMocks
    ExerciseSeriesChartService exerciseSeriesChartService;

    @Test
    void seriesPoints_whenRepMinGreaterThanRepMax_throwsBadRequest() {
        // When: invalid inclusive rep range — spec forbids silent swap.
        ExerciseSeriesRequest req = new ExerciseSeriesRequest(EXERCISE_ID, null, null, 10, 5, null, null);

        // Assert: service surfaces 400 with explicit message.
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class, () -> exerciseSeriesChartService.seriesPoints(USER_ID, req));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("repMin");
    }

    @Test
    void seriesPoints_whenWeightMinGreaterThanWeightMax_throwsBadRequest() {
        // When: invalid weight range.
        ExerciseSeriesRequest req = new ExerciseSeriesRequest(
                EXERCISE_ID, null, null, null, null, new BigDecimal("100"), new BigDecimal("50"));

        // Assert: 400 before any repository aggregation runs.
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class, () -> exerciseSeriesChartService.seriesPoints(USER_ID, req));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("weightMin");
    }

    @Test
    void seriesPoints_whenExerciseMissing_throwsNotFound() {
        // When: no Exercise row for the requested id — neutral 404 for chart callers.
        when(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.empty());
        ExerciseSeriesRequest req = new ExerciseSeriesRequest(EXERCISE_ID, null, null, null, null, null, null);

        // Assert: aggregation must not run.
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class, () -> exerciseSeriesChartService.seriesPoints(USER_ID, req));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void seriesPoints_whenExerciseOwnedByAnotherUser_throwsNotFound() {
        // When: exercise exists but is private to a different account.
        AppUser other = new AppUser();
        other.setId(99L);
        Exercise foreign = new Exercise();
        foreign.setId(EXERCISE_ID);
        foreign.setUser(other);
        when(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.of(foreign));
        ExerciseSeriesRequest req = new ExerciseSeriesRequest(EXERCISE_ID, null, null, null, null, null, null);

        // Assert: same neutral 404 as missing id (no leak).
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class, () -> exerciseSeriesChartService.seriesPoints(USER_ID, req));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void seriesPoints_whenCatalogExercise_andNoRows_returnsEmptyList() {
        // When: catalog exercise (user == null) is visible; aggregation yields no days.
        Exercise ex = catalogExercise();
        when(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.of(ex));
        when(workoutSetRepository.aggregateExerciseSeriesByDay(
                        eq(USER_ID),
                        eq(EXERCISE_ID),
                        eq(WorkoutSetStatus.DONE),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null)))
                .thenReturn(List.of());
        ExerciseSeriesRequest req = new ExerciseSeriesRequest(EXERCISE_ID, null, null, null, null, null, null);

        // Assert: 200 contract on FE is an empty JSON array, not an error.
        List<ExerciseSeriesPointDto> out = exerciseSeriesChartService.seriesPoints(USER_ID, req);
        assertThat(out).isEmpty();

        // Verify: chart path always aggregates DONE sets only (PLANNED excluded in query).
        verify(workoutSetRepository)
                .aggregateExerciseSeriesByDay(
                        eq(USER_ID),
                        eq(EXERCISE_ID),
                        eq(WorkoutSetStatus.DONE),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null));
    }

    @Test
    void seriesPoints_passesOptionalBoundsToRepository() {
        // When: valid exercise and optional filters — repository receives the same nullable args.
        Exercise ex = catalogExercise();
        when(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.of(ex));
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        when(workoutSetRepository.aggregateExerciseSeriesByDay(
                        USER_ID,
                        EXERCISE_ID,
                        WorkoutSetStatus.DONE,
                        from,
                        to,
                        5,
                        12,
                        new BigDecimal("40"),
                        new BigDecimal("80")))
                .thenReturn(java.util.Collections.singletonList(new Object[] {LocalDate.of(2026, 1, 10), new BigDecimal("120"), 8L}));
        ExerciseSeriesRequest req =
                new ExerciseSeriesRequest(EXERCISE_ID, from, to, 5, 12, new BigDecimal("40"), new BigDecimal("80"));

        // Assert: one mapped point with summed totals from the query row.
        List<ExerciseSeriesPointDto> out = exerciseSeriesChartService.seriesPoints(USER_ID, req);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).workoutDay()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(out.get(0).totalWeight()).isEqualByComparingTo("120");
        assertThat(out.get(0).totalReps()).isEqualTo(8L);

        // Verify: DONE-only path is always used for chart aggregation.
        verify(workoutSetRepository)
                .aggregateExerciseSeriesByDay(
                        eq(USER_ID),
                        eq(EXERCISE_ID),
                        eq(WorkoutSetStatus.DONE),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any());
    }

    private static Exercise catalogExercise() {
        Exercise ex = new Exercise();
        ex.setId(EXERCISE_ID);
        ex.setUser(null);
        return ex;
    }
}
