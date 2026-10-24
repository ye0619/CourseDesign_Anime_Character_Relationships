package com.animegraph.explore;

import com.animegraph.identity.ReviewedNameProjection;
import com.animegraph.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/explore")
public class OnlineNetworkController {
    private final ReviewedNameProjection names;
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    private final OnlineNetworkService service;
    public OnlineNetworkController(OnlineNetworkService service, ReviewedNameProjection names) {this.names=names;this.service=service;}
    @GetMapping("/anime/{id}/network")
    public Object network(@PathVariable long id,@RequestParam(defaultValue="CHARACTERS")String kind,@RequestParam(defaultValue="1")int page) {
        return ApiResponse.ok(names.project(service.network(id,kind,page),"nodes"));
    }
    @GetMapping("/characters/{id}/relations")
    public Object relations(@PathVariable long id) {return ApiResponse.ok(names.project(service.relations(id),"nodes"));}
}
