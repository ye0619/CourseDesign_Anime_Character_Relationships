package com.animegraph.explore;

import com.animegraph.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/explore/anime/{id}/metadata")
public class SubjectSnapshotController {
    private final SubjectSnapshotService service;
    public SubjectSnapshotController(SubjectSnapshotService service){this.service=service;}
    @GetMapping public ApiResponse<?> read(@PathVariable long id){return ApiResponse.ok(service.read(id));}
    @PostMapping("/jobs") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public ApiResponse<?> start(@PathVariable long id,@RequestParam(defaultValue="false")boolean refresh){return ApiResponse.ok(service.start(id,refresh));}
}
