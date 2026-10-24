package com.animegraph.viewer;
import com.animegraph.explore.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ViewingReportServiceTest {
    @Test void deduplicatesVoiceRolesAndPreservesMissingRatingsAndCoverage(){var actor=new CreditModels.Entity("person:bangumi:1","配音员","","","PERSON",1,List.of(),List.of(),"v","url");var cast1=new CreditModels.CastCredit("c1","anime:bangumi:1",actor.id(),"char1",0,"原声","VOICE",null,null,null,null,null,"field");var cast2=new CreditModels.CastCredit("c2","anime:bangumi:1",actor.id(),"char2",0,"原声","VOICE",null,null,null,null,null,"field");var page=new CreditModels.Page("anime:bangumi:1","CAST",List.of(actor),List.of(),List.of(),List.of(cast1,cast2),new CreditModels.Coverage(1,60,2,2,2,0,0,0,null,true,false,"COMPLETE"),new CreditModels.Source("BANGUMI","url","field","v",Instant.now(),false,false,"v"),"v");var section=new SubjectSnapshotService.Section("CAST",List.of(page),2,2,true,null,null);var staff=new SubjectSnapshotService.Section("STAFF",List.of(),0,0,false,1,null);var result=ViewingReportService.analyze(List.of(RecommendationEngineTest.work(1,2,0,List.of()),RecommendationEngineTest.work(2,2,9,List.of())),List.of(new SubjectSnapshotService.Snapshot(1,section,staff,null)));assertEquals(1,result.personnel().size());assertEquals(1,result.personnel().getFirst().workCount());assertNull(result.personnel().getFirst().averageRating());assertNull(result.personnel().getFirst().adjustedRating());assertEquals(0,result.metadataCovered());assertEquals(2,result.completed());}
}
