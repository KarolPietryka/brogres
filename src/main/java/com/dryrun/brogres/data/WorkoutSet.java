package com.dryrun.brogres.data;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;

@Entity
@Table(name = "workout_set")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
public class WorkoutSet {

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

    /** Global line index within the workout (0, 1, …), set from request order when saving. */
    @Column(name = "line_order", nullable = false)
    private int lineOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private WorkoutSetStatus status = WorkoutSetStatus.DONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_id", nullable = false)
    private Workout workout;
}
