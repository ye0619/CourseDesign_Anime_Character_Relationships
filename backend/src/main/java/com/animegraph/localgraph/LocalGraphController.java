package com.animegraph.localgraph;

import com.animegraph.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/explore/anime/{id}/local-graph")
public class LocalGraphController {
    private final LocalGraphService service;
    public LocalGraphController(LocalGraphService service){this.service=service;}
    @GetMapping public Object read(@PathVariable long id){return ApiResponse.ok(service.read(id));}
    @PutMapping public Object save(@PathVariable long id,@Valid @RequestBody LocalGraphModels.Document document){return ApiResponse.ok(service.save(id,document));}
    @GetMapping("/backups") public Object backups(@PathVariable long id){return ApiResponse.ok(service.backups(id));}
    @GetMapping("/backups/{backup}") public Object backup(@PathVariable long id,@PathVariable String backup){return ApiResponse.ok(service.backup(id,backup));}
}
