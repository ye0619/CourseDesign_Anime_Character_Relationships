package com.animegraph.connection;

import static com.animegraph.connection.ConnectionModels.*;
import com.animegraph.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/connections")
public class ConnectionController {
    private final ConnectionService service;
    @org.springframework.beans.factory.annotation.Autowired private com.animegraph.identity.ReviewedNameProjection names;
    public ConnectionController(ConnectionService service){this.service=service;}
    @GetMapping("/coverage") public ApiResponse<?> coverage(){return ApiResponse.ok(service.coverage());}
    @GetMapping("/characters") public ApiResponse<?> characters(@RequestParam(defaultValue="") String q,@RequestParam(required=false) Long subjectId){return ApiResponse.ok(service.characters(q,subjectId));}
    @PostMapping("/searches") public ApiResponse<?> search(HttpServletRequest request,@Valid @RequestBody Query query){return ApiResponse.ok(names.project(service.start(request.getSession().getId(),query),"paths.nodes"));}
    @GetMapping("/searches/{id}") public ApiResponse<?> read(HttpServletRequest request,@PathVariable String id){return ApiResponse.ok(names.project(service.read(request.getSession().getId(),id),"paths.nodes"));}
    @DeleteMapping("/searches/{id}") public ApiResponse<?> cancel(HttpServletRequest request,@PathVariable String id){return ApiResponse.ok(service.cancel(request.getSession().getId(),id));}
    @PostMapping("/coverage/jobs") public ApiResponse<?> build(HttpServletRequest request,@Valid @RequestBody BuildRequest body){return ApiResponse.ok(service.build(request.getSession().getId(),body));}
    @GetMapping("/coverage/jobs/{id}") public ApiResponse<?> buildRead(HttpServletRequest request,@PathVariable String id){return ApiResponse.ok(service.buildStatus(request.getSession().getId(),id,false));}
    @DeleteMapping("/coverage/jobs/{id}") public ApiResponse<?> buildCancel(HttpServletRequest request,@PathVariable String id){return ApiResponse.ok(service.buildStatus(request.getSession().getId(),id,true));}
}
