package com.animegraph.connection;

import static com.animegraph.connection.ConnectionModels.*;
import com.animegraph.explore.*;
import com.animegraph.localgraph.LocalGraphRepository;
import com.animegraph.graph.GraphStore;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class SavedConnectionProvider implements VoiceCreditProvider, CharacterStoryRelationProvider {
    private final ConnectionSourceMapper sources;
    private final SubjectSnapshotService snapshots;
    private final SavedAnimeService saved;
    private final CharacterRelationProvider stories;
    private final LocalGraphRepository local;
    private final GraphStore graph;
    @org.springframework.beans.factory.annotation.Autowired private SavedRawCredits raw;
    public SavedConnectionProvider(ConnectionSourceMapper sources,SubjectSnapshotService snapshots,SavedAnimeService saved,
                                   CharacterRelationProvider stories,LocalGraphRepository local,GraphStore graph) {
        this.sources=sources;this.snapshots=snapshots;this.saved=saved;this.stories=stories;this.local=local;this.graph=graph;
    }
    public List<Long> availableSubjects(){var ids=new TreeSet<>(sources.subjects());ids.addAll(raw.availableSubjects());return List.copyOf(ids);}
    public List<VoiceCreditProvider.Work> read(List<Long> ids) {
        var inputs=snapshots.readMany(ids);var result=new ArrayList<VoiceCreditProvider.Work>();
        for(long id:ids) {
            var payload=saved.read(id);var snapshot=inputs.get(id);
            var pages=snapshot!=null&&!snapshot.cast().pages().isEmpty()?snapshot.cast().pages():payload==null?List.<CreditModels.Page>of():payload.cast();
            if(pages.isEmpty()){var page=raw.read(id,"CAST");if(page!=null)pages=List.of(page);}
            String title=payload==null?raw.title(id):Objects.toString(payload.entity().get("nameCn"),"");
            if(title.isBlank())title=Objects.toString(payload.entity().get("name"),"作品 "+id);
            result.add(new VoiceCreditProvider.Work(id,title,pages));
        }
        return List.copyOf(result);
    }
    public List<Node> localCharacters(long subjectId) {
        return local.read("anime:bangumi:"+subjectId).nodes().stream().filter(n->n.kind().equals("CHARACTER")).map(n->{var aliases=new LinkedHashSet<>(n.aliases());aliases.add(n.name());if(n.names()!=null)n.names().forEach(name->aliases.add(name.text()));return new Node(n.id(),"CHARACTER",n.name(),n.imageUrl(),List.copyOf(aliases));}).toList();
    }
    public Stories read(long id,Set<String> ids) {
        var result=stories.relations(id,ids); var facts=new ArrayList<Fact>();
        for(var e:result.edges()) if(ids.contains(e.source())&&ids.contains(e.target())&&!Set.of("MEMBER","FOUNDER").contains(e.relationType().name())) {
            var evidence=new Evidence(result.source(),e.id(),e.sourceUrl(),"crt_relation",result.revision(),null,"SOURCE",e.evidence());
            facts.add(new Fact(e.id(),"STORY",e.source(),e.target(),id,e.relationType().name(),e.directed(),e.spoiler(),e.ended(),false,null,null,null,"GLOBAL_SOURCE_PROJECTED",List.of(evidence)));
        }
        // Saved story records are not restricted by the 200-node display limit.
        var rows=graph.read("""
            MATCH (a:Character {active:true})-[r:CHARACTER_RELATION {active:true}]->(b:Character {active:true})
            WHERE r.animeUid=$work AND a.uid IN $ids AND b.uid IN $ids
            RETURN a.uid AS source,b.uid AS target,properties(r) AS fact ORDER BY r.uid LIMIT 50001
            """,Map.of("work","anime:bangumi:"+id,"ids",List.copyOf(ids)));
        if(rows.size()>50000)throw new IllegalStateException("单作剧情事实超过构建上限，请缩小资料范围");
        for(var row:rows) {
            var r=GraphStore.castMap(row.get("fact"));String semantic=text(r,"relationCode");
            if(semantic.isBlank()||Set.of("MEMBER","FOUNDER").contains(semantic)||"AI_CANDIDATE".equals(text(r,"reviewStatus"))||"DISPUTED".equals(text(r,"reviewStatus")))continue;
            var evidence=new Evidence(text(r,"sourceName"),text(r,"uid"),text(r,"sourceUrl"),"CHARACTER_RELATION",text(r,"archiveRevision"),text(r,"updatedAt"),"SOURCE",text(r,"evidence"));
            facts.add(new Fact(text(r,"uid"),"STORY",text(row,"source"),text(row,"target"),id,semantic,Boolean.TRUE.equals(r.get("directed")),Boolean.TRUE.equals(r.get("spoiler")),Boolean.TRUE.equals(r.get("ended")),false,null,text(r,"versionLabel"),text(r,"episodeScope"),"SAVED_WORK_SCOPE",List.of(evidence)));
        }
        var document=local.read("anime:bangumi:"+id);
        for(var e:document.edges()) if(ids.contains(e.source())&&ids.contains(e.target())&&!e.relationType().equals("MEMBER"))
            facts.add(new Fact(e.id(),"STORY",e.source(),e.target(),id,e.relationType()+":"+e.label(),e.directed(),e.spoiler(),false,true,null,null,null,"MANUAL_WORK_SCOPE",
                    List.of(new Evidence("LOCAL",e.id(),null,"local-graph",String.valueOf(document.version()),null,"MANUAL",e.evidence()))));
        return new Stories(result.status(),List.copyOf(facts));
    }
    private static String text(Map<String,Object> row,String key){return Objects.toString(row.get(key),"");}
}
