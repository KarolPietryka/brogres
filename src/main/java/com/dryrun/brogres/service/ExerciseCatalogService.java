package com.dryrun.brogres.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExerciseCatalogService {

    private static final List<String> GROUP_ORDER = List.of("Klata", "Plecy", "Nogi", "Ramiona", "Barki");

    private static final Map<String, List<String>> BY_GROUP = Map.of(
            "Klata", List.of("Butterfly", "Incline Bench Press", "Bench Press", "Dip"),
            "Plecy", List.of("Pull Up", "Wiosłowanie ze sztangą", "Wiosłowanie na maszynie"),
            "Nogi", List.of("Squat", "Squat na maszynie"),
            "Ramiona", List.of("Chin Up", "Hantle curl", "Prostowanie ramienia na maszynie"),
            "Barki", List.of("OHP sztanga", "OHP maszyna", "OHP hantle")
    );

    public Map<String, List<String>> exercisesByDisplayGroup() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String g : GROUP_ORDER) {
            out.put(g, BY_GROUP.getOrDefault(g, List.of()));
        }
        return out;
    }
}
