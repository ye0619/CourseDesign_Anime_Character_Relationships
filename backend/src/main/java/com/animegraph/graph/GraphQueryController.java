package com.animegraph.graph;

import com.animegraph.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class GraphQueryController {
    private final GraphQueryService queries;
    public GraphQueryController(GraphQueryService queries) {this.queries=queries;}
    @GetMapping("/graph/neighbors") Object neighbors(@RequestParam String centerUid,@RequestParam(required=false)String relationTypes,@RequestParam(defaultValue="80")int maxNodes,@RequestParam(required=false)String animeUid) {return ApiResponse.ok(queries.neighbors(centerUid,relationTypes,maxNodes,animeUid));}
    @GetMapping("/analysis/common") Object common(@RequestParam String animeA,@RequestParam String animeB) {return ApiResponse.ok(queries.common(animeA,animeB));}
    @GetMapping("/analysis/paths") Object paths(@RequestParam String fromUid,@RequestParam String toUid,@RequestParam(defaultValue="4")int maxDepth,@RequestParam(defaultValue="5")int limit) {return ApiResponse.ok(queries.paths(fromUid,toUid,maxDepth,limit));}
    @GetMapping("/analysis/people/ranking") Object ranking(@RequestParam(required=false)Integer yearFrom,@RequestParam(required=false)Integer yearTo,@RequestParam(required=false)String roleCodes,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize) {return ApiResponse.ok(queries.ranking(yearFrom,yearTo,roleCodes,page,pageSize));}
    @GetMapping("/analysis/people/{uid}/collaborators") Object collaborators(@PathVariable String uid,@RequestParam(required=false)Integer yearFrom,@RequestParam(required=false)Integer yearTo,@RequestParam(required=false)String roleCodes,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize) {return ApiResponse.ok(queries.collaborators(uid,null,yearFrom,yearTo,roleCodes,page,pageSize));}
    @GetMapping("/analysis/people/{uid}/collaborators/{otherUid}/works") Object works(@PathVariable String uid,@PathVariable String otherUid,@RequestParam(required=false)Integer yearFrom,@RequestParam(required=false)Integer yearTo,@RequestParam(required=false)String roleCodes,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize) {return ApiResponse.ok(queries.collaborators(uid,otherUid,yearFrom,yearTo,roleCodes,page,pageSize));}
    @GetMapping("/analysis/people/{uid}/network") Object network(@PathVariable String uid,@RequestParam(required=false)Integer yearFrom,@RequestParam(required=false)Integer yearTo,@RequestParam(required=false)String roleCodes,@RequestParam(defaultValue="20")int limit) {return ApiResponse.ok(queries.network(uid,yearFrom,yearTo,roleCodes,limit));}
}
