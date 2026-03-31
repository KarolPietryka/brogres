package com.dryrun.brogres.controller;

import com.dryrun.brogres.model.WorkoutResponseDtos.GraphVolumePointDto;
import com.dryrun.brogres.security.SecurityUtils;
import com.dryrun.brogres.service.WorkoutGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/brogres")
@RequiredArgsConstructor
public class BrogresGraphController {

    private final WorkoutGraphService workoutGraphService;

    @GetMapping("/graph")
    public List<GraphVolumePointDto> graphVolume() {
        return workoutGraphService.graphVolumePoints(SecurityUtils.requireUserId());
    }
}
