package com.dryrun.brogres.repo;

import com.dryrun.brogres.data.Workout;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    @EntityGraph(attributePaths = "sets")
    Optional<Workout> findByWorkoutDateAndUser_Id(LocalDate workoutDate, Long userId);

    boolean existsByWorkoutDateAndUser_Id(LocalDate workoutDate, Long userId);

    @EntityGraph(attributePaths = "sets")
    Optional<Workout> findFirstByUser_IdAndWorkoutDateLessThanOrderByWorkoutDateDesc(Long userId, LocalDate date);

    @EntityGraph(attributePaths = "sets")
    List<Workout> findAllByUser_IdOrderByWorkoutDateDesc(Long userId);

    @EntityGraph(attributePaths = "sets")
    List<Workout> findAllByUser_IdOrderByWorkoutDateAsc(Long userId);
}
