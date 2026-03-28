package com.dryrun.brogres.repo;

import com.dryrun.brogres.data.Workout;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    Optional<Workout> findByWorkoutDate(LocalDate workoutDate);

    boolean existsByWorkoutDate(LocalDate workoutDate);

    @EntityGraph(attributePaths = "sets")
    Optional<Workout> findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(LocalDate date);

    /** Fetches {@code sets} in one query; {@code planWorkoutSets} is lazy + batch-loaded (see {@code Workout}). */
    @EntityGraph(attributePaths = "sets")
    List<Workout> findAllByOrderByWorkoutDateDesc();

    @EntityGraph(attributePaths = "planWorkoutSets")
    Optional<Workout> findWithPlanWorkoutSetsByWorkoutDate(LocalDate workoutDate);
}
