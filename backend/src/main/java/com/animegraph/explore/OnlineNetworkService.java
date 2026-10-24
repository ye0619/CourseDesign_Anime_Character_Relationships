package com.animegraph.explore;

import com.animegraph.charactergraph.RelationType;
import com.animegraph.common.ApiException;
import com.animegraph.viewer.BangumiClient;
import com.animegraph.identity.EntityIdentity;
import com.animegraph.identity.NameResolutionService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Public, read-only online projection. Never reads or writes the local graph. */
@Service
public class OnlineNetworkService {
    public record Node(String id,String label,String originalName,String imageUrl,String role,String group,
                       String description,Integer characterType,String category,List<String> roles,String sourceUrl,
                       List<String> aliases,List<NameResolutionService.NameAssertion> nameAssertions,String namePolicyVersion) {}
    public record Edge(String id,String source,String target,String relationType,String label,boolean directed,
                       boolean spoiler,boolean ended,String description,String evidence,String sourceName,String sourceUrl) {}
    public record Meta(Instant fetchedAt,boolean cached,boolean stale,boolean truncated,String notice) {}
    public record Network(long subjectId,String kind,List<Node> nodes,List<Edge> edges,int page,int pageSize,int total,Meta meta) {}
    public record Relations(String characterId,List<Node> nodes,List<Edge> edges,Meta meta) {}
    private final BangumiAnimeService anime;
    private final NameResolutionService names;
    private final NetworkSourceCache sources;
    @org.springframework.beans.factory.annotation.Autowired
    public OnlineNetworkService(NetworkSourceCache sources,BangumiAnimeService anime,NameResolutionService names) {this.sources=sources;this.anime=anime;this.names=names;}
    OnlineNetworkService(BangumiClient client,BangumiAnimeService anime) {this(new NetworkSourceCache(client),anime,new NameResolutionService(new com.fasterxml.jackson.databind.ObjectMapper()));}
    OnlineNetworkService(BangumiClient client,BangumiAnimeService anime,Clock clock) {this(new NetworkSourceCache(client,clock),anime,new NameResolutionService(new com.fasterxml.jackson.databind.ObjectMapper()));}
    public Network network(long id,String kind,int page) {
        validateId(id);
        if(!Set.of("CHARACTERS","STAFF").contains(kind) || page<1 || page>4) throw bad("图谱类型或页码不正确（最多240个条目）");
        anime.detail(id); // validates that an arbitrary public subject really is an anime
        String path="/p1/subjects/"+id+"/"+(kind.equals("CHARACTERS")?"characters":"staffs/persons")+"?limit=60&offset="+((page-1)*60);
        NetworkSourceCache.Snapshot snapshot=sources.fetch(path);JsonNode response=snapshot.data();
        var nodes=new LinkedHashMap<String,Node>();var edges=new LinkedHashMap<String,Edge>();
        for(var row:response.path("data")) {
            if(kind.equals("CHARACTERS")) {
                var item=row.path("character");if(!valid(item)) continue;
                Node node=node(item,"CHARACTER",role(row.path("type").asInt()),List.of(),snapshot.fetchedAt());nodes.put(node.id(),node);
            } else {
                var item=row.path("staff");if(!valid(item)) continue;
                var roles=new LinkedHashSet<String>();
                for(var position:row.path("positions")) {String name=text(position.path("type"),"cn",80);if(!name.isBlank()) roles.add(name);}
                Node person=node(item,"PERSON",roles.stream().findFirst().orElse("制作人员"),List.copyOf(roles),snapshot.fetchedAt());nodes.put(person.id(),person);
                for(var position:row.path("positions")) {
                    var type=position.path("type");String name=text(type,"cn",80);int positionId=type.path("id").asInt();if(positionId<1 || name.isBlank()) continue;
                    String target="position:bangumi:"+positionId;
                    nodes.putIfAbsent(target,new Node(target,name,"","", "职务","", "本作制作职务",4,"POSITION",List.of(),"https://bgm.tv/subject/"+id,List.of(),List.of(),""));
                    String key=person.id()+":"+target;
                    String description=person.label()+"在本作担任"+name+"。";
                    String episodes=text(position,"appearEps",200),summary=text(position,"summary",500);
                    if(!episodes.isBlank()) description+=" 参与集数："+episodes;
                    if(!summary.isBlank()) description+=" "+summary;
                    edges.put(key,new Edge(key,person.id(),target,"OTHER",name,true,false,false,description,"本作制作职务列表","Bangumi 在线","https://bgm.tv/subject/"+id));
                }
            }
        }
        return new Network(id,kind,List.copyOf(nodes.values()),List.copyOf(edges.values()),page,60,response.path("total").asInt(),meta(snapshot,response.path("total").asInt()>240,"角色与制作人员分开展示；关系来自 Bangumi 在线资料，不依赖本地档案。"));
    }

