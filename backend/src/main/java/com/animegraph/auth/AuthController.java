package com.animegraph.auth;

import com.animegraph.common.ApiResponse;
import com.animegraph.user.UserService;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final UserService users;
    public AuthController(UserService users) { this.users=users; }
    @GetMapping({"/auth/csrf","/session/csrf"})
    public ApiResponse<?> csrf(CsrfToken token) {
        return ApiResponse.ok(Map.of("token",token.getToken(),"headerName",token.getHeaderName(),"parameterName",token.getParameterName()));
    }
    @GetMapping("/auth/me")
    public ApiResponse<?> me(@AuthenticationPrincipal AccountPrincipal principal) { return ApiResponse.ok(users.me(principal)); }
}
