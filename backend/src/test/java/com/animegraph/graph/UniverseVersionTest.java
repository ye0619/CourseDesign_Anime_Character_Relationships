package com.animegraph.graph;
import com.animegraph.common.GlobalExceptionHandler;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
class UniverseVersionTest {
    @Test void analysisRejectsAChangedGraphVersion()throws Exception{
        var service=mock(UniverseService.class);var world=new UniverseService.World("universe:fixture:v","fixture","fixture","series:fixture:v",List.of());
        when(service.network(anyString(),anyList(),anyBoolean(),anyString(),anyString(),anyBoolean())).thenReturn(new UniverseService.Network(world,List.of(),List.of(),false,"fixture","g1|evidence:2",null));
        var mvc=MockMvcBuilders.standaloneSetup(new UniverseController(service,mock(com.animegraph.identity.ReviewedNameProjection.class))).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/v1/universes/universe:fixture:v/analysis").param("version","g1|evidence:1")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORLD_CHANGED"));
        mvc.perform(get("/api/v1/universes/universe:fixture:v/analysis").param("version","g1|evidence:2")).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("g1|evidence:2"));
    }
}
