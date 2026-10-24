package com.animegraph.explore;

import com.animegraph.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/anime")
public class LocalWorkController {
    private final SavedAnimeService saved;
    public LocalWorkController(SavedAnimeService saved){this.saved=saved;}
    @DeleteMapping("/{uid}")
    public Object delete(@PathVariable String uid,jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return ApiResponse.ok(saved.deleteLocal(uid));
    }
}
