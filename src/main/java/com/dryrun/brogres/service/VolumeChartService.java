package com.dryrun.brogres.service;

import com.dryrun.brogres.data.WorkoutSetStatus;
import com.dryrun.brogres.model.VolumeGraphDtos.VolumePointDto;
import com.dryrun.brogres.repo.WorkoutSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VolumeChartService {

    private final WorkoutSetRepository workoutSetRepository;

    /**
     * Per workout calendar day: sum of weight × reps over {@link WorkoutSetStatus#DONE} sets only
     * (same notion of “completed volume” as the exercise-series chart).
     */
    @Transactional(readOnly = true)
    public List<VolumePointDto> volumeByDayForUser(long userId) {
        List<Object[]> rows = workoutSetRepository.aggregateVolumeByDay(userId, WorkoutSetStatus.DONE);
        return rows.stream().map(VolumeChartService::toPoint).toList();
    }

    private static VolumePointDto toPoint(Object[] row) {
        LocalDate day = (LocalDate) row[0];
        BigDecimal volume = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
        return new VolumePointDto(day, volume);
    }
}
