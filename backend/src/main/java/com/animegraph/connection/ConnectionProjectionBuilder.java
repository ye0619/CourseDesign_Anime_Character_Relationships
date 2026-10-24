package com.animegraph.connection;

import static com.animegraph.connection.ConnectionModels.*;
import com.animegraph.explore.*;
import com.animegraph.graph.GraphStore;
import com.animegraph.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Stable source UIDs are the identity mapping. No name-based identity merges. */
@Service
public class ConnectionProjectionBuilder {
    private final VoiceCreditProvider voices; private final CharacterStoryRelationProvider stories;
    public ConnectionProjectionBuilder(VoiceCreditProvider voices,CharacterStoryRelationProvider stories){this.voices=voices;this.stories=stories;}
    public Projection build(List<Long> requested,BooleanSupplier cancelled,IntConsumer progress) {
        var ids=requested==null||requested.isEmpty()?voices.availableSubjects():requested.stream().distinct().sorted().toList();
        if(ids.size()>500)throw new ApiException(HttpStatus.BAD_REQUEST,"COVERAGE_LIMIT","本次最多选入500部已有资料的作品，请明确选择范围");
        var nodes=new TreeMap<String,Node>();var facts=new TreeMap<String,Fact>();var appearances=new LinkedHashSet<Endpoint>();var scope=new ArrayList<Subject>();int processed=0;
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
        for(var subjectId:ids)for(var work:voices.read(List.of(subjectId))) {
            if(System.nanoTime()>deadline)throw new ApiException(HttpStatus.REQUEST_TIMEOUT,"PROJECTION_TIMEOUT","本轮构建达到120秒上限，上一版本保留；请缩小范围后重试");
            if(cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException();
            var roster=new HashSet<String>();int voiceCount=0;
            for(var node:voices.localCharacters(work.subjectId())){nodes.put(node.uid(),node);roster.add(node.uid());appearances.add(new Endpoint(node.uid(),work.subjectId()));}
            for(var page:work.pages()) {
                if(!page.kind().equals("CAST")||!page.subjectUid().equals("anime:bangumi:"+work.subjectId()))continue;
                var characters=new HashMap<String,CreditModels.Entity>();var people=new HashMap<String,CreditModels.Entity>();
                page.characters().stream().filter(c->Objects.equals(c.providerType(),1)).forEach(c->{characters.put(c.id(),c);nodes.putIfAbsent(c.id(),node(c,"CHARACTER"));roster.add(c.id());appearances.add(new Endpoint(c.id(),work.subjectId()));});
                page.people().stream().filter(p->Objects.equals(p.providerType(),1)).forEach(p->people.put(p.id(),p));
                for(var credit:page.cast()) {
                    if(!"VOICE".equals(credit.performanceType())||!page.subjectUid().equals(credit.subjectUid())||!characters.containsKey(credit.characterUid())||!people.containsKey(credit.personUid()))continue;
                    var person=people.get(credit.personUid());nodes.putIfAbsent(person.id(),node(person,"VOICE_ACTOR"));
                    var source=page.source();var evidence=new Evidence(source.provider(),credit.id(),source.sourceUrl(),credit.sourceField(),page.snapshotVersion(),source.fetchedAt()==null?null:source.fetchedAt().toString(),"SOURCE",credit.sourceRelationLabel());
                    merge(facts,new Fact(credit.id(),"VOICE",person.id(),credit.characterUid(),work.subjectId(),"VOICE",true,false,false,false,clean(credit.language()),clean(credit.version()),clean(credit.episodeScope()),"WORK_CREDIT",List.of(evidence)));voiceCount++;
                }
            }
            var story=stories.read(work.subjectId(),Set.copyOf(roster));story.facts().forEach(f->merge(facts,f));
            scope.add(new Subject(work.subjectId(),work.title(),work.pages().isEmpty()?"NOT_FETCHED":SubjectSnapshotService.collected("CAST",work.pages())?"COMPLETE":"PARTIAL",story.status(),roster.size(),(int)facts.values().stream().filter(f->f.subjectId()==work.subjectId()&&f.kind().equals("VOICE")).count(),(int)facts.values().stream().filter(f->f.subjectId()==work.subjectId()&&f.kind().equals("STORY")).count()));
            if(nodes.size()>10000||facts.size()>50000)throw new ApiException(HttpStatus.BAD_REQUEST,"PROJECTION_LIMIT","本次超过10000个实体或50000条事实，请缩小范围；上一资料版本保留");
            progress.accept(++processed);
        }
        return new Projection(UUID.randomUUID().toString(),Instant.now(),List.copyOf(nodes.values()),List.copyOf(facts.values()),List.copyOf(appearances),List.copyOf(scope),"connections/1");
    }
    private static String clean(String text){return text==null||text.isBlank()?null:text.strip();}
    private static Node node(CreditModels.Entity e,String kind) {
        var names=new LinkedHashSet<String>();names.add(e.label());if(e.originalName()!=null)names.add(e.originalName());if(e.aliases()!=null)names.addAll(e.aliases());
        return new Node(e.id(),kind,e.label(),e.imageUrl(),names.stream().filter(n->n!=null&&!n.isBlank()).toList());
    }
    static void merge(Map<String,Fact> facts,Fact f) {
        String a=f.source(),b=f.target();if(!f.directed()&&a.compareTo(b)>0){String swap=a;a=b;b=swap;}
        String key=GraphStore.hash(String.join("|",f.kind(),a,b,String.valueOf(f.subjectId()),f.semantic(),String.valueOf(f.directed()),String.valueOf(f.spoiler()),String.valueOf(f.ended()),String.valueOf(f.manual()),Objects.toString(f.language(),""),Objects.toString(f.version(),""),Objects.toString(f.episodeScope(),"")).getBytes(StandardCharsets.UTF_8));
        var old=facts.get(key);var evidence=new LinkedHashSet<Evidence>();if(old!=null)evidence.addAll(old.evidence());evidence.addAll(f.evidence());
        facts.put(key,new Fact(key,f.kind(),a,b,f.subjectId(),f.semantic(),f.directed(),f.spoiler(),f.ended(),f.manual(),f.language(),f.version(),f.episodeScope(),old==null?f.scope():old.scope(),List.copyOf(evidence)));
    }
}
