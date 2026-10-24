package com.animegraph.characteredit;

import com.animegraph.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/character-edits/{uid}")
public class CharacterEditController {
    private final CharacterEditService service;
    public CharacterEditController(CharacterEditService service){this.service=service;}
    @GetMapping public Object read(@PathVariable String uid){return ApiResponse.ok(service.read(uid));}
    @PutMapping public Object save(@PathVariable String uid,@RequestBody CharacterEditService.Edit input){return ApiResponse.ok(service.save(uid,input));}
    @GetMapping("/backups") public Object backups(@PathVariable String uid){return ApiResponse.ok(service.backups(uid));}
    @GetMapping("/backups/{id}") public Object backup(@PathVariable String uid,@PathVariable String id){return ApiResponse.ok(service.backup(uid,id));}
}
