package com.dryrun.brogres.controller;

import com.dryrun.brogres.model.VolumeGraphDtos.VolumePointDto;
import com.dryrun.brogres.security.SecurityUtils;
import com.dryrun.brogres.service.VolumeChartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/brogres")
@RequiredArgsConstructor
public class BrogresGraphController {

    private final VolumeChartService volumeChartService;

    /** GET /brogres/graph — training volume (Σ weight×reps) per workout day for the current user. */
    @GetMapping("/graph")
    public List<VolumePointDto> volumeGraph() {
        return volumeChartService.volumeByDayForUser(SecurityUtils.requireUserId());
    }
}
