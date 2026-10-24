package com.animegraph.admin;

import com.animegraph.audit.AuditService;
import com.animegraph.auth.AccountPrincipal;
import com.animegraph.catalog.CatalogService;
import com.animegraph.common.*;
import com.animegraph.graph.GraphStore;
import com.animegraph.importing.ImportService;
import com.animegraph.user.UserService;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final GraphStore graph;private final CatalogService catalog;private final ImportService imports;private final UserService users;private final AuditService audit;private final ObjectMapper json;private final JdbcTemplate jdbc;
    public AdminController(GraphStore graph,CatalogService catalog,ImportService imports,UserService users,AuditService audit,ObjectMapper json,JdbcTemplate jdbc) {this.graph=graph;this.catalog=catalog;this.imports=imports;this.users=users;this.audit=audit;this.json=json;this.jdbc=jdbc;}
    @GetMapping("/entities") Object entities(@RequestParam(defaultValue="ANIME")String type,@RequestParam(required=false)String keyword,@RequestParam(required=false)String source,@RequestParam(required=false)Boolean active,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize) {return ApiResponse.ok(catalog.list(type,keyword,null,null,active,page,pageSize,source));}
    @PostMapping("/entities") @ResponseStatus(HttpStatus.CREATED)
    Object createEntity(@RequestBody JsonNode input,@AuthenticationPrincipal AccountPrincipal actor) {
        var node=input.deepCopy();if(!(node instanceof com.fasterxml.jackson.databind.node.ObjectNode body)) throw GraphStore.bad("实体必须是对象");
        body.put("sourceId",UUID.randomUUID().toString());
        String uid=graph.upsertEntity("manual",body,"manual");audit.record(actor.account().id(),"ENTITY_CREATE","ENTITY",uid,"人工新增实体");return ApiResponse.ok(graph.entity(uid,true));
    }
    @PatchMapping("/entities/{uid}") Object updateEntity(@PathVariable String uid,@RequestBody Map<String,Object> changes,@AuthenticationPrincipal AccountPrincipal actor) {var result=graph.patchEntity(uid,changes);audit.record(actor.account().id(),"ENTITY_UPDATE","ENTITY",uid,"更新字段 "+changes.keySet());return ApiResponse.ok(result);}
    @GetMapping("/relations") Object relations(@RequestParam(required=false)String type,@RequestParam(required=false)String entityUid,@RequestParam(required=false)Boolean active,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize) {return ApiResponse.ok(catalog.relations(type,entityUid,active,page,pageSize));}
    @PostMapping("/relations") @ResponseStatus(HttpStatus.CREATED)
    Object createRelation(@RequestBody JsonNode input,@AuthenticationPrincipal AccountPrincipal actor) {String uid=graph.upsertRelation("manual",input,"manual");audit.record(actor.account().id(),"RELATION_CREATE","RELATION",uid,"人工新增关系");return ApiResponse.ok(Map.of("uid",uid));}
    @PatchMapping("/relations/{uid}") Object updateRelation(@PathVariable String uid,@RequestBody Map<String,Object> changes,@AuthenticationPrincipal AccountPrincipal actor) {graph.patchRelation(uid,changes);audit.record(actor.account().id(),"RELATION_UPDATE","RELATION",uid,"更新关系字段");return ApiResponse.ok(null);}
    @PostMapping("/imports") @ResponseStatus(HttpStatus.ACCEPTED)
    Object upload(@RequestParam MultipartFile file,@AuthenticationPrincipal AccountPrincipal actor) throws IOException {return ApiResponse.ok(imports.create(file.getBytes(),actor.account().id(),null));}
    @GetMapping("/imports") Object tasks(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize) {return ApiResponse.ok(imports.list(page,pageSize));}
    @GetMapping("/imports/{id}") Object task(@PathVariable long id) {return ApiResponse.ok(imports.required(id));}
    @GetMapping("/imports/{id}/records") Object records(@PathVariable long id,@RequestParam(required=false)String status,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize) {return ApiResponse.ok(imports.entries(id,status,page,pageSize));}
    @PostMapping("/imports/{id}/retry") @ResponseStatus(HttpStatus.ACCEPTED)
    Object retry(@PathVariable long id,@AuthenticationPrincipal AccountPrincipal actor) {return ApiResponse.ok(imports.retry(id,actor.account().id()));}
    public record NewUser(@NotBlank String username,@NotBlank String password,@Size(max=100)String displayName,@NotEmpty List<String> roles) {}
    public record UserUpdate(@Size(max=100)String displayName,Boolean enabled,List<String> roles) {}
    public record Password(@NotBlank String password) {}
    @GetMapping("/users") Object users(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize) {return ApiResponse.ok(users.list(page,pageSize));}
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED)
    Object createUser(@Valid @RequestBody NewUser input,@AuthenticationPrincipal AccountPrincipal actor) {return ApiResponse.ok(users.create(input.username(),input.password(),input.displayName(),input.roles(),actor.account().id()));}
    @PatchMapping("/users/{id}") Object updateUser(@PathVariable long id,@Valid @RequestBody UserUpdate input,@AuthenticationPrincipal AccountPrincipal actor) {return ApiResponse.ok(users.update(id,input.displayName(),input.enabled(),input.roles(),actor.account().id()));}
    @PostMapping("/users/{id}/reset-password") Object password(@PathVariable long id,@Valid @RequestBody Password input,@AuthenticationPrincipal AccountPrincipal actor) {users.resetPassword(id,input.password(),actor.account().id());return ApiResponse.ok(null);}
    @GetMapping("/logs") Object logs(@RequestParam(required=false)String action,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize) {
        int offset=PageResult.offset(page,pageSize);String filter=action==null?"":" WHERE action=?";var args=new ArrayList<Object>();if(action!=null)args.add(action);
        Long total=jdbc.queryForObject("SELECT count(*) FROM operation_log"+filter,Long.class,args.toArray());args.add(pageSize);args.add(offset);
        return ApiResponse.ok(new PageResult<>(jdbc.queryForList("SELECT id,request_id,actor_id,action,target_type,target_uid,result,summary,created_at FROM operation_log"+filter+" ORDER BY id DESC LIMIT ? OFFSET ?",args.toArray()),page,pageSize,total==null?0:total));
    }
    @GetMapping("/stats") Object stats() {
        var result=new LinkedHashMap<String,Object>();result.put("entities",graph.read("MATCH (n:Entity) RETURN n.active AS active,head([label IN labels(n) WHERE label <> 'Entity']) AS type,count(n) AS count",Map.of()));
        result.put("relations",graph.read("MATCH (:Entity)-[r]->(:Entity) WHERE r.uid IS NOT NULL RETURN type(r) AS type,r.active AS active,count(r) AS count",Map.of()));
        result.put("imports",jdbc.queryForList("SELECT status,count(*) AS count FROM import_task GROUP BY status"));result.put("users",jdbc.queryForObject("SELECT count(*) FROM sys_user",Long.class));return ApiResponse.ok(result);
    }
}
