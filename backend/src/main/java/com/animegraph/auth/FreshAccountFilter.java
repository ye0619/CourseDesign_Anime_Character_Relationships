package com.animegraph.auth;

import com.animegraph.common.ApiResponse;
import com.animegraph.user.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class FreshAccountFilter extends OncePerRequestFilter {
    private final UserMapper mapper;
    private final ObjectMapper json;
    public FreshAccountFilter(UserMapper mapper, ObjectMapper json) { this.mapper = mapper; this.json = json; }
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountPrincipal principal) {
            try {
                var current = mapper.findById(principal.account().id());
                if (current == null || !current.enabled() || current.authVersion() != principal.account().authVersion()) {
                    SecurityContextHolder.clearContext();
                    var session = request.getSession(false);
                    if (session != null) session.invalidate();
                }
            } catch (DataAccessException exception) {
                response.setStatus(503);
                response.setContentType("application/json;charset=UTF-8");
                json.writeValue(response.getOutputStream(), ApiResponse.error("DATABASE_UNAVAILABLE", "账户服务暂时不可用", null));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
