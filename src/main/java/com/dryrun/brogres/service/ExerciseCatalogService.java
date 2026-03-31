package com.dryrun.brogres.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExerciseCatalogService {

    private static final List<String> GROUP_ORDER = List.of("Chest", "Back", "Legs", "Arms", "Shoulders");

    private static final Map<String, List<String>> BY_GROUP = Map.of(
            "Chest", List.of("Butterfly", "Incline Bench Press", "Bench Press", "Dip"),
            "Back", List.of("Pull Up", "Barbell Row", "Machine Row"),
            "Legs", List.of("Squat", "Machine Squat"),
            "Arms", List.of("Chin Up", "Dumbbell Curl", "Triceps Pushdown"),
            "Shoulders", List.of("Barbell Overhead Press", "Machine Overhead Press", "Dumbbell Overhead Press", "Lateral Raise")
    );

    public Map<String, List<String>> exercisesByDisplayGroup() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String g : GROUP_ORDER) {
            out.put(g, BY_GROUP.getOrDefault(g, List.of()));
        }
        return out;
    }
}
