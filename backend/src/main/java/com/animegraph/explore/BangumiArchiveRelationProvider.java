package com.animegraph.explore;

import com.animegraph.charactergraph.CharacterGraphModels.CharacterEdge;
import com.animegraph.charactergraph.RelationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BangumiArchiveRelationProvider implements CharacterRelationProvider {
    private final Path root;
    private final ObjectMapper json;
    public BangumiArchiveRelationProvider(ObjectMapper json,@Value("${app.bangumi.archive-index:../data/index/bangumi}")String directory) {
        this.json=json;this.root=Path.of(directory).toAbsolutePath().normalize();
    }
    public Result relations(long subjectId,Set<String> characters) {
        if(!Files.exists(root.resolve("CURRENT")))return new Result("bangumi","NOT_FETCHED",null,"尚未导入官方Archive关系索引，可使用导入脚本补全。",List.of());
        try {
            String version=Files.readString(root.resolve("CURRENT")).strip();
            if(!version.matches("[0-9a-f]{64}"))throw new IllegalStateException("Invalid archive revision");
            Path bucket=root.resolve(version).resolve((subjectId%256)+".json");
            if(Files.size(bucket)>16_000_000)throw new IllegalStateException("Archive index exceeds limit");
            var subject=json.readTree(bucket.toFile()).path(String.valueOf(subjectId));
            if(subject.isMissingNode())return new Result("bangumi","NOT_IN_ARCHIVE",version,"该快照尚未收录本作角色映射，不能判断没有角色关系。",List.of());
            var edges=new LinkedHashMap<String,CharacterEdge>();int unknown=0;
            for(var row:subject.path("records")) {
                if(!row.path("person_type").asText().equals("crt"))continue;
                String owner="character:bangumi:"+row.path("person_id").asLong(),related="character:bangumi:"+row.path("related_person_id").asLong();
                if(!characters.contains(owner)||!characters.contains(related)||owner.equals(related))continue;
                int raw=row.path("relation_type").asInt();
                RelationType type=Arrays.stream(RelationType.values()).filter(t->Objects.equals(t.definition().bangumiId(),raw)).findFirst().orElse(null);
                if(type==null){unknown++;continue;}
                String from=related,to=owner;
                if(type==RelationType.CRUSH){from=owner;to=related;}
                if(Set.of("STUDENT","PET","SERVANT","SUBORDINATE","PROTOTYPE","PREDECESSOR").contains(type.name())) {
                    type=RelationType.valueOf(type.definition().inverseCode());String swap=from;from=to;to=swap;
                }
                boolean directed=type.directed()&&!Set.of(RelationType.MEMBER,RelationType.FOUNDER,RelationType.PILOT).contains(type);
                if(!directed&&from.compareTo(to)>0){String swap=from;from=to;to=swap;}
                String key="archive:"+subjectId+":"+from+":"+type.name()+":"+to;
                var old=edges.get(key);boolean spoiler=row.path("spoiler").asBoolean()||(old!=null&&old.spoiler());
                boolean ended=row.path("ended").asBoolean()||(old!=null&&old.ended());
                String evidence="原始记录："+row+"；Archive SHA256："+version;
                if(old!=null)evidence=old.evidence()+"；"+evidence;
                edges.put(key,new CharacterEdge(key,from,to,type,type.label(),"角色全局关联，双方出场于本作；不代表本季剧情时点。",evidence,"Bangumi 官方Archive",directed,spoiler,ended,"https://bgm.tv/character/"+row.path("person_id").asLong(),raw,version));
            }
            return new Result("bangumi",unknown>0||edges.isEmpty()&&!subject.path("records").isEmpty()?"PARTIAL":edges.isEmpty()?"EMPTY":"VERIFIED",version,
                    "官方归档的角色全局关联，仅展示当前已加载角色范围；不是本季独立剧情审核。"+(unknown>0?"有未识别关系类型，未擅自转换。":""),List.copyOf(edges.values()));
        } catch(Exception e) {
            return new Result("bangumi","FAILED",null,"归档索引读取失败，保留可用角色资料；请重新构建索引。",List.of());
        }
    }
}
