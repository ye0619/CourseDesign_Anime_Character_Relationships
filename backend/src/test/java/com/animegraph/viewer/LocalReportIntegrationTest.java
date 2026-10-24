package com.animegraph.viewer;

import com.animegraph.graph.GraphStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.neo4j.driver.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit, read-only five-work cross-check. No viewer binding, external requests or invented personal ratings. */
@EnabledIfSystemProperty(named="report.integration",matches="true")
class LocalReportIntegrationTest {
    @Test void fiveRealWorksAgreeWithIndependentDistinctWorkQuery() throws Exception {
        var json=new ObjectMapper().findAndRegisterModules();
        var ids=List.of(1424L,3774L,328609L,9717L,10380L);
        var uids=ids.stream().map(id->"anime:bangumi:"+id).toList();
        try(var driver=GraphDatabase.driver(System.getenv("NEO4J_URI"),
                AuthTokens.basic(System.getenv("NEO4J_USERNAME"),Objects.requireNonNull(System.getenv("NEO4J_PASSWORD"))))){
            var graph=new GraphStore(driver,System.getenv("NEO4J_DATABASE"),json);
            var input=new LocalReportCredits(graph,json).read(ids);
            assertEquals(5,input.size());
            var collections=ids.stream().map(id->RecommendationEngineTest.work(id,2,0,List.of())).toList();
            var report=ViewingReportAnalyzer.analyze(collections,input);
            var expected=graph.read("""
                CALL {
                  MATCH (p:Person {active:true})-[:PARTICIPATED_IN {active:true}]->(a:Anime {active:true})
                  WHERE a.uid IN $uids RETURN p,a
                  UNION
                  MATCH (p:Person {active:true})-[v:VOICED {active:true,scopeKnown:true}]->(c:Character {active:true})
                        -[:APPEARS_IN {active:true}]->(a:Anime {active:true})
                  WHERE a.uid IN $uids AND v.animeUid=a.uid RETURN p,a
                }
                RETURN p.uid AS uid,count(DISTINCT a) AS works,collect(DISTINCT a.uid) AS workUids
                ORDER BY uid LIMIT 10000
                """,Map.of("uids",uids));
            var byPerson=new HashMap<String,Map<String,Object>>();expected.forEach(row->byPerson.put((String)row.get("uid"),row));
            assertEquals(expected.size(),report.overallTotal());
            assertTrue(report.overallTotal()>0);
            for(var person:report.overall()){
                var row=byPerson.get(person.uid());assertNotNull(row);
                assertEquals(((Number)row.get("works")).intValue(),person.workCount());
                assertEquals(new TreeSet<>((List<?>)row.get("workUids")),
                        new TreeSet<>(person.works().stream().map(work->"anime:bangumi:"+work.subjectId()).toList()));
                assertEquals(0,person.ratedCount());assertNull(person.averageRating());
            }
            assertEquals(5,report.completed());assertEquals(0,report.metadataCovered());
            assertEquals(5,report.coverageSummary().staff().collection().get("NOT_VERIFIED"));
            String output=System.getProperty("report.acceptance.path");
            if(output!=null){
                var record=new LinkedHashMap<String,Object>();
                record.put("checkedAt",Instant.now());record.put("passed",true);record.put("subjects",ids);
                record.put("reportVersion",report.version());
                record.put("overallTotal",report.overallTotal());record.put("comparedPeople",report.overall().size());
                record.put("coverage",report.coverage());record.put("manualReviewPerformed",false);
                record.put("scope","Read-only real graph cross-check using a synthetic zero-rating collection; not a private account or human fact audit");
                Files.writeString(Path.of(output),json.writerWithDefaultPrettyPrinter().writeValueAsString(record));
            }
        }
    }
    @Test void savedPublicPagesRetainActualAnimationCompaniesAndScopedVoiceRoles() throws Exception {
        var json=new ObjectMapper().findAndRegisterModules();long id=373267;String uid="anime:bangumi:"+id;
        var sections=new HashMap<String,List<com.animegraph.explore.CreditModels.Page>>();
        try(var connection=java.sql.DriverManager.getConnection(System.getenv("MYSQL_URL"),System.getenv("MYSQL_USER"),System.getenv("MYSQL_PASSWORD"))) {
            connection.setReadOnly(true);
            try(var statement=connection.prepareStatement("SELECT kind,payload FROM public_credit_snapshot WHERE subject_id=?")) {
                statement.setLong(1,id);try(var rows=statement.executeQuery()){while(rows.next())sections.put(rows.getString(1),json.readValue(rows.getString(2),new com.fasterxml.jackson.core.type.TypeReference<List<com.animegraph.explore.CreditModels.Page>>(){}));}
            }
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(sections.containsKey("STAFF")&&sections.containsKey("CAST"),"Save public subject373267 before source acceptance");
        var staff=sections.get("STAFF");var cast=sections.get("CAST");
        var snapshot=new com.animegraph.explore.SubjectSnapshotService.Snapshot(id,
                new com.animegraph.explore.SubjectSnapshotService.Section("CAST",cast,0,cast.stream().mapToInt(page->page.coverage().returnedRows()).sum(),false,null,null),
                new com.animegraph.explore.SubjectSnapshotService.Section("STAFF",staff,0,staff.stream().mapToInt(page->page.coverage().returnedRows()).sum(),false,null,null),null);
        var report=ViewingReportAnalyzer.analyze(List.of(RecommendationEngineTest.work(id,2,0,List.of())),Map.of(id,ReportCredits.fromSnapshot(snapshot)));
        var expectedCompanies=new TreeSet<String>();var expectedCast=new TreeSet<String>();
        for(var page:staff){var people=new HashMap<String,com.animegraph.explore.CreditModels.Entity>();page.people().forEach(person->people.put(person.id(),person));
            for(var credit:page.staff()){var person=people.get(credit.personUid());if(person!=null&&Objects.equals(person.providerType(),2)&&credit.positions().stream().anyMatch(position->"动画制作".equals(position.label())))expectedCompanies.add(person.id());}}
        for(var page:cast){var people=new HashMap<String,com.animegraph.explore.CreditModels.Entity>();page.people().forEach(person->people.put(person.id(),person));var characters=new HashSet<>(page.characters().stream().map(com.animegraph.explore.CreditModels.Entity::id).toList());
            for(var credit:page.cast()){var person=people.get(credit.personUid());if(uid.equals(credit.subjectUid())&&"VOICE".equals(credit.performanceType())&&person!=null&&Objects.equals(person.providerType(),1)&&characters.contains(credit.characterUid()))expectedCast.add(person.id()+"|"+credit.characterUid());}}
        assertFalse(expectedCompanies.isEmpty());assertFalse(expectedCast.isEmpty());
        assertEquals(expectedCompanies,new TreeSet<>(report.companies().stream().map(ProfileDetails.Company::uid).toList()));
        var actualCast=new TreeSet<String>();report.voices().forEach(voice->voice.roles().forEach(role->actualCast.add(voice.uid()+"|"+role.characterUid())));
        assertEquals(expectedCast,actualCast);assertTrue(report.companies().stream().allMatch(company->company.completed()==1&&company.scores().ratedCount()==0&&company.scores().averageRating()==null));
        Files.writeString(Path.of("../.local/profile-source-integration.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "passed",true,"subjectId",id,"staffPages",staff.size(),"castPages",cast.size(),"companies",report.companies().stream().map(company->Map.of("uid",company.uid(),"name",company.name())).toList(),
                "voices",report.voices().size(),"scopedPerformances",actualCast.size(),"scope","Actual saved Bangumi public pages with a synthetic zero-rating collection; no private account read or mutation")));
    }
}
