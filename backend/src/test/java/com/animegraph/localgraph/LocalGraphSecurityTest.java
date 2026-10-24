package com.animegraph.localgraph;
import com.animegraph.config.SecurityConfig;
import com.animegraph.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@WebMvcTest(LocalGraphController.class)
@Import(SecurityConfig.class)
class LocalGraphSecurityTest {
    @Autowired MockMvc mvc;@MockitoBean LocalGraphService service;@MockitoBean UserMapper users;@MockitoBean UserService userService;
    private final String body="{\"version\":0,\"schemaVersion\":1,\"workUid\":\"anime:bangumi:1\",\"nodes\":[],\"edges\":[]}";
    @Test void writeRequiresLoopbackAndCsrfWithoutLocalLogin()throws Exception {
        mvc.perform(put("/api/v1/explore/anime/1/local-graph").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/explore/anime/1/local-graph").with(csrf()).with(req->{req.setRemoteAddr("192.0.2.1");return req;}).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        verify(service,never()).save(anyLong(),any());
        mvc.perform(put("/api/v1/explore/anime/1/local-graph").with(csrf()).with(req->{req.setRemoteAddr("127.0.0.1");return req;}).contentType("application/json").content(body)).andExpect(status().isOk());
        verify(service).save(eq(1L),any());
    }
    @Test void invalidDocumentsDoNotReachService()throws Exception {
        mvc.perform(put("/api/v1/explore/anime/1/local-graph").with(csrf()).contentType("application/json").content("{\"version\":0,\"schemaVersion\":3,\"workUid\":\"x\",\"nodes\":[],\"edges\":[]}")).andExpect(status().isBadRequest());
        verify(service,never()).save(anyLong(),any());
    }
}
