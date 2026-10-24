package com.animegraph.config;

import com.animegraph.common.ApiResponse;
import com.animegraph.auth.AccountPrincipal;
import com.animegraph.auth.FreshAccountFilter;
import com.animegraph.user.UserMapper;
import com.animegraph.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper, UserMapper users, UserService service,
            @Value("${app.admin.enabled:false}")boolean legacyAdmin) throws Exception {
        http.authorizeHttpRequests(auth -> {
                    if(legacyAdmin) auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN").requestMatchers("/api/v1/auth/login", "/api/v1/auth/logout").permitAll();
                    else auth.requestMatchers("/api/v1/admin/**","/api/v1/auth/login","/api/v1/auth/logout","/api/v1/auth/me").denyAll();
                    if(legacyAdmin) auth.requestMatchers(HttpMethod.POST,"/api/v1/universes/*/jobs").hasRole("ADMIN");
                    else auth.requestMatchers(HttpMethod.POST,"/api/v1/universes/*/jobs").denyAll();
                    // User-requested local save without registration; CSRF remains required.
                    // Explicit loopback authorization prevents exposing writes when read services bind externally.
                    auth.requestMatchers(HttpMethod.DELETE,"/api/v1/anime/*").access((authentication,context) ->
                            new org.springframework.security.authorization.AuthorizationDecision(
                                    java.util.Set.of("127.0.0.1","0:0:0:0:0:0:0:1","::1").contains(context.getRequest().getRemoteAddr())))
                        .requestMatchers(HttpMethod.PUT,"/api/v1/explore/anime/*/local-graph").access((authentication,context) ->
                            new org.springframework.security.authorization.AuthorizationDecision(
                                    java.util.Set.of("127.0.0.1","0:0:0:0:0:0:0:1","::1").contains(context.getRequest().getRemoteAddr())))
                        .requestMatchers(HttpMethod.POST,"/api/v1/explore/anime/*/saved").access((authentication,context) ->
                            new org.springframework.security.authorization.AuthorizationDecision(
                                    java.util.Set.of("127.0.0.1","0:0:0:0:0:0:0:1","::1").contains(context.getRequest().getRemoteAddr())))
                        .requestMatchers(HttpMethod.POST,"/api/v1/explore/anime/*/metadata/jobs").permitAll()
                        .requestMatchers("/api/v1/viewer","/api/v1/viewer/**").permitAll()
                        .requestMatchers(HttpMethod.PUT,"/api/v1/character-edits/*").access((authentication,context) -> new org.springframework.security.authorization.AuthorizationDecision(java.util.Set.of("127.0.0.1","0:0:0:0:0:0:0:1","::1").contains(context.getRequest().getRemoteAddr()))).requestMatchers(HttpMethod.POST,"/api/v1/connections/searches").permitAll().requestMatchers(HttpMethod.DELETE,"/api/v1/connections/searches/*","/api/v1/connections/coverage/jobs/*").permitAll().requestMatchers(HttpMethod.POST,"/api/v1/connections/coverage/jobs").access((authentication,context) -> new org.springframework.security.authorization.AuthorizationDecision(java.util.Set.of("127.0.0.1","0:0:0:0:0:0:0:1","::1").contains(context.getRequest().getRemoteAddr()))).requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll().requestMatchers("/error").permitAll().anyRequest().denyAll();
                });
        if(legacyAdmin) http.formLogin(login -> login.loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request,response,auth) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            mapper.writeValue(response.getOutputStream(),ApiResponse.ok(service.me((AccountPrincipal)auth.getPrincipal())));
                        })
                        .failureHandler((request,response,exception) -> writeError(response,mapper,401,"UNAUTHORIZED","账号或密码不正确")))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request,response,auth) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            mapper.writeValue(response.getOutputStream(),ApiResponse.ok(null));
                        })).addFilterBefore(new FreshAccountFilter(users,mapper),UsernamePasswordAuthenticationFilter.class);
        else http.formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable);
        return http.httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, mapper, 401, "UNAUTHORIZED", "请先登录"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, mapper, 403, "FORBIDDEN", "无权执行此操作")))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    private static void writeError(HttpServletResponse response, ObjectMapper mapper,
                                   int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiResponse.error(code, message, null));
    }
}
