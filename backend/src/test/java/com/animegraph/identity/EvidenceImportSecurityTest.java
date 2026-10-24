package com.animegraph.identity;
import com.animegraph.auth.AccountPrincipal;
import com.animegraph.common.GlobalExceptionHandler;
import com.animegraph.config.SecurityConfig;
import com.animegraph.user.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EvidenceImportSecurityTest {
    @Test void permanentWorldRefreshIsNotAnAnonymousPublicMetadataJob()throws Exception {
        try(var c=context(false)){mvc(c).perform(post("/api/v1/universes/universe:bangumi:test/jobs").with(csrf()).with(user(new AccountPrincipal(ACCOUNT,List.of("ADMIN"))))).andExpect(status().isForbidden());}
        try(var c=context(true)){mvc(c).perform(post("/api/v1/universes/universe:bangumi:test/jobs").with(csrf())).andExpect(status().isUnauthorized());mvc(c).perform(post("/api/v1/universes/universe:bangumi:test/jobs").with(csrf()).with(user(new AccountPrincipal(ACCOUNT,List.of("USER"))))).andExpect(status().isForbidden());}
    }
    static final UserRecord ACCOUNT=new UserRecord(7,"operator","unused","operator",true,1,Instant.EPOCH);
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,EvidenceImportController.class,IdentityReviewController.class,IdentityNameController.class,GlobalExceptionHandler.class})
    static class Config {
        @Bean ObjectMapper json(){return new ObjectMapper().findAndRegisterModules();}
        @Bean EvidenceImportService service(){return mock(EvidenceImportService.class);}
        @Bean IdentityReviewService identities(){return mock(IdentityReviewService.class);}
        @Bean IdentityNameService names(){return mock(IdentityNameService.class);}
        @Bean UserMapper users(){var mapper=mock(UserMapper.class);when(mapper.findById(7)).thenReturn(ACCOUNT);return mapper;}
        @Bean UserService userService(){return mock(UserService.class);}
    }
    static AnnotationConfigWebApplicationContext context(boolean enabled){var c=new AnnotationConfigWebApplicationContext();c.setServletContext(new MockServletContext());c.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",Map.of("app.admin.enabled",enabled)));c.register(Config.class);c.refresh();return c;}
    static MockMvc mvc(AnnotationConfigWebApplicationContext c){return org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(c).apply(springSecurity()).build();}
    @Test void defaultDisabledBlocksEvenAdmin()throws Exception{try(var c=context(false)){mvc(c).perform(get("/api/v1/admin/evidence-imports").with(user(new AccountPrincipal(ACCOUNT,List.of("ADMIN"))))).andExpect(status().isForbidden());mvc(c).perform(post("/api/v1/admin/evidence-imports").with(csrf()).with(user(new AccountPrincipal(ACCOUNT,List.of("ADMIN")))) .contentType("application/json").content("{\"previewId\":\"x\"}")).andExpect(status().isForbidden());verifyNoInteractions(c.getBean(EvidenceImportService.class));}}
    @Test void enabledStillRequiresAdminAndCsrf()throws Exception{try(var c=context(true)){var mvc=mvc(c);mvc.perform(get("/api/v1/admin/evidence-imports")).andExpect(status().isUnauthorized());mvc.perform(get("/api/v1/admin/evidence-imports").with(user(new AccountPrincipal(ACCOUNT,List.of("USER"))))).andExpect(status().isForbidden());mvc.perform(post("/api/v1/admin/evidence-imports").with(user(new AccountPrincipal(ACCOUNT,List.of("ADMIN")))).contentType("application/json").content("{\"previewId\":\"x\"}")).andExpect(status().isForbidden());verifyNoInteractions(c.getBean(EvidenceImportService.class));}}
    @Test void authenticatedAdminCanPreviewAndActorComesFromSession()throws Exception{try(var c=context(true)){mvc(c).perform(multipart("/api/v1/admin/evidence-imports/preview").file(new MockMultipartFile("file","evidence.json","application/json","{}".getBytes())).with(user(new AccountPrincipal(ACCOUNT,List.of("ADMIN")))).with(csrf())).andExpect(status().isOk());verify(c.getBean(EvidenceImportService.class)).preview(any(byte[].class),eq(7L));}}
    @Test void identityMaintenanceDefaultOffAndCsrfApply()throws Exception{
        try(var c=context(false)){mvc(c).perform(get("/api/v1/admin/external-identities").with(user(new AccountPrincipal(ACCOUNT,List.of("ADMIN"))))).andExpect(status().isForbidden());verifyNoInteractions(c.getBean(IdentityReviewService.class));}
        try(var c=context(true)){var mvc=mvc(c);mvc.perform(get("/api/v1/admin/external-identities")).andExpect(status().isUnauthorized());mvc.perform(get("/api/v1/admin/external-identities").with(user(new AccountPrincipal(ACCOUNT,List.of("USER"))))).andExpect(status().isForbidden());mvc.perform(post("/api/v1/admin/external-identities/1/decisions").with(user(new AccountPrincipal(ACCOUNT,List.of("ADMIN")))).contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(c.getBean(IdentityReviewService.class));}
    }
    @Test void identityDecisionActorIsSessionBoundAndResponseNotCached()throws Exception{try(var c=context(true)){mvc(c).perform(post("/api/v1/admin/external-identities/1/decisions").with(user(new AccountPrincipal(ACCOUNT,List.of("ADMIN")))).with(csrf()).contentType("application/json").content("{\"expectedVersion\":0,\"decision\":\"REVIEW\",\"reason\":\"verified\"}")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));verify(c.getBean(IdentityReviewService.class)).decide(eq(1L),any(),eq(7L));}}
    @Test void reviewedNamesRequireEnabledAdminAndCsrf()throws Exception{
        try(var c=context(false)){mvc(c).perform(get("/api/v1/admin/external-identities/1/names").with(user(new AccountPrincipal(ACCOUNT,List.of("ADMIN"))))).andExpect(status().isForbidden());verifyNoInteractions(c.getBean(IdentityNameService.class));}
        try(var c=context(true)){var mvc=mvc(c);mvc.perform(get("/api/v1/admin/external-identities/1/names")).andExpect(status().isUnauthorized());mvc.perform(get("/api/v1/admin/external-identities/1/names").with(user(new AccountPrincipal(ACCOUNT,List.of("USER"))))).andExpect(status().isForbidden());mvc.perform(put("/api/v1/admin/external-identities/1/names").with(user(new AccountPrincipal(ACCOUNT,List.of("ADMIN")))).contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(c.getBean(IdentityNameService.class));}
    }
    @Test void nameReplacementUsesSessionActorAndNoStore()throws Exception{try(var c=context(true)){mvc(c).perform(put("/api/v1/admin/external-identities/1/names").with(user(new AccountPrincipal(ACCOUNT,List.of("ADMIN")))).with(csrf()).contentType("application/json").content("{\"expectedVersion\":3,\"reason\":\"verified\",\"names\":[],\"actorId\":999}")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));verify(c.getBean(IdentityNameService.class)).replace(eq(1L),any(),eq(7L));}}
}
