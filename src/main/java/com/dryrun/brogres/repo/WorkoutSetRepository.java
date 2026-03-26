package com.dryrun.brogres.repo;

import com.dryrun.brogres.data.WorkoutSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, Long> {

    @Query("SELECT COALESCE(MAX(w.lineOrder), -1) FROM WorkoutSet w WHERE w.workout.id = :workoutId")
    int findMaxLineOrderIndex(@Param("workoutId") Long workoutId);
}
