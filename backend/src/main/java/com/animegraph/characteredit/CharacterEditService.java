package com.animegraph.characteredit;

import com.animegraph.common.ApiException;
import com.animegraph.identity.NameResolutionService;
import com.animegraph.localgraph.LocalGraphRules;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Instance-shared sparse overrides. Empty fields and absent fields deliberately differ. */
@Service
public class CharacterEditService {
    public record Edit(String uid,long version,int schemaVersion,String sourceVersion,ObjectNode fields,Map<String,JsonNode> portraits) {}
    private final CharacterEditMapper mapper;private final ObjectMapper json;
    public CharacterEditService(CharacterEditMapper mapper,ObjectMapper json){this.mapper=mapper;this.json=json;}
    private void uid(String uid){if(uid==null||!uid.matches("character:bangumi:[1-9][0-9]{0,9}"))throw bad("角色身份无效");}
    public Edit read(String uid){uid(uid);return decode(uid,mapper.read(uid));}
    private Edit decode(String uid,CharacterEditMapper.Row row) {
        if(row==null)return new Edit(uid,0,1,"",json.createObjectNode(),Map.of());
        try {var data=json.readTree(row.payload());var fields=data.path("fields").isObject()?(ObjectNode)data.get("fields"):json.createObjectNode();
            var portraits=new LinkedHashMap<String,JsonNode>();data.path("portraits").fields().forEachRemaining(e->portraits.put(e.getKey(),e.getValue()));
            return new Edit(uid,row.version(),1,data.path("sourceVersion").asText(""),fields,Map.copyOf(portraits));
        }catch(Exception e){throw new IllegalStateException("Invalid character modification",e);}
    }
    @Transactional public Edit save(String uid,Edit input) {
        uid(uid);validate(uid,input);mapper.ensure(uid);var old=mapper.lock(uid);
        if(old.version()!=input.version())throw new ApiException(HttpStatus.CONFLICT,"CHARACTER_EDIT_CONFLICT","角色资料已被其他窗口修改，草稿保留；请导出草稿后重新读取并核对");
        String payload;try{payload=json.writeValueAsString(input);}catch(Exception e){throw bad("角色修改格式无效");}
        mapper.backup(UUID.randomUUID().toString(),old);mapper.replace(new CharacterEditMapper.Row(uid,input.version(),payload));mapper.trim(uid);return read(uid);
    }
    public List<Map<String,Object>> backups(String uid){uid(uid);return mapper.backups(uid);}
    public Edit backup(String uid,String id){uid(uid);var row=mapper.backupRead(uid,id);if(row==null)throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","备份不存在");return decode(uid,row);}
    public Map<String,Edit> many(List<String> uids){if(uids.isEmpty())return Map.of();if(uids.size()>1200)throw bad("本次角色过多");var rows=new HashMap<String,Edit>();for(var row:mapper.many(uids))rows.put(row.uid(),decode(row.uid(),row));return rows;}
    public List<String> search(String q){return mapper.search(q.replace("%","\\%").replace("_","\\_"));}
    public void apply(ObjectNode node,Edit edit) {
        if(edit==null)return;
        node.set("sourceOriginal",node.deepCopy());node.put("localModified",!edit.fields().isEmpty());node.put("localEditVersion",edit.version());
        var f=edit.fields();for(String field:List.of("imageUrl","gender","description","notes"))if(f.has(field))node.set(field,f.get(field));
        if(f.has("description"))node.set("summary",f.get("description"));
        if(f.has("names")) {
            var names=new ArrayList<NameResolutionService.LocalName>();for(var n:f.path("names"))names.add(new NameResolutionService.LocalName(n.path("id").asText(),n.path("text").asText(),n.path("locale").asText("und"),n.path("kind").asText("ALIAS"),"LOCAL","MANUAL"));
            String display=NameResolutionService.localDisplay(names,f.path("preferredNameId").asText(null),List.of());node.put("label",display);node.put("nameCn",display);node.put("name",display);if(node.has("characterName"))node.put("characterName",display);node.set("names",json.valueToTree(names));
            var aliases=new LinkedHashSet<String>();for(var a:node.path("aliases"))aliases.add(a.asText());names.forEach(n->aliases.add(n.text()));node.set("aliases",json.valueToTree(aliases));
            ArrayNode assertions=node.path("nameAssertions").isArray()?(ArrayNode)node.get("nameAssertions"):node.putArray("nameAssertions");
            for(var n:names)assertions.addObject().put("name",n.text()).put("locale",n.locale()).put("kind",n.kind()).put("provider","LOCAL").put("reviewStatus","MANUAL").put("sourceUrl","").putNull("retrievedAt").putNull("reviewedOn");
        }
        node.set("portraitSettings",json.valueToTree(edit.portraits()));
        if(!edit.sourceVersion().isBlank())node.put("modifiedAgainstSourceVersion",edit.sourceVersion());
    }
    private void validate(String uid,Edit input) {
        if(input==null||!uid.equals(input.uid())||input.version()<0||input.schemaVersion()!=1||input.fields()==null||input.portraits()==null||input.sourceVersion()==null||input.sourceVersion().length()>200)throw bad("角色修改格式无效");
        var f=input.fields();f.fieldNames().forEachRemaining(k->{if(!Set.of("names","preferredNameId","imageUrl","gender","description","notes").contains(k))throw bad("存在不支持的修改字段");});
        for(String field:List.of("imageUrl","gender","description","notes"))if(f.has(field)&&!f.get(field).isTextual())throw bad("字段必须为文本，恢复来源请移除此字段");
        if(f.path("gender").asText().length()>40||f.path("description").asText().length()>4000||f.path("notes").asText().length()>2000)throw bad("字段长度超过限制");
        LocalGraphRules.image(f.path("imageUrl").asText());
        if(f.has("names")){if(!f.get("names").isArray()||f.get("names").size()>30)throw bad("名称必须为集合");var names=new ArrayList<NameResolutionService.LocalName>();
            for(var n:f.get("names"))names.add(new NameResolutionService.LocalName(n.path("id").asText(),n.path("text").asText(),n.path("locale").asText(),n.path("kind").asText(),"LOCAL","MANUAL"));
            NameResolutionService.validateLocal(names,f.path("preferredNameId").asText(null));
        }else if(f.has("preferredNameId"))throw bad("指定显示名称时需同时提供名称集合");
        if(input.portraits().size()>100)throw bad("每角色最多100个作品取景设置");
        input.portraits().forEach((scope,p)->{
            if(!scope.equals("GLOBAL")&&!scope.matches("anime:bangumi:[1-9][0-9]{0,9}"))throw bad("取景范围无效");
            validateCrop(p);
        });
        if(input.toString().length()>900000)throw bad("角色修改资料过大");
    }
    public static void validateCrop(JsonNode p) {
        if(p==null||!p.isObject())throw bad("取景资料格式无效");
        if(p.has("imageUrl")){if(!p.get("imageUrl").isTextual())throw bad("作品头像必须为图片地址或上传图片");LocalGraphRules.image(p.get("imageUrl").asText());}
            if(!Set.of("portrait","full").contains(p.path("mode").asText())||!p.path("imageFingerprint").asText().matches("sha256:[a-f0-9]{64}"))throw bad("取景模式或图片内容指纹无效");
            for(String key:List.of("focalX","focalY","scale")){double value=p.path(key).asDouble(-1);if(!p.path(key).isNumber()||!Double.isFinite(value)||value<(key.equals("scale")?1:0)||value>(key.equals("scale")?3:1))throw bad("取景参数无效");}
    }
    private static ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,"CHARACTER_EDIT_INVALID",message);}
}
