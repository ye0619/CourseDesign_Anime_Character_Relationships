package com.animegraph.identity;
import com.animegraph.common.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/entities/{uid}/identities")
public class ExternalIdentityController {
    private final ExternalIdentityMapper mapper;
    public ExternalIdentityController(ExternalIdentityMapper mapper){this.mapper=mapper;}
    @GetMapping public ApiResponse<?> read(@PathVariable String uid){if(!uid.matches("(character|person|anime):[a-z0-9_-]+:[A-Za-z0-9_-]{1,100}"))throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","实体编号不正确");return ApiResponse.ok(mapper.identities(uid));}
}
