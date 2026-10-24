package com.animegraph.explore;

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

@WebMvcTest({ExploreController.class,LocalWorkController.class})
@Import(SecurityConfig.class)
class SavedAnimeSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ExploreService explore;
    @MockitoBean SavedAnimeService saved;
    @MockitoBean com.animegraph.identity.ReviewedNameProjection names;
    @MockitoBean UserMapper users;
    @MockitoBean UserService userService;
    @Test void deleteRequiresCsrfAndLoopback()throws Exception {
        String path="/api/v1/anime/anime:fixture:a1";
        mvc.perform(delete(path)).andExpect(status().isForbidden());
        mvc.perform(delete(path).with(csrf()).with(request->{request.setRemoteAddr("192.0.2.1");return request;})).andExpect(status().isUnauthorized());
        verify(saved,never()).deleteLocal(anyString());
        mvc.perform(delete(path).with(csrf()).with(request->{request.setRemoteAddr("127.0.0.1");return request;})).andExpect(status().isOk());
        verify(saved).deleteLocal("anime:fixture:a1");
    }
    @Test void saveRequiresCsrfAndLoopbackButDoesNotRequireRegistration()throws Exception {
        mvc.perform(post("/api/v1/explore/anime/12/saved")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/explore/anime/12/saved").with(csrf()).with(request->{request.setRemoteAddr("192.0.2.1");return request;})).andExpect(status().isUnauthorized());
        verify(saved,never()).start(anyLong(),anyBoolean());
        mvc.perform(post("/api/v1/explore/anime/12/saved").with(csrf()).with(request->{request.setRemoteAddr("127.0.0.1");return request;})).andExpect(status().isAccepted());
        verify(saved).start(12,false);
    }
}
