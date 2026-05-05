package com.dryrun.brogres.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Response rows for {@code GET /brogres/graph} (volume per workout day). */
public final class VolumeGraphDtos {

    private VolumeGraphDtos() {
    }

    public record VolumePointDto(LocalDate workoutDay, BigDecimal volume) {
    }
}
