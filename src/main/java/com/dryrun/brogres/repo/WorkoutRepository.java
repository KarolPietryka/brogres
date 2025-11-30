package com.dryrun.brogres.repo;

import com.dryrun.brogres.data.Workout;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {
}