    public Relations relations(long id) {
        validateId(id);NetworkSourceCache.Snapshot snapshot=sources.fetch("/p1/characters/"+id+"/relations?limit=100&offset=0");
        var nodes=new LinkedHashMap<String,Node>();var edges=new LinkedHashMap<String,Edge>();
        for(var row:snapshot.data().path("data")) {
            var item=row.path("character");if(!valid(item) || item.path("id").asLong()==id) continue;
            Node target=node(item,"CHARACTER","关联角色",List.of(),snapshot.fetchedAt());nodes.put(target.id(),target);
            JsonNode relation=row.path("relation");String label=text(relation,"cn",80);if(label.isBlank()) continue;
            RelationType type=Arrays.stream(RelationType.values()).filter(t -> Objects.equals(t.definition().bangumiId(),relation.path("id").asInt())).findFirst().orElse(RelationType.OTHER);
            // The API label describes the related character relative to the page owner.
            String source=target.id(),destination="character:bangumi:"+id;
            // 单恋 is explicitly unilateral: the page owner admires the related character.
            if(type==RelationType.CRUSH) {String swap=source;source=destination;destination=swap;}
            if(type.definition().inverseCode()!=null && Set.of("STUDENT","PET","SERVANT","SUBORDINATE","PROTOTYPE","PREDECESSOR").contains(type.name())) {
                type=RelationType.valueOf(type.definition().inverseCode());String swap=source;source=destination;destination=swap;label=type.label();
            }
            // These non-inverse categories are returned with the same label from
            // both pages. Keep an undirected association instead of claiming
            // that an organization is itself a member of a human character.
            boolean directed=type.directed() && !Set.of(RelationType.MEMBER,RelationType.FOUNDER,RelationType.PILOT).contains(type);
            if(!directed && source.compareTo(destination)>0) {String swap=source;source=destination;destination=swap;}
            String key=source+":"+type.name()+":"+destination+":"+label;
            String evidence="Bangumi角色页的关联角色；关系可能涉及其他季或原作";
            if(type.directed() && !directed) evidence+="。该类关联在来源两端使用相同名称，保留无向连线，不推断成员或职能方向";
            edges.put(key,new Edge(key,source,destination,type.name(),label,directed,row.path("spoiler").asBoolean(),row.path("ended").asBoolean(),text(row,"comment",2000),evidence,"Bangumi 在线","https://bgm.tv/character/"+id));
        }
        int total=snapshot.data().path("total").asInt();
        return new Relations("character:bangumi:"+id,List.copyOf(nodes.values()),List.copyOf(edges.values()),meta(snapshot,total>100,"仅返回来源明确列出的关系；箭头起点是终点的所标身份。"));
    }

    private Meta meta(NetworkSourceCache.Snapshot value,boolean truncated,String notice) {return new Meta(value.fetchedAt(),value.cached(),value.stale(),truncated,notice);}
    private Node node(JsonNode item,String category,String role,List<String> roles,Instant at) {
        long id=item.path("id").asLong();
        var identity=new EntityIdentity(category.equals("CHARACTER")?EntityIdentity.Kind.CHARACTER:EntityIdentity.Kind.PERSON,id);
        var resolved=names.resolve(identity,item,at);
        String image=text(item.path("images"),"medium",1000);if(!image.matches("https://lain\\.bgm\\.tv/[^\\s]+")) image="";
        return new Node(identity.canonicalUid(),resolved.displayName(),resolved.originalName(),image,role,"",text(item,"info",2000),category.equals("CHARACTER")?item.path("role").asInt(1):1,category,roles,identity.sourceUrl(),resolved.aliases(),resolved.assertions(),resolved.policyVersion());
    }
    private static boolean valid(JsonNode item) {return item.path("id").asLong()>0 && item.path("id").asLong()<=Integer.MAX_VALUE;}
    private static String text(JsonNode item,String field,int max) {String value=item.path(field).asText("");return value.substring(0,Math.min(max,value.length()));}
    private static String role(int type) {return switch(type){case 1 -> "主角";case 2 -> "配角";case 3 -> "客串";case 4 -> "闲角";default -> "未标注";};}
    private static void validateId(long id) {if(id<1 || id>Integer.MAX_VALUE) throw bad("Bangumi编号超出范围");}
    private static ApiException bad(String message) {return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message);}
    private static ApiException unavailable() {return new ApiException(HttpStatus.BAD_GATEWAY,"NETWORK_UNAVAILABLE","暂时无法读取在线关系，请重试");}
}
