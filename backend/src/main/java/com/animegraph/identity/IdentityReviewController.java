package com.animegraph.identity;

import com.animegraph.auth.AccountPrincipal;
import com.animegraph.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/external-identities")
public class IdentityReviewController {
    private final IdentityReviewService service;
    public IdentityReviewController(IdentityReviewService service){this.service=service;}
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping Object list(@RequestParam(defaultValue="")String provider,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="0")long beforeId,@RequestParam(defaultValue="30")int limit){return ApiResponse.ok(service.list(provider,status,beforeId,limit));}
    @GetMapping("/{id}") Object detail(@PathVariable long id){return ApiResponse.ok(service.detail(id));}
    @PostMapping Object create(@RequestBody IdentityReviewService.Candidate input,@AuthenticationPrincipal AccountPrincipal actor){return ApiResponse.ok(service.create(input,actor.account().id()));}
    @PostMapping("/{id}/decisions") Object decide(@PathVariable long id,@RequestBody IdentityReviewService.Decision input,@AuthenticationPrincipal AccountPrincipal actor){return ApiResponse.ok(service.decide(id,input,actor.account().id()));}
}
