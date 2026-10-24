package com.animegraph.explore;

import static com.animegraph.explore.CreditModels.*;
import com.animegraph.graph.GraphStore;
import com.animegraph.identity.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Offline adapter for public v0 responses already acquired by this project; no remote calls. */
@Service
public class SavedRawCredits {
    private final Path root;private final ObjectMapper json;private final NameResolutionService names;
    public SavedRawCredits(ObjectMapper json,NameResolutionService names,@Value("${app.bangumi.saved-raw:../data/raw/bangumi}")String root){this.json=json;this.names=names;this.root=Path.of(root).toAbsolutePath().normalize();}
    private Map<String,Instant> provenance() {
        var records=new HashMap<String,Instant>();
        for(String file:List.of("provenance.json","bangumi-curated-20-2026-10-23-provenance.json")) {
            var path=root.resolve(file);if(!Files.exists(path))continue;
            try {if(Files.size(path)>4_000_000)throw new IllegalStateException("Provenance file exceeds bound");
                for(var request:json.readTree(path.toFile()).path("requests"))if(request.path("status").asInt()==200){String url=request.path("url").asText();if(url.matches("https://api\\.bgm\\.tv/v0/subjects/[1-9][0-9]{0,9}(/characters|/persons)?")) {
                    String date=request.path("fetchedAt").asText(request.path("cacheWrittenAt").asText("1970-01-01T00:00:00Z"));Instant at=Instant.parse(date);records.merge(url,at,(a,b)->a.isAfter(b)?a:b);
                }}
            }catch(Exception e){throw new IllegalStateException("Cannot read saved public provenance",e);}
        }
        return records;
    }
    public List<Long> availableSubjects() {
        var ids=new TreeSet<Long>();for(String url:provenance().keySet())if(url.endsWith("/characters")){long id=Long.parseLong(url.split("/")[5]);if(Files.exists(root.resolve("v0-subjects-"+id+"-characters.json")))ids.add(id);}
        if(ids.size()>500)throw new IllegalStateException("Saved public source scope exceeds 500 works");return List.copyOf(ids);
    }
    public String title(long id){var source=document(id,"");if(source==null||source.path("type").asInt()!=2)return "作品 "+id;return names.resolve(new EntityIdentity(EntityIdentity.Kind.ANIME,id),source,Instant.EPOCH).displayName();}
    private JsonNode document(long id,String suffix) {
        if(id<1||id>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid subject");Path file=root.resolve("v0-subjects-"+id+suffix+".json");if(!Files.exists(file))return null;
        try {if(!file.toRealPath().startsWith(root.toRealPath())||Files.size(file)>16_000_000)throw new IllegalStateException("Saved source path or size invalid");return json.readTree(file.toFile());}catch(Exception e){throw new IllegalStateException("Invalid saved public source",e);}
    }
    public Page read(long id,String kind) {
        String suffix=kind.equals("CAST")?"/characters":"/persons",url="https://api.bgm.tv/v0/subjects/"+id+suffix;
        var at=provenance().get(url);if(at==null)return null;
        var subject=document(id,"");if(subject==null||subject.path("id").asLong()!=id||subject.path("type").asInt()!=2)return null;
        var data=document(id,kind.equals("CAST")?"-characters":"-persons");if(data==null||!data.isArray()||data.size()>6000)return null;
        var people=new LinkedHashMap<String,Entity>();var characters=new LinkedHashMap<String,Entity>();var staff=new ArrayList<StaffCredit>();var cast=new ArrayList<CastCredit>();int parsed=0,omitted=0;
        for(var row:data) {
            if(!row.path("id").canConvertToLong()||row.path("id").asLong()<1){omitted++;continue;}
            if(kind.equals("STAFF")) {var person=entity(row,false,at);people.put(person.id(),person);parsed++;String role=row.path("relation").asText();staff.add(new StaffCredit(person.id(),role.isBlank()?List.of():List.of(new Position("",role,row.path("eps").asText(null),null))));if(role.isBlank())omitted++;}
            else {var character=entity(row,true,at);characters.put(character.id(),character);parsed++;
                if(!row.path("actors").isArray()){omitted++;continue;}
                for(var actor:row.path("actors")) {
                    if(actor.path("id").asLong()<1||actor.path("type").asInt()!=1||!hasVoiceCareer(actor)){omitted++;continue;}
                    var person=entity(actor,false,at);people.put(person.id(),person);String key="saved-v0:"+id+":"+character.id()+":"+person.id();
                    cast.add(new CastCredit(key,"anime:bangumi:"+id,person.id(),character.id(),null,"v0作品角色配音记录","VOICE",null,null,null,null,null,"characters[].actors[]"));
                }
            }
        }
        String revision=GraphStore.hash(bytes(id,kind.equals("CAST")?"-characters":"-persons"));
        return new Page("anime:bangumi:"+id,kind,List.copyOf(people.values()),List.copyOf(characters.values()),List.copyOf(staff),List.copyOf(cast),
                new Coverage(1,Math.max(1,data.size()),data.size(),data.size(),parsed,data.size()-parsed,omitted,0,null,true,false,omitted==0?"COMPLETE":"PARTIAL"),
                new Source("BANGUMI_SAVED_V0",url,"saved-v0-array","saved-v0/1",at,true,true,names.policyVersion()),revision);
    }
    private byte[] bytes(long id,String suffix){try{return Files.readAllBytes(root.resolve("v0-subjects-"+id+suffix+".json"));}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean hasVoiceCareer(JsonNode actor){for(var career:actor.path("career"))if(career.asText().equals("seiyu"))return true;return false;}
    private Entity entity(JsonNode source,boolean character,Instant at) {
        var identity=new EntityIdentity(character?EntityIdentity.Kind.CHARACTER:EntityIdentity.Kind.PERSON,source.path("id").asLong());var n=names.resolve(identity,source,at);String image=source.path("images").path("medium").asText("");
        return new Entity(identity.canonicalUid(),n.displayName(),n.originalName(),image.matches("https://lain\\.bgm\\.tv/[^\\s]+")?image:"",character?"CHARACTER":"PERSON",source.path("type").isIntegralNumber()?source.path("type").asInt():null,n.aliases(),n.assertions(),n.policyVersion(),identity.sourceUrl());
    }
    public SubjectSnapshotService.Snapshot snapshot(long id) {
        var cast=read(id,"CAST");var staff=read(id,"STAFF");if(cast==null&&staff==null)return null;
        return new SubjectSnapshotService.Snapshot(id,SubjectSnapshotService.section("CAST",cast==null?List.of():List.of(cast),null),SubjectSnapshotService.section("STAFF",staff==null?List.of():List.of(staff),null),null);
    }
}
