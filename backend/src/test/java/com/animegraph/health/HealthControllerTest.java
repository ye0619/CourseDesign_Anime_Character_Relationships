package com.animegraph.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.animegraph.common.RequestIdFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {
    @Test
    void unavailableDatabaseReturns503WithRequestId() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new HealthController(new HealthService(List.of())))
                .addFilters(new RequestIdFilter()).build();
        mvc.perform(get("/api/v1/health/live")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        mvc.perform(get("/api/v1/health/ready")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCIES_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.status").value("NOT_READY"))
                .andExpect(header().exists("X-Request-Id"));
    }
}
