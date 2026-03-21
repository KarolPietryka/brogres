package com.dryrun.brogres.repo;

import com.dryrun.brogres.data.Workout;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    boolean existsByWorkoutDate(LocalDate workoutDate);

    @EntityGraph(attributePaths = "sets")
    List<Workout> findAllByOrderByWorkoutDateDesc();
}
