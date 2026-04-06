package com.dryrun.brogres.repo;

import com.dryrun.brogres.data.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    List<Exercise> findAllByUserIsNull();

    Optional<Exercise> findByUser_IdAndBodyPartAndName(long userId, String bodyPart, String name);

    Optional<Exercise> findByUserIsNullAndBodyPartAndName(String bodyPart, String name);

    List<Exercise> findAllByUser_IdAndBodyPartOrderByNameAsc(long userId, String bodyPart);
}
