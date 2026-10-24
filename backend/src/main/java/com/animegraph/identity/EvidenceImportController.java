package com.animegraph.identity;

import com.animegraph.auth.AccountPrincipal;
import com.animegraph.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Existing optional ADMIN maintenance boundary and CSRF apply to every route. */
@RestController
@RequestMapping("/api/v1/admin")
public class EvidenceImportController {
    private final EvidenceImportService service;
    public EvidenceImportController(EvidenceImportService service){this.service=service;}
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    public record Commit(@NotBlank @Size(max=36)String previewId) {}
    public record Reason(@NotBlank @Size(max=500)String reason) {}
    @PostMapping("/evidence-imports/preview") Object preview(@RequestParam MultipartFile file,@AuthenticationPrincipal AccountPrincipal actor)throws IOException{
        if(file.getSize()>512*1024)throw new com.animegraph.common.ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,"FILE_TOO_LARGE","证据包不得超过512KB");
        return ApiResponse.ok(service.preview(file.getBytes(),actor.account().id()));
    }
    @PostMapping("/evidence-imports") Object apply(@Valid @RequestBody Commit input,@AuthenticationPrincipal AccountPrincipal actor){return ApiResponse.ok(service.apply(input.previewId(),actor.account().id()));}
    @GetMapping("/evidence-imports") Object list(@RequestParam(defaultValue="30")int limit){return ApiResponse.ok(service.list(limit));}
    @PostMapping("/evidence-imports/{batchId}/disable") Object disable(@PathVariable String batchId,@Valid @RequestBody Reason input,@AuthenticationPrincipal AccountPrincipal actor){return ApiResponse.ok(service.disable(batchId,input.reason(),actor.account().id()));}
    @PostMapping("/relationship-assertions/{uid}/revoke") Object revoke(@PathVariable String uid,@Valid @RequestBody Reason input,@AuthenticationPrincipal AccountPrincipal actor){return ApiResponse.ok(service.revoke(uid,input.reason(),actor.account().id()));}
}
