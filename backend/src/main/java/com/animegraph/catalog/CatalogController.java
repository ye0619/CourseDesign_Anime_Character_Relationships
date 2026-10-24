package com.animegraph.catalog;

import com.animegraph.common.ApiResponse;
import com.animegraph.identity.ReviewedNameProjection;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService catalog;private final ReviewedNameProjection names;
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    public CatalogController(CatalogService catalog,ReviewedNameProjection names) {this.catalog=catalog;this.names=names;}
    private String kind(String collection) {return switch(collection){case "anime" -> "ANIME";case "people" -> "PERSON";default -> "CHARACTER";};}
    @GetMapping("/{collection:anime|people|characters}")
    public Object list(@PathVariable String collection,@RequestParam(required=false) String keyword,@RequestParam(required=false) Integer year,@RequestParam(required=false) String format,@RequestParam(required=false)String source,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.ok(names.project(catalog.list(kind(collection),keyword,year,format,true,page,pageSize,source),"items"));
    }
    @GetMapping("/{collection:anime|people|characters}/{uid}")
    public Object detail(@PathVariable String collection,@PathVariable String uid) {return ApiResponse.ok(names.project(catalog.detail(kind(collection),uid)));}
    @GetMapping("/{collection:anime|people|characters}/{uid}/{section:staff|characters|cast|works}")
    public Object related(@PathVariable String collection,@PathVariable String uid,@PathVariable String section,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.ok(names.project(catalog.related(kind(collection),uid,section,page,pageSize),"items.entity","items.character"));
    }
}
