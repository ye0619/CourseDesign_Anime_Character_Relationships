package com.animegraph.connection;

import static com.animegraph.connection.ConnectionModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/** Durable immutable revision; publication and typed graph projection commit together. */
@Repository
public class ConnectionProjectionRepository {
    private final Driver driver; private final String database; private final ObjectMapper json;
    public ConnectionProjectionRepository(Driver driver,@Value("${app.neo4j.database}") String database,ObjectMapper json){this.driver=driver;this.database=database;this.json=json;}
    public Projection active() {
        try(var session=driver.session(SessionConfig.forDatabase(database))) {
            return session.executeRead(tx->{var rows=tx.run("MATCH (:ConnectionActive {uid:'active'})-[:CURRENT]->(r:ConnectionRevision) RETURN r.payload AS payload");
                return rows.hasNext()?decode(rows.single().get("payload").asString()):null;},TransactionConfig.builder().withTimeout(Duration.ofSeconds(5)).build());
        }
    }
    public void publish(Projection projection) {
        String payload;try{payload=json.writeValueAsString(projection);}catch(Exception e){throw new IllegalStateException(e);}
        if(payload.length()>16*1024*1024)throw new IllegalStateException("资料投影超过16MB，上一版本保留");
        try(var session=driver.session(SessionConfig.forDatabase(database))) {
            session.executeWrite(tx->{
                var lock=tx.run("MERGE (a:ConnectionActive {uid:'active'}) SET a.lock=coalesce(a.lock,0)+1 RETURN a.lock");lock.consume();
                tx.run("CREATE (:ConnectionRevision {uid:$revision,payload:$payload,builtAt:$at})",Map.of("revision",projection.revision(),"payload",payload,"at",projection.builtAt().toString())).consume();
                for(String kind:List.of("CHARACTER","VOICE_ACTOR")) {
                    var rows=projection.nodes().stream().filter(n->n.kind().equals(kind)).map(n->Map.<String,Object>of("uid",n.uid(),"name",n.name(),"names",n.names())).toList();
                    String label=kind.equals("CHARACTER")?"ConnectionCharacter":"ConnectionVoiceActor";
                    tx.run("UNWIND $rows AS row MERGE (n:"+label+" {canonicalUid:row.uid}) SET n.name=row.name,n.names=row.names",Map.of("rows",rows)).consume();
                }
                for(int offset=0;offset<projection.facts().size();offset+=500) {
                    var chunk=projection.facts().subList(offset,Math.min(offset+500,projection.facts().size()));
                    for(String kind:List.of("VOICE","STORY")) {
                        var rows=chunk.stream().filter(f->f.kind().equals(kind)).map(f->Map.<String,Object>of("uid",f.uid(),"source",f.source(),"target",f.target(),"subject",f.subjectId())).toList();
                        String from=kind.equals("VOICE")?"ConnectionVoiceActor":"ConnectionCharacter",type=kind.equals("VOICE")?"CONNECTION_VOICE":"CONNECTION_STORY";
                        tx.run("UNWIND $rows AS row MATCH (a:"+from+" {canonicalUid:row.source}),(b:ConnectionCharacter {canonicalUid:row.target}) CREATE (a)-[:"+type+" {factUid:row.uid,revision:$revision,subjectId:row.subject}]->(b)",Map.of("rows",rows,"revision",projection.revision())).consume();
                    }
                }
                tx.run("MATCH (a:ConnectionActive {uid:'active'}) OPTIONAL MATCH (a)-[old:CURRENT]->() DELETE old WITH DISTINCT a MATCH (r:ConnectionRevision {uid:$revision}) CREATE (a)-[:CURRENT]->(r)",Map.of("revision",projection.revision())).consume();
                // Source graph and user edits are never deleted. Search tasks hold immutable Java snapshots.
                tx.run("MATCH (r:ConnectionRevision) WITH r ORDER BY r.builtAt DESC,r.uid DESC SKIP 2 WITH collect(r.uid) AS obsolete MATCH ()-[e:CONNECTION_VOICE|CONNECTION_STORY]->() WHERE e.revision IN obsolete DELETE e").consume();
                tx.run("MATCH (r:ConnectionRevision) WITH r ORDER BY r.builtAt DESC,r.uid DESC SKIP 2 DETACH DELETE r").consume();
                return null;
            },TransactionConfig.builder().withTimeout(Duration.ofSeconds(30)).build());
        }
    }
    private Projection decode(String payload){try{return json.readValue(payload,Projection.class);}catch(Exception e){throw new IllegalStateException("Invalid connection revision",e);}}
}
