package com.dryrun.brogres.service;

import com.dryrun.brogres.data.WorkoutSetStatus;
import com.dryrun.brogres.model.VolumeGraphDtos.VolumePointDto;
import com.dryrun.brogres.repo.WorkoutSetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VolumeChartServiceTest {

    private static final long USER_ID = 2L;

    @Mock
    WorkoutSetRepository workoutSetRepository;

    @InjectMocks
    VolumeChartService volumeChartService;

    @Test
    void volumeByDayForUser_mapsRowsAndUsesDoneStatus() {
        // When: repository returns one aggregated day (weight×reps sum).
        when(workoutSetRepository.aggregateVolumeByDay(USER_ID, WorkoutSetStatus.DONE))
                .thenReturn(
                        Collections.singletonList(new Object[] {LocalDate.of(2026, 2, 15), new BigDecimal("480.0")}));

        // Assert: DTO carries the same day and volume.
        List<VolumePointDto> out = volumeChartService.volumeByDayForUser(USER_ID);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).workoutDay()).isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(out.get(0).volume()).isEqualByComparingTo("480.0");

        // Verify: only DONE sets contribute to the volume series.
        verify(workoutSetRepository).aggregateVolumeByDay(eq(USER_ID), eq(WorkoutSetStatus.DONE));
    }
}
