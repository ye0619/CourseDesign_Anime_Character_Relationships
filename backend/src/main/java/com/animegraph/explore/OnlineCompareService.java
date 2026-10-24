package com.animegraph.explore;

import com.animegraph.common.ApiException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OnlineCompareService {
    public record Side(long subjectId,Map<String,Object> entity,String error,SubjectSnapshotService.Snapshot metadata) {}
    public record CommonItem(CreditModels.Entity entity,List<CreditModels.Position> rolesA,List<CreditModels.Position> rolesB,List<CreditModels.CastCredit> castA,List<CreditModels.CastCredit> castB) {}
    public record Group(String kind,List<CommonItem> items,boolean complete,boolean truncated,String notice) {}
    public record Comparison(Side a,Side b,List<Group> groups,List<String> sharedTags,Double tagSimilarity,String methodology) {}
    private final BangumiAnimeService anime;
    private final SubjectSnapshotService snapshots;
    public OnlineCompareService(BangumiAnimeService anime,SubjectSnapshotService snapshots){this.anime=anime;this.snapshots=snapshots;}
    public Comparison compare(long a,long b) {
        if(a<1||b<1||a>Integer.MAX_VALUE||b>Integer.MAX_VALUE)throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","请选择有效动画作品");
        Side left=side(a),right=side(b);
        var tagsA=tags(left.entity());var tagsB=tags(right.entity());var shared=new TreeSet<>(tagsA);shared.retainAll(tagsB);
        var all=new HashSet<>(tagsA);all.addAll(tagsB);
        Double similarity=tagsA.isEmpty()||tagsB.isEmpty()?null:Math.round(10000.0*shared.size()/all.size())/100.0;
        return new Comparison(left,right,List.of(group("STAFF",left.metadata().staff(),right.metadata().staff()),group("VOICE",left.metadata().cast(),right.metadata().cast()),group("CHARACTER",left.metadata().cast(),right.metadata().cast())),List.copyOf(shared),similarity,"按稳定实体ID比较；已读交集是下界。标签相似度=共有标签数/两侧标签并集，仅描述来源现有标签，不表示作品优劣。");
    }
    private Side side(long id) {
        Map<String,Object> entity=Map.of();String error=null;
        try{entity=anime.detail(id).data();}catch(ApiException e){error=e.getMessage();}
        return new Side(id,entity,error,snapshots.read(id));
    }
    @SuppressWarnings("unchecked") private Set<String> tags(Map<String,Object> entity) {return new LinkedHashSet<>((List<String>)entity.getOrDefault("tags",List.of()));}
    static Group group(String kind,SubjectSnapshotService.Section a,SubjectSnapshotService.Section b) {
        var peopleA=entities(a,kind.equals("CHARACTER"));var peopleB=entities(b,kind.equals("CHARACTER"));
        Set<String> idsA=members(a,kind),idsB=members(b,kind);idsA.retainAll(idsB);
        var result=new ArrayList<CommonItem>();
        for(String id:new TreeSet<>(idsA)) {
            if(!peopleA.containsKey(id)||!peopleB.containsKey(id))continue;
            result.add(new CommonItem(peopleA.get(id),roles(a,id),roles(b,id),casts(a,id),casts(b,id)));
        }
        boolean complete=a.complete()&&b.complete();
        return new Group(kind,result.stream().limit(500).toList(),complete,result.size()>500,complete?"两侧已读完来源当前名单；来源跨页可能更新":"尚不能完整比较：当前仅展示已读取分页中的共同项");
    }
    private static Map<String,CreditModels.Entity> entities(SubjectSnapshotService.Section s,boolean characters) {var result=new LinkedHashMap<String,CreditModels.Entity>();for(var p:s.pages())for(var e:characters?p.characters():p.people())result.put(e.id(),e);return result;}
    private static Set<String> members(SubjectSnapshotService.Section s,String kind) {
        Set<String> ids=new HashSet<>();var entities=entities(s,false);
        for(var p:s.pages())switch(kind) {
            case "STAFF" -> p.staff().forEach(c->ids.add(c.personUid()));
            case "VOICE" -> p.cast().stream().filter(c->c.performanceType().equals("VOICE")&&entities.containsKey(c.personUid())&&Objects.equals(entities.get(c.personUid()).providerType(),1)).forEach(c->ids.add(c.personUid()));
            case "CHARACTER" -> p.characters().forEach(c->ids.add(c.id()));
            default -> throw new IllegalArgumentException("Unsupported comparison group");
        }
        return ids;
    }
    private static List<CreditModels.Position> roles(SubjectSnapshotService.Section s,String id) {return s.pages().stream().flatMap(p->p.staff().stream()).filter(c->c.personUid().equals(id)).flatMap(c->c.positions().stream()).distinct().toList();}
    private static List<CreditModels.CastCredit> casts(SubjectSnapshotService.Section s,String id) {return s.pages().stream().flatMap(p->p.cast().stream()).filter(c->c.personUid().equals(id)||c.characterUid().equals(id)).distinct().toList();}
}
