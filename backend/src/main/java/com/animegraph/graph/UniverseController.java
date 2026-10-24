package com.animegraph.graph;
import com.animegraph.identity.ReviewedNameProjection;
import com.animegraph.common.ApiResponse;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
public class UniverseController {
    private final ReviewedNameProjection names;
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    private final UniverseService service;
    public UniverseController(UniverseService service, ReviewedNameProjection names){this.names=names;this.service=service;}
    @GetMapping("/universes")public ApiResponse<?> list(){return ApiResponse.ok(service.list());}
    @GetMapping("/universes/{uid}")public ApiResponse<?> detail(@PathVariable String uid){return ApiResponse.ok(service.world(uid));}
    @GetMapping({"/universes/{uid}/network","/series/{uid}/network"})public ApiResponse<?> network(@PathVariable String uid,@RequestParam(defaultValue="")List<Long> subjects,@RequestParam(defaultValue="false")boolean spoilers,@RequestParam(defaultValue="")String relation,@RequestParam(defaultValue="")String faction,@RequestParam(defaultValue="false")boolean strict){return ApiResponse.ok(names.project(service.network(uid,subjects,spoilers,relation,faction,strict),"nodes"));}
    @PostMapping("/universes/{uid}/jobs")@ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)public ApiResponse<?> start(@PathVariable String uid){return ApiResponse.ok(service.start(uid));}
    @GetMapping("/universes/{uid}/analysis")public ApiResponse<?> analysis(@PathVariable String uid,@RequestParam(defaultValue="")List<Long> subjects,@RequestParam(defaultValue="false")boolean spoilers,@RequestParam(defaultValue="")String relation,@RequestParam(defaultValue="")String faction,@RequestParam(defaultValue="false")boolean strict,@RequestParam(defaultValue="")String fromUid,@RequestParam(defaultValue="")String toUid,@RequestParam(defaultValue="4")int maxDepth,@RequestParam(required=false)String version){var network=service.network(uid,subjects,spoilers,relation,faction,strict);if(version!=null&&!version.equals(network.version()))throw new com.animegraph.common.ApiException(org.springframework.http.HttpStatus.CONFLICT,"WORLD_CHANGED","关系资料已更新，请重新读取图谱再查看统计");return ApiResponse.ok(UniverseAnalysisService.analyze(network,fromUid,toUid,maxDepth));}
}
