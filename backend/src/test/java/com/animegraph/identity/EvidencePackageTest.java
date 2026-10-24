package com.animegraph.identity;
import com.animegraph.common.ApiException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvidencePackageTest {
    static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
    static ObjectNode input(){
        try{return (ObjectNode)JSON.readTree("""
          {"schemaVersion":1,"batchId":"review-1","worldUid":"universe:fixture:review","provider":"PROJECT",
           "sourceVersion":"test/1","licenseLabel":"CC0-1.0 (synthetic test)","retrievedAt":"2026-10-20T00:00:00Z",
           "facts":[{"source":"character:fixture:a","target":"character:fixture:b","relationType":"FRIEND",
             "ended":false,"spoiler":false,"scopeType":"GLOBAL","subjectIds":[],"reviewStatus":"REVIEWED",
             "sourceUrl":"https://example.org/synthetic","sourceRevision":"r1","sourceField":"test.friend",
             "rawRelation":"Synthetic only","sourceName":"Synthetic test","description":"Not a source fact",
             "attribution":"Test fixture","matchMethod":"MANUAL"}]}
          """);}catch(Exception e){throw new AssertionError(e);}
    }
    static ObjectNode row(ObjectNode root){return (ObjectNode)root.path("facts").get(0);}
    @Test void normalizesUndirectedAndDeduplicatesEvidence(){var first=input();((com.fasterxml.jackson.databind.node.ArrayNode)first.path("facts")).add(row(first).deepCopy());assertEquals(1,EvidencePackage.parse(first,JSON).facts().size());var reverse=input();row(reverse).put("source","character:fixture:b").put("target","character:fixture:a");assertEquals(EvidencePackage.parse(input(),JSON).facts(),EvidencePackage.parse(reverse,JSON).facts());}
    @Test void directedReversalsAndOpposingClaimsStayDistinct(){var a=input();row(a).put("relationType","MEMBER");var b=a.deepCopy();row(b).put("source","character:fixture:b").put("target","character:fixture:a");assertNotEquals(EvidencePackage.parse(a,JSON).facts().getFirst().uid(),EvidencePackage.parse(b,JSON).facts().getFirst().uid());row(b).put("relationType","RIVAL");assertNotEquals(EvidencePackage.parse(a,JSON).facts().getFirst().uid(),EvidencePackage.parse(b,JSON).facts().getFirst().uid());}
    @Test void explicitScopeHasSeparateIdentityAndRequiresSubjects(){var root=input();row(root).put("scopeType","SUBJECT_EXPLICIT");assertThrows(ApiException.class,()->EvidencePackage.parse(root,JSON));row(root).putArray("subjectIds").add(328609);assertNotEquals(EvidencePackage.parse(input(),JSON).facts().getFirst().uid(),EvidencePackage.parse(root,JSON).facts().getFirst().uid());row(root).put("scopeType","GLOBAL");assertThrows(ApiException.class,()->EvidencePackage.parse(root,JSON));}
    @Test void candidatesNamesUnknownFieldsAndUnsafeSourcesAreRejected(){for(String field:List.of("reviewStatus","source","sourceUrl")){var root=input();row(root).put(field,switch(field){case "reviewStatus"->"CANDIDATE";case "source"->"同名角色";default->"https://password@example.org/private";});assertThrows(ApiException.class,()->EvidencePackage.parse(root,JSON));}var root=input();root.put("accessToken","must-not-be-stored");assertThrows(ApiException.class,()->EvidencePackage.parse(root,JSON));var mismatch=input();mismatch.put("provider","MOEGIRL");assertThrows(ApiException.class,()->EvidencePackage.parse(mismatch,JSON));}
    @Test void sourceRevisionAndRawFieldAreMandatory(){for(String field:List.of("sourceRevision","sourceField","rawRelation","attribution")){var root=input();row(root).put(field,"");assertThrows(ApiException.class,()->EvidencePackage.parse(root,JSON));}}
    @Test void sameNameNeverDefinesIdentityAndLegacyGlobalKeyIsStable(){var p=EvidencePackage.parse(input(),JSON);var f=p.facts().getFirst();assertEquals("assertion:"+EvidencePackage.hash(JSON,List.of(f.source(),f.target(),f.relationType(),false,false,"GLOBAL")),f.uid());var other=input();row(other).put("source","character:fixture:different-person");assertNotEquals(f.uid(),EvidencePackage.parse(other,JSON).facts().getFirst().uid());}
    @Test void rowCountAndBooleansAreBounded(){var root=input();row(root).put("spoiler","false");assertThrows(ApiException.class,()->EvidencePackage.parse(root,JSON));var many=input();var rows=many.putArray("facts");for(int i=0;i<201;i++)rows.add(row(input()));assertThrows(ApiException.class,()->EvidencePackage.parse(many,JSON));}
}
