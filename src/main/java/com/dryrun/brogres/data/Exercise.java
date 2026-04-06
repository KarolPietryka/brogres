package com.dryrun.brogres.data;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Exercise definition: either built-in catalog ({@code user == null}) or user-owned ({@code user != null}).
 * A workout links to an exercise through {@link WorkoutSet} (and to {@link Workout} through the same row) —
 * there is no direct {@code Exercise → Workout} FK.
 */
@Entity
@Table(name = "exercise")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** When {@code null}, this row is part of the global catalog. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private AppUser user;

    @Column(name = "body_part", nullable = false, length = 64)
    private String bodyPart;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
