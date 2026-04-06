package com.dryrun.brogres.service;

import com.dryrun.brogres.data.AppUser;
import com.dryrun.brogres.data.Exercise;
import com.dryrun.brogres.model.ExerciseDtos.CreateExerciseRequest;
import com.dryrun.brogres.model.ExerciseDtos.ExercisePickerDto;
import com.dryrun.brogres.model.ExerciseDtos.ExerciseRefDto;
import com.dryrun.brogres.repo.AppUserRepository;
import com.dryrun.brogres.repo.ExerciseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExercisePickerService {

    private static final Set<String> ALLOWED_BODY_PARTS =
            Set.of("chest", "back", "legs", "arms", "shoulders");

    private final ExerciseRepository exerciseRepository;
    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public ExercisePickerDto pickerForBodyPart(long userId, String bodyPart) {
        // Guardrail: keep API restricted to known body parts.
        requireAllowedBodyPart(bodyPart);

        // Catalog (user == null): global defaults for this body part.
        List<ExerciseRefDto> catalog = exerciseRepository.findAllByUserIsNull().stream()
                .filter(e -> bodyPart.equals(e.getBodyPart()))
                .sorted(Comparator.comparingInt(Exercise::getSortOrder)) // sortOrder is a UI hint
                .map(e -> new ExerciseRefDto(e.getId(), e.getName()))
                .toList();

        // Custom (user == userId): user-owned labels for the same body part.
        List<ExerciseRefDto> custom = exerciseRepository.findAllByUser_IdAndBodyPartOrderByNameAsc(userId, bodyPart)
                .stream()
                .map(e -> new ExerciseRefDto(e.getId(), e.getName()))
                .toList();

        // UI consumes two buckets to render "defaults + my exercises".
        return new ExercisePickerDto(catalog, custom);
    }

    @Transactional
    public ExerciseRefDto createUserExercise(long userId, CreateExerciseRequest request) {

        // Normalize input (stable matching + uniqueness)
        String bodyPart = request.bodyPart().trim();
        requireAllowedBodyPart(bodyPart);

        // Validate name (BE safety net)
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is blank");
        }

        // Prevent duplicates per user + bodyPart
        if (exerciseRepository.findByUser_IdAndBodyPartAndName(userId, bodyPart, name).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exercise already exists for this body part");
        }

        // Lightweight FK link (no full user fetch needed)
        AppUser owner = appUserRepository.getReferenceById(userId);

        // Build "custom" exercise (user != null)
        Exercise e = new Exercise();
        e.setUser(owner);
        e.setBodyPart(bodyPart);
        e.setName(name);
        // UI ordering hint only (not unique; duplicates are fine).
        e.setSortOrder(0);

        // Return id so FE can store exerciseId immediately
        Exercise saved = exerciseRepository.save(e);
        return new ExerciseRefDto(saved.getId(), saved.getName());
    }

    private static void requireAllowedBodyPart(String bodyPart) {
        if (!ALLOWED_BODY_PARTS.contains(bodyPart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown body part");
        }
    }
}
