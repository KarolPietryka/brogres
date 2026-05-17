package com.dryrun.brogres.model;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /workout — summaries plus the server’s calendar “today” and whether a workout row exists for that date
 * (carousel / “Start new”; the client must not infer this from the browser clock).
 */
public record WorkoutListEnvelopeDto(
        List<WorkoutResponseDtos.WorkoutSummaryDto> workouts,
        LocalDate serverToday,
        boolean hasWorkoutForToday) {
}
