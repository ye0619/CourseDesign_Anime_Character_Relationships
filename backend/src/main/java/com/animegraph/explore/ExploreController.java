package com.animegraph.explore;

import com.animegraph.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/explore/anime")
public class ExploreController {
    private final ExploreService service;
    private final SavedAnimeService saved;
    private final com.animegraph.identity.ReviewedNameProjection names;
    public ExploreController(ExploreService service,SavedAnimeService saved,com.animegraph.identity.ReviewedNameProjection names) {this.service=service;this.saved=saved;this.names=names;}
    @GetMapping public Object search(@RequestParam(defaultValue="")String keyword,@RequestParam(required=false)Integer year,@RequestParam(defaultValue="")String format,@RequestParam(defaultValue="MIXED")String mode,@RequestParam(defaultValue="")String source,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="1")int localPage) {
        return ApiResponse.ok(service.search(keyword,year,format,mode,source,page,localPage));
    }
    @GetMapping("/{id}") public Object detail(@PathVariable long id,@RequestParam(defaultValue="AUTO")String source) {
        var payload=saved.read(id);
        if(payload==null)return ApiResponse.ok(service.detail(id,"AUTO"));
        var entity=new java.util.LinkedHashMap<>(payload.entity());
        try {var legacy=service.detail(id,"LOCAL");entity.putAll(ExploreService.merge(entity,legacy.entity()));}catch(com.animegraph.common.ApiException e){if(e.status().value()!=404)throw e;}
        entity.put("localAvailable",true);entity.put("libraryStatus","ARCHIVED");
        var state=saved.status(id);
        return ApiResponse.ok(new ExploreService.Detail(entity,entity,new ExploreService.Meta("SKIPPED","OK",state.updatedAt(),true,false,
                "已保存资料；"+(state.status().equals("COMPLETE")?"可主动更新。":"部分资料待补全，可重新更新。")),"LOCAL"));
    }
    @GetMapping("/{id}/graph") public Object graph(@PathVariable long id){return ApiResponse.ok(names.project(saved.graph(id),"nodes"));}
    @GetMapping("/{id}/saved") public Object saved(@PathVariable long id){return ApiResponse.ok(saved.status(id));}
    @PostMapping("/{id}/saved") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public Object save(@PathVariable long id,@RequestParam(defaultValue="false")boolean refresh){return ApiResponse.ok(saved.start(id,refresh));}
}
