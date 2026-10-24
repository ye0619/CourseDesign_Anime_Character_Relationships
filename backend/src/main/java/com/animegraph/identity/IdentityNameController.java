package com.animegraph.identity;
import com.animegraph.auth.AccountPrincipal;
import com.animegraph.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/admin/external-identities/{id}/names")
public class IdentityNameController {
    private final IdentityNameService service;
    public IdentityNameController(IdentityNameService service){this.service=service;}
    @GetMapping public ResponseEntity<?> read(@PathVariable long id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.detail(id)));}
    @PutMapping public ResponseEntity<?> replace(@PathVariable long id,@RequestBody IdentityNameService.Replacement input,@AuthenticationPrincipal AccountPrincipal actor){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.replace(id,input,actor.account().id())));}
}
