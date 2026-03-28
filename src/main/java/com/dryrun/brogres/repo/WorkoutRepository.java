package com.dryrun.brogres.repo;

import com.dryrun.brogres.data.Workout;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    @EntityGraph(attributePaths = "sets")
    Optional<Workout> findByWorkoutDate(LocalDate workoutDate);

    boolean existsByWorkoutDate(LocalDate workoutDate);

    @EntityGraph(attributePaths = "sets")
    Optional<Workout> findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(LocalDate date);

    @EntityGraph(attributePaths = "sets")
    List<Workout> findAllByOrderByWorkoutDateDesc();
}
