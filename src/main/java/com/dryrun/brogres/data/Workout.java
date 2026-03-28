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

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "workout", fetch = FetchType.LAZY)
    private List<WorkoutSet> sets = new ArrayList<>();
}
