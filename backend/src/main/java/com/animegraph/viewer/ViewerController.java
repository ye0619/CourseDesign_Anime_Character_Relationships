package com.animegraph.viewer;

import com.animegraph.common.ApiResponse;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/viewer")
public class ViewerController {
    private static final String TOKEN="bangumi.viewer.token",TOKEN_OWNER="bangumi.viewer.owner";
    private final ViewerService service;
    private final ViewerIdentity identity;
    public ViewerController(ViewerService service,ViewerIdentity identity) {this.service=service;this.identity=identity;}
    public record BindRequest(@Size(max=200)String username,@Size(max=3000)String token) {
        @Override public String toString() {return "BindRequest[username="+username+", token=REDACTED]";}
    }
    @GetMapping public ApiResponse<?> state(HttpServletRequest request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");String id=identity.read(request);
        return ApiResponse.ok(service.state(id,token(request,id)!=null));
    }
    @PostMapping("/bind") public ApiResponse<?> bind(@Valid @RequestBody BindRequest body,HttpServletRequest request,HttpServletResponse response) {
        String token=body.token()==null||body.token().isBlank()?null:body.token().strip();
        var profile=service.verify(body.username(),token);
        String id=identity.read(request);if(id==null) id=identity.create(response);
        service.bind(id,profile,token==null?"PUBLIC":"TOKEN");
        var session=request.getSession();session.removeAttribute(TOKEN);session.removeAttribute(TOKEN_OWNER);
        if(token!=null) {request.changeSessionId();session.setAttribute(TOKEN,token);session.setAttribute(TOKEN_OWNER,id);}
        response.setHeader("Cache-Control","no-store");
        return ApiResponse.ok(service.state(id,token!=null));
    }
    @DeleteMapping("/binding") public ApiResponse<?> unbind(HttpServletRequest request,HttpServletResponse response) {
        service.unbind(identity.read(request));identity.clear(response);
        var session=request.getSession(false);if(session!=null) {session.removeAttribute(TOKEN);session.removeAttribute(TOKEN_OWNER);}
        return ApiResponse.ok(null);
    }
    @PostMapping("/sync") public ApiResponse<?> sync(HttpServletRequest request,HttpServletResponse response) {
        String id=identity.read(request);service.sync(id,token(request,id));response.setStatus(202);return ApiResponse.ok(service.state(id,token(request,id)!=null));
    }
    @GetMapping("/collections") public ApiResponse<?> collections(HttpServletRequest request,HttpServletResponse response,
        @RequestParam(required=false)Integer status,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="1")int page,
        @RequestParam(defaultValue="20")int pageSize,@RequestParam(defaultValue="updated")String sort,
        @RequestParam(defaultValue="COMMUNITY")String tagSource,@RequestParam(defaultValue="")String tag,
        @RequestParam(defaultValue="")String year,@RequestParam(defaultValue="")String format,
        @RequestParam(defaultValue="")String rating,@RequestParam(defaultValue="false")boolean missingTags) {
        response.setHeader("Cache-Control","no-store");return ApiResponse.ok(service.collections(identity.read(request),new CollectionFilter(status,keyword,sort,tagSource,tag,year,format,rating,missingTags),page,pageSize));
    }
    @GetMapping("/preferences") public ApiResponse<?> preferences(HttpServletRequest request,HttpServletResponse response,@RequestParam(defaultValue="COMMUNITY")String tagSource,@RequestParam(defaultValue="FREQUENCY")String tagSort) {
        response.setHeader("Cache-Control","no-store");return ApiResponse.ok(service.preferences(identity.read(request),tagSource,tagSort));
    }
    private String token(HttpServletRequest request,String id) {
        var session=request.getSession(false);
        return session!=null && id!=null && id.equals(session.getAttribute(TOKEN_OWNER))?(String)session.getAttribute(TOKEN):null;
    }
}
