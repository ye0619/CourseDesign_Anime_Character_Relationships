package com.animegraph.connection;

import com.animegraph.characteredit.*;
import com.animegraph.config.SecurityConfig;
import com.animegraph.identity.ReviewedNameProjection;
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

@WebMvcTest({ConnectionController.class,CharacterEditController.class})
@Import(SecurityConfig.class)
class ConnectionSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ConnectionService connections;@MockitoBean CharacterEditService edits;@MockitoBean ReviewedNameProjection names;
    @MockitoBean UserMapper users;@MockitoBean UserService userService;
    final String query="{\"from\":{\"characterUid\":\"character:bangumi:1\",\"subjectId\":1},\"to\":{\"characterUid\":\"character:bangumi:2\",\"subjectId\":2}}";
    @Test void searchesRequireCsrfAndUseServerDefaults()throws Exception {
        mvc.perform(post("/api/v1/connections/searches").contentType("application/json").content(query)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/connections/searches").with(csrf()).contentType("application/json").content(query)).andExpect(status().isOk());
        verify(connections).start(anyString(),argThat(q->q.maxHops()==8&&q.limit()==10&&!q.spoilers()&&!q.includeManualRelations()));
    }
    @Test void invalidBoundsDoNotReachSearchService()throws Exception {
        mvc.perform(post("/api/v1/connections/searches").with(csrf()).contentType("application/json").content(query.replace("}}","},\"maxHops\":0}"))).andExpect(status().isBadRequest());
        verify(connections,never()).start(anyString(),any());
    }
    @Test void sharedCharacterWritesRemainLoopbackAndCsrfProtected()throws Exception {
        String body="{\"uid\":\"character:bangumi:1\",\"version\":0,\"schemaVersion\":1,\"sourceVersion\":\"\",\"fields\":{},\"portraits\":{}}";
        mvc.perform(put("/api/v1/character-edits/character:bangumi:1").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/character-edits/character:bangumi:1").with(csrf()).with(r->{r.setRemoteAddr("192.0.2.2");return r;}).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        verify(edits,never()).save(anyString(),any());
        mvc.perform(put("/api/v1/character-edits/character:bangumi:1").with(csrf()).with(r->{r.setRemoteAddr("127.0.0.1");return r;}).contentType("application/json").content(body)).andExpect(status().isOk());
        verify(edits).save(anyString(),any());
    }
}
