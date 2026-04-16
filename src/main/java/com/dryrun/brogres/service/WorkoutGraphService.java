package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.model.WorkoutResponseDtos.GraphVolumePointDto;
import com.dryrun.brogres.data.WorkoutSet;
import com.dryrun.brogres.data.WorkoutSetStatus;
import com.dryrun.brogres.repo.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkoutGraphService {

    private final WorkoutRepository workoutRepository;

    /**
     * Builds volume-over-time points for the <strong>current</strong> specialization slice: workouts from the last
     * time the dominant {@code bodyPart} (by set count) changed through today, ascending by date.
     * <p>
     * Volume = sum of {@code weight * repetitions} over {@link WorkoutSetStatus#DONE} sets only. {@code null} weight
     * is treated as zero.
     */
    @Transactional(readOnly = true)
    public List<GraphVolumePointDto> graphVolumePoints(Long userId) {
        List<Workout> chronological = workoutRepository.findAllByUser_IdOrderByWorkoutDateAsc(userId);
        if (chronological.isEmpty()) {
            return List.of();
        }

        int n = chronological.size();
        String[] effectiveDominant = new String[n];

        // "Dominant" only makes sense when the workout has any sets.
        // If we cannot compute it for a given day (dominantBodyPart returns null),
        // we "inherit" the last known dominant from the previous workout.
        // This way missing data does not break the specialization series.
        String prevDominant = "";
        for (int i = 0; i < n; i++) {
            Workout w = chronological.get(i);
            String raw = dominantBodyPart(w.getSets());
            if (raw == null) {
                effectiveDominant[i] = prevDominant;
            } else {
                effectiveDominant[i] = raw;
                prevDominant = raw;
            }
        }

        // Find the START of the LAST continuous run of the same dominant.
        // lastSeriesStart is updated every time the dominant changes,
        // so after the loop it points to the first workout index in the "current focus".
        int lastSeriesStart = 0;
        for (int i = 1; i < n; i++) {
            if (!Objects.equals(effectiveDominant[i], effectiveDominant[i - 1])) {
                lastSeriesStart = i;
            }
        }

        // Build graph points only for the current run (from lastSeriesStart to the end),
        // i.e. "from the last dominant change until today".
        List<GraphVolumePointDto> out = new ArrayList<>();
        for (int i = lastSeriesStart; i < n; i++) {
            Workout w = chronological.get(i);
            out.add(new GraphVolumePointDto(w.getWorkoutDate(), volumeDone(w.getSets())));
        }
        String dominant = n > 0 ? effectiveDominant[n - 1] : "none";
        log.info("Graph served: points={}, dominantBodyPart={}", out.size(), dominant);
        return out;
    }

    static String dominantBodyPart(List<WorkoutSet> sets) {
        if (sets == null || sets.isEmpty()) {
            return null;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (WorkoutSet s : sets) {
            counts.merge(s.getBodyPart(), 1, Integer::sum);
        }

        // Pick the "best" bodyPart:
        // 1) higher set count wins,
        // 2) on a tie, pick the lexicographically smallest name (deterministic result).
        // This matters so the graph does not "jump" randomly when counts are equal.
        String bestPart = null;
        int bestCount = -1;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String part = e.getKey();
            int c = e.getValue();
            if (bestPart == null || c > bestCount || (c == bestCount && part.compareTo(bestPart) < 0)) {
                bestCount = c;
                bestPart = part;
            }
        }
        return bestPart;
    }

    static BigDecimal volumeDone(List<WorkoutSet> sets) {
        if (sets == null || sets.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (WorkoutSet s : sets) {
            // For the graph we only count sets that were actually executed (DONE).
            if (s.getStatus() != WorkoutSetStatus.DONE) {
                continue;
            }
            int reps = s.getRepetitions();

            // Guard against "garbage" data: a set without a sensible rep count adds no volume.
            if (reps <= 0) {
                continue;
            }

            // Treat null weight as 0 (e.g. bodyweight was not entered),
            // to avoid calculation errors and keep the BigDecimal type consistent.
            BigDecimal w = s.getWeight() != null ? s.getWeight() : BigDecimal.ZERO;
            sum = sum.add(w.multiply(BigDecimal.valueOf(reps)));
        }
        return sum;
    }
}