package com.dryrun.brogres.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Data
@EntityListeners(AuditingEntityListener.class)
public class Workout extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private LocalDate workoutDate;

    /** Lazy; excluded from JSON and Lombok {@code toString} so POST/PUT can return the entity after the TX ends (no session). */
    @JsonIgnore
    @ToString.Exclude
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "workout", fetch = FetchType.LAZY)
    private List<WorkoutSet> sets = new ArrayList<>();
}
