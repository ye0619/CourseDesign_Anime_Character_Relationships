package com.animegraph.viewer;
import com.animegraph.common.ApiResponse;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/viewer/report")
public class ViewingReportController {
    private final ViewingReportService service;private final ViewerIdentity identity;
    public ViewingReportController(ViewingReportService service,ViewerIdentity identity){this.service=service;this.identity=identity;}
    @GetMapping public ApiResponse<?> read(HttpServletRequest request,HttpServletResponse response){response.setHeader("Cache-Control","no-store");return ApiResponse.ok(service.read(identity.read(request)));}
    @PostMapping("/jobs") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED) public ApiResponse<?> start(HttpServletRequest request,HttpServletResponse response,@RequestParam(defaultValue="true")boolean supplement){response.setHeader("Cache-Control","no-store");return ApiResponse.ok(service.start(identity.read(request),supplement));}
    @DeleteMapping("/jobs/current") public ApiResponse<?> cancel(HttpServletRequest request,HttpServletResponse response){response.setHeader("Cache-Control","no-store");return ApiResponse.ok(service.cancel(identity.read(request)));}
}
