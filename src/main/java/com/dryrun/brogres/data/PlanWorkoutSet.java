package com.dryrun.brogres.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;

/**
 * Snapshot of the previous session (last workout day), stored when the first set of the current day is saved.
 * Same ordering semantics as {@link WorkoutSet} ({@code lineOrder}).
 */
@Entity
@Table(name = "plan_workout_set")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
public class PlanWorkoutSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String exercise;

    @Column(nullable = false)
    private int repetitions;

    @Column(nullable = false)
    private String bodyPart;

    @Column
    private BigDecimal weight;

    @Column(name = "line_order", nullable = false)
    private int lineOrder;

    @JsonIgnore
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_id", nullable = false)
    private Workout workout;
}
