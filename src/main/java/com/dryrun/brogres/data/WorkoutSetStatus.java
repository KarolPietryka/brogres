package com.dryrun.brogres.data;

/**
 * Per-set status in a workout: planned (not performed yet) vs completed.
 * The old {@code NEXT} marker is gone — "next up" is now derived on the FE from the progress-bar position.
 */
public enum WorkoutSetStatus {
    PLANNED,
    DONE
}
