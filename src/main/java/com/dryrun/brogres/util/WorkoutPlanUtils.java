package com.dryrun.brogres.util;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helpers for the recent-plan-template carousel: plan signatures and grouping past sessions by signature.
 */
public final class WorkoutPlanUtils {

    private WorkoutPlanUtils() {
    }

    /**
     * Plan signature: every persisted set row in session order ({@code lineOrder}, tie-break {@code id}),
     * both {@code DONE} and {@code PLANNED} — only exercise ids, no weights/reps.
     * Empty string when the workout has no sets (caller skips it for the carousel).
     */
    public static String calcSignature(Workout workout) {
        return workout.getSets().stream()
                .sorted(Comparator.comparingInt(WorkoutSet::getLineOrder).thenComparing(WorkoutSet::getId))
                .map(s -> s.getExercise().getId().toString())
                .collect(Collectors.joining(","));
    }

    /**
     * Groups past workouts by {@link #calcSignature(Workout)}: one representative per distinct signature
     * (the latest session for that key — caller must pass workouts ordered newest-first).
     * Then sorts representatives by {@link Workout#getWorkoutDate()} descending so the response order is explicit.
     */
    public static List<Workout> latestWorkoutPerPlanKeyDescending(List<Workout> pastNewestFirst) {
        // putIfAbsent keeps the first (newest) workout for each planKey when scanning newest-first.
        Map<String, Workout> latestWorkoutByPlanKey = new LinkedHashMap<>();
        for (Workout w : pastNewestFirst) {
            String planKey = calcSignature(w);
            if (planKey.isEmpty()) {
                continue;
            }
            latestWorkoutByPlanKey.putIfAbsent(planKey, w);
        }

        List<Workout> ordered = new ArrayList<>(latestWorkoutByPlanKey.values());
        ordered.sort(Comparator.comparing(Workout::getWorkoutDate).reversed());
        return ordered;
    }
}
