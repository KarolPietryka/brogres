package com.dryrun.brogres.repo;

import com.dryrun.brogres.data.WorkoutSet;
import com.dryrun.brogres.data.WorkoutSetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, Long> {

    @Query("SELECT COALESCE(MAX(w.lineOrder), -1) FROM WorkoutSet w WHERE w.workout.id = :workoutId")
    int findMaxLineOrderIndex(@Param("workoutId") Long workoutId);

    /** Replace-day submit: remove all sets for this workout before inserting the new snapshot. */
    @Modifying
    @Query("DELETE FROM WorkoutSet w WHERE w.workout.id = :workoutId")
    void deleteAllByWorkoutId(@Param("workoutId") Long workoutId);

    /**
     * Per calendar {@code workout_date}: sums of weight and reps over sets matching filters (spec: only {@code DONE}).
     * Days with no matching rows are omitted.
     */
    @Query("""
            select w.workoutDate, sum(ws.weight), sum(ws.repetitions)
            from WorkoutSet ws join ws.workout w
            where w.user.id = :userId
              and ws.exercise.id = :exerciseId
              and ws.status = :doneStatus
              and (:fromDate is null or w.workoutDate >= :fromDate)
              and (:toDate is null or w.workoutDate <= :toDate)
              and (:repMin is null or ws.repetitions >= :repMin)
              and (:repMax is null or ws.repetitions <= :repMax)
              and (:weightMin is null or ws.weight >= :weightMin)
              and (:weightMax is null or ws.weight <= :weightMax)
            group by w.workoutDate
            order by w.workoutDate
            """)
    List<Object[]> aggregateExerciseSeriesByDay(
            @Param("userId") long userId,
            @Param("exerciseId") long exerciseId,
            @Param("doneStatus") WorkoutSetStatus doneStatus,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("repMin") Integer repMin,
            @Param("repMax") Integer repMax,
            @Param("weightMin") BigDecimal weightMin,
            @Param("weightMax") BigDecimal weightMax);

    /**
     * Per workout day: Σ(weight × repetitions) for {@code DONE} sets (days with no DONE rows omitted).
     */
    @Query("""
            select w.workoutDate, sum(ws.weight * ws.repetitions)
            from WorkoutSet ws join ws.workout w
            where w.user.id = :userId
              and ws.status = :doneStatus
            group by w.workoutDate
            order by w.workoutDate
            """)
    List<Object[]> aggregateVolumeByDay(@Param("userId") long userId, @Param("doneStatus") WorkoutSetStatus doneStatus);
}
