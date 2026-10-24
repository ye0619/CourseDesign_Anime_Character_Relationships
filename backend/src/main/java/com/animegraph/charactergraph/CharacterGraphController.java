package com.animegraph.charactergraph;

import com.animegraph.identity.ReviewedNameProjection;
import static com.animegraph.charactergraph.CharacterGraphModels.*;
import com.animegraph.common.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/character-graph")
public class CharacterGraphController {
    private final ReviewedNameProjection names;
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    private final CharacterGraphService service;

    public CharacterGraphController(CharacterGraphService service, ReviewedNameProjection names) {this.names=names; this.service = service; }

    @GetMapping("/works")
    public ApiResponse<Catalog> works(@RequestParam(defaultValue = "NEO4J") DataSource dataSource) {
        return ApiResponse.ok(service.works(dataSource));
    }

    @GetMapping
    public ApiResponse<?> graph(@RequestParam(defaultValue = "NEO4J") DataSource dataSource,
                                            @RequestParam @NotBlank @Size(max = 200) String animeUid,
                                            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int maxNodes) {
        return ApiResponse.ok(names.project(service.graph(dataSource, animeUid, maxNodes),"nodes"));
    }
}
