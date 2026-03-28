package com.dryrun.brogres.mapper;

import com.dryrun.brogres.data.PlanWorkoutSet;
import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanWorkoutSetMapperTest {

    /**
     * Pure unit tests (no Spring context): MapStruct generates an implementation class,
     * and {@link Mappers#getMapper(Class)} returns an instance of it.
     */
    private final PlanWorkoutSetMapper mapper = Mappers.getMapper(PlanWorkoutSetMapper.class);

    @Test
    @DisplayName("""
            Use case: creating a "plan snapshot" for a new workout from a previous workout set.
            Expectations:
            - null bodyPart in the source is normalized to an empty string (never null in the target),
            - the target must be linked to the provided Workout context (@AfterMapping),
            - the target id must remain "unset" (ignored by the mapper).
            """)
    void fromWorkoutSet_bodyPartNull_normalizesToEmptyString_linksWorkout_andIgnoresId() throws Exception {
        // Given: a mapping context (the workout that will own the snapshot row)
        Workout workoutContext = new Workout();

        // Given: a source set with a missing bodyPart (data edge-case)
        WorkoutSet source = new WorkoutSet();
        source.setBodyPart(null);

        // When: mapping a single set into a plan snapshot row
        PlanWorkoutSet mapped = mapper.fromWorkoutSet(source, workoutContext);

        // Then: mapper returns a new instance
        assertNotNull(mapped, "Mapper should return a new PlanWorkoutSet instance");

        // Then: bodyPart is normalized (null -> "")
        assertEquals("", mapped.getBodyPart(),
                "When WorkoutSet.bodyPart is null, the mapper must set PlanWorkoutSet.bodyPart to an empty string");

        // Then: @AfterMapping links the row to the workout provided via @Context
        assertSame(workoutContext, mapped.getWorkout(),
                "Mapper must link PlanWorkoutSet.workout to the Workout provided via @Context");

        // Then: id is not set by mapping (so persistence can treat it as a new row)
        assertIdIgnored(mapped);
    }

    @Test
    @DisplayName("""
            Use case: creating a "plan snapshot" row when the source bodyPart is present.
            Expectations:
            - bodyPart is copied as-is,
            - the target is linked to the provided Workout context.
            """)
    void fromWorkoutSet_bodyPartPresent_copiesValue_andLinksWorkout() {
        // Given
        Workout workoutContext = new Workout();

        WorkoutSet source = new WorkoutSet();
        source.setBodyPart("Chest");

        // When
        PlanWorkoutSet mapped = mapper.fromWorkoutSet(source, workoutContext);

        // Then
        assertNotNull(mapped, "Mapper should return a new PlanWorkoutSet instance");
        assertEquals("Chest", mapped.getBodyPart(),
                "When WorkoutSet.bodyPart is present, the mapper should copy it as-is");
        assertSame(workoutContext, mapped.getWorkout(),
                "Mapper must link PlanWorkoutSet.workout to the Workout provided via @Context");
    }

    @Test
    @DisplayName("""
            Use case: bulk snapshot mapping of multiple workout sets into plan rows.
            Expectations:
            - the output list size matches the input list size,
            - each mapped row is linked to the same Workout context,
            - bodyPart is normalized per element (null -> "").
            """)
    void fromWorkoutSets_mapsAllElements_linksWorkoutForEach_andNormalizesBodyPart() {
        // Given: two sets - one with bodyPart present, one with bodyPart missing
        Workout workoutContext = new Workout();

        WorkoutSet first = new WorkoutSet();
        first.setBodyPart("Back");

        WorkoutSet second = new WorkoutSet();
        second.setBodyPart(null);

        List<WorkoutSet> sources = List.of(first, second);

        // When: mapping a list
        List<PlanWorkoutSet> mapped = mapper.fromWorkoutSets(sources, workoutContext);

        // Then: list is returned and all items are mapped
        assertNotNull(mapped, "Mapper should return a list (not null)");
        assertEquals(2, mapped.size(), "Mapper should map exactly as many elements as provided in the input list");

        // Then: element 0 - bodyPart copied, workout linked
        assertEquals("Back", mapped.get(0).getBodyPart(), "Element[0]: bodyPart should be copied as-is");
        assertSame(workoutContext, mapped.get(0).getWorkout(), "Element[0]: workout should be linked from @Context");

        // Then: element 1 - bodyPart normalized, workout linked
        assertEquals("", mapped.get(1).getBodyPart(), "Element[1]: null bodyPart should be normalized to empty string");
        assertSame(workoutContext, mapped.get(1).getWorkout(), "Element[1]: workout should be linked from @Context");
    }

    /**
     * The mapper configuration ignores the target "id" field.
     *
     * In practice:
     * - if id is a primitive (long/int) -> it must remain the default value (0 / 0L)
     * - if id is a reference type (Long/Integer/UUID/...) -> it must remain null
     *
     * Reflection is used to avoid coupling this test to a concrete id type.
     */
    private static void assertIdIgnored(PlanWorkoutSet mapped) throws Exception {
        Method getId = mapped.getClass().getMethod("getId");
        Class<?> returnType = getId.getReturnType();
        Object idValue = getId.invoke(mapped);

        if (returnType.equals(long.class)) {
            assertEquals(0L, (long) idValue,
                    "If id is a primitive long, it must remain 0L because the mapper ignores the id field");
        } else if (returnType.equals(int.class)) {
            assertEquals(0, (int) idValue,
                    "If id is a primitive int, it must remain 0 because the mapper ignores the id field");
        } else {
            assertNull(idValue,
                    "If id is a reference type, it must remain null because the mapper ignores the id field");
        }
    }
}
