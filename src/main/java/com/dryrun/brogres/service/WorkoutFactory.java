package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Workout;
import org.springframework.stereotype.Component;

@Component
public class WorkoutFactory {
    public Workout createWorkout() {
        return new Workout();
    }
}
