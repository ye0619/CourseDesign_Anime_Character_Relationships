package com.animegraph.charactergraph;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.animegraph.common.GlobalExceptionHandler;
import com.animegraph.common.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CharacterGraphControllerTest {
    private MockMvc mvc;

    @BeforeEach
    void setup() throws Exception {
        var neo4j = mock(Neo4jCharacterGraphRepository.class);
        when(neo4j.listWorks(51)).thenThrow(new ServiceUnavailableException("unavailable"));
        var service = new CharacterGraphService(new DemoCharacterGraphRepository(new ObjectMapper()), neo4j);
        mvc = MockMvcBuilders.standaloneSetup(new CharacterGraphController(service,new com.animegraph.identity.ReviewedNameProjection(mock(com.animegraph.identity.IdentityNameMapper.class),new ObjectMapper())))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }

    @Test
    void demoEndpointReturnsRealBundledDatasetAndRequestId() throws Exception {
        mvc.perform(get("/api/v1/character-graph").param("dataSource", "DEMO").param("animeUid", "anime:fixture:starlit"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nodes.length()").value(8))
                .andExpect(jsonPath("$.data.edges.length()").value(13))
                .andExpect(jsonPath("$.data.meta.demo").value(true))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void invalidSourceAndMissingWorkReturn400() throws Exception {
        mvc.perform(get("/api/v1/character-graph/works").param("dataSource", "INJECT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/character-graph").param("dataSource", "DEMO"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/character-graph").param("dataSource", "DEMO")
                        .param("animeUid", "anime:fixture:starlit").param("maxNodes", "201"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingWorkAndUnavailableProjectDataAreNotSuccessfulEmptyGraphs() throws Exception {
        mvc.perform(get("/api/v1/character-graph").param("dataSource", "DEMO").param("animeUid", "anime:fixture:missing"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/character-graph/works"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("GRAPH_UNAVAILABLE"));
    }
}
