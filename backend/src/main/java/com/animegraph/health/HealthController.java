package com.animegraph.health;

import com.animegraph.common.ApiResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final HealthService service;

    public HealthController(HealthService service) { this.service = service; }

    @GetMapping("/live")
    public ApiResponse<Map<String, String>> live() {
        return ApiResponse.ok(Map.of("status", "UP"));
    }

    @GetMapping("/ready")
    public ResponseEntity<?> ready() {
        var readiness = service.readiness();
        return readiness.ready()
                ? ResponseEntity.ok(ApiResponse.ok(readiness))
                : ResponseEntity.status(503).body(ApiResponse.error("DEPENDENCIES_UNAVAILABLE", "数据服务尚未就绪", readiness));
    }
}
