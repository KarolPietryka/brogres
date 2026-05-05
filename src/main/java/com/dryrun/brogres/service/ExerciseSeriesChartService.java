package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Exercise;
import com.dryrun.brogres.data.WorkoutSetStatus;
import com.dryrun.brogres.model.ExerciseSeriesChartDtos.ExerciseSeriesPointDto;
import com.dryrun.brogres.model.ExerciseSeriesChartDtos.ExerciseSeriesRequest;
import com.dryrun.brogres.repo.ExerciseRepository;
import com.dryrun.brogres.repo.WorkoutSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExerciseSeriesChartService {

    private final ExerciseRepository exerciseRepository;
    private final WorkoutSetRepository workoutSetRepository;

    /**
     * Validates optional inclusive bounds (spec: min > max → 400, never swap).
     */
    private static void validateFilterRanges(ExerciseSeriesRequest request) {
        if (request.repMin() != null && request.repMax() != null && request.repMin() > request.repMax()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "repMin must be <= repMax");
        }
        if (request.weightMin() != null
                && request.weightMax() != null
                && request.weightMin().compareTo(request.weightMax()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weightMin must be <= weightMax");
        }
    }

    /**
     * Loads exercise for chart; 404 when missing or not visible to this user (catalog or own only).
     */
    private static void requireExerciseForUser(long userId, Exercise exercise) {
        if (exercise.getUser() != null && !exercise.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found");
        }
    }

    @Transactional(readOnly = true)
    public List<ExerciseSeriesPointDto> seriesPoints(long userId, ExerciseSeriesRequest request) {
        validateFilterRanges(request);

        Exercise exercise = exerciseRepository
                .findById(request.exerciseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found"));
        requireExerciseForUser(userId, exercise);

        List<Object[]> rows = workoutSetRepository.aggregateExerciseSeriesByDay(
                userId,
                request.exerciseId(),
                WorkoutSetStatus.DONE,
                request.fromDate(),
                request.toDate(),
                request.repMin(),
                request.repMax(),
                request.weightMin(),
                request.weightMax());

        return rows.stream()
                .map(ExerciseSeriesChartService::toPoint)
                .toList();
    }

    private static ExerciseSeriesPointDto toPoint(Object[] row) {
        LocalDate day = (LocalDate) row[0];
        BigDecimal totalWeight = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
        long totalReps = row[2] == null ? 0L : ((Number) row[2]).longValue();
        return new ExerciseSeriesPointDto(day, totalWeight, totalReps);
    }
}
