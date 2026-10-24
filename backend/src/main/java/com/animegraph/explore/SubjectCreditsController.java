package com.animegraph.explore;

import com.animegraph.identity.ReviewedNameProjection;
import com.animegraph.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/explore/anime")
public class SubjectCreditsController {
    private final ReviewedNameProjection names;
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    private final SavedAnimeService service;
    public SubjectCreditsController(SavedAnimeService service, ReviewedNameProjection names) {this.names=names; this.service = service; }
    @GetMapping("/{id}/credits")
    public Object credits(@PathVariable long id, @RequestParam(defaultValue = "CAST") String kind, @RequestParam(defaultValue = "1") int page) {
        return ApiResponse.ok(names.project(service.credits(id, kind, page),"people","characters"));
    }
}
