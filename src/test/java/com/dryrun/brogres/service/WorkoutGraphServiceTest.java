package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutResponseDtos.GraphVolumePointDto;
import com.dryrun.brogres.data.WorkoutSet;
import com.dryrun.brogres.data.WorkoutSetStatus;
import com.dryrun.brogres.repo.WorkoutRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutGraphServiceTest {

    @Mock
    WorkoutRepository workoutRepository;

    @InjectMocks
    WorkoutGraphService workoutGraphService;

    /**
     * When the repository returns no workouts, the graph is empty (no points to plot).
     */
    @Test
    void graphVolumePoints_whenNoWorkouts_returnsEmptyList() {
        when(workoutRepository.findAllByOrderByWorkoutDateAsc()).thenReturn(List.of());

        assertThat(workoutGraphService.graphVolumePoints()).isEmpty();
    }

    /**
     * When every workout has the same dominant body part, the current series starts at the first workout;
     * assert each point’s date and Σ(weight×reps) over DONE sets only.
     */
    @Test
    void graphVolumePoints_whenDominantUnchanged_returnsAllWorkoutsWithVolumeSum() {
        LocalDate d1 = LocalDate.of(2025, 1, 1);
        LocalDate d2 = LocalDate.of(2025, 1, 3);
        Workout w1 = workoutWithSets(
                d1,
                set("chest", 10, "60", WorkoutSetStatus.DONE),
                set("chest", 8, "60", WorkoutSetStatus.DONE));
        Workout w2 = workoutWithSets(
                d2,
                set("chest", 5, "40", WorkoutSetStatus.DONE),
                set("chest", 3, "40", WorkoutSetStatus.DONE),
                set("back", 1, "20", WorkoutSetStatus.DONE));
        when(workoutRepository.findAllByOrderByWorkoutDateAsc()).thenReturn(List.of(w1, w2));

        List<GraphVolumePointDto> points = workoutGraphService.graphVolumePoints();

        assertThat(points).hasSize(2);
        assertThat(points.get(0).workoutDay()).isEqualTo(d1);
        assertThat(points.get(0).volume()).isEqualByComparingTo(new BigDecimal("1080"));
        assertThat(points.get(1).workoutDay()).isEqualTo(d2);
        assertThat(points.get(1).volume()).isEqualByComparingTo(new BigDecimal("340"));
    }

    /**
     * When the dominant part changes (chest → legs), assert the slice is only from the first legs day onward
     * (last series boundary in chronological order).
     */
    @Test
    void graphVolumePoints_whenDominantChanges_keepsOnlyCurrentSeriesTail() {
        LocalDate chestDay = LocalDate.of(2025, 2, 1);
        LocalDate legsDay = LocalDate.of(2025, 2, 5);
        Workout chestFocus = workoutWithSets(chestDay, set("chest", 10, "50", WorkoutSetStatus.DONE));
        Workout legsFocus = workoutWithSets(
                legsDay,
                set("legs", 8, "100", WorkoutSetStatus.DONE),
                set("chest", 2, "30", WorkoutSetStatus.DONE));
        when(workoutRepository.findAllByOrderByWorkoutDateAsc()).thenReturn(List.of(chestFocus, legsFocus));

        List<GraphVolumePointDto> points = workoutGraphService.graphVolumePoints();

        assertThat(points).hasSize(1);
        assertThat(points.get(0).workoutDay()).isEqualTo(legsDay);
        assertThat(points.get(0).volume()).isEqualByComparingTo(new BigDecimal("860"));
    }

    /**
     * When two body parts tie on set count, assert the lexicographically smaller name wins as dominant
     * (stable tie-break for series detection).
     */
    @Test
    void dominantBodyPart_whenTie_prefersLexicographicallySmallerPart() {
        List<WorkoutSet> sets = List.of(
                set("zebra", 1, "10", WorkoutSetStatus.DONE),
                set("arms", 1, "10", WorkoutSetStatus.DONE));

        assertThat(WorkoutGraphService.dominantBodyPart(sets)).isEqualTo("arms");
    }

    /**
     * When a set is PLANNED, assert it does not contribute to volume (only performed DONE volume counts).
     */
    @Test
    void volumeDone_whenPlannedPresent_ignoresNonDoneSets() {
        List<WorkoutSet> sets = List.of(
                set("chest", 100, "999", WorkoutSetStatus.PLANNED),
                set("chest", 2, "25", WorkoutSetStatus.DONE));

        assertThat(WorkoutGraphService.volumeDone(sets)).isEqualByComparingTo(new BigDecimal("50"));
    }

    private static Workout workoutWithSets(LocalDate date, WorkoutSet... sets) {
        Workout w = new Workout();
        w.setWorkoutDate(date);
        List<WorkoutSet> list = new ArrayList<>();
        for (WorkoutSet s : sets) {
            s.setWorkout(w);
            list.add(s);
        }
        w.setSets(list);
        return w;
    }

    private static WorkoutSet set(String bodyPart, int reps, String weight, WorkoutSetStatus status) {
        WorkoutSet ws = new WorkoutSet();
        ws.setBodyPart(bodyPart);
        ws.setRepetitions(reps);
        ws.setWeight(new BigDecimal(weight));
        ws.setStatus(status);
        ws.setExercise("x");
        ws.setLineOrder(0);
        return ws;
    }
}
