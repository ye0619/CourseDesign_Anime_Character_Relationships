package com.animegraph.viewer;

import com.animegraph.explore.*;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfileDetailsTest {
    private ReportCredits.Work input(long id,List<ReportCredits.Performance> roles,List<ReportCredits.Studio> studios) {
        return new ReportCredits.Work(id,"PUBLIC_SNAPSHOT","v",ReportCredits.missing(),ReportCredits.missing(),List.of(),roles,studios);
    }
    private ReportCredits.Performance role(String actor,String character) {
        return new ReportCredits.Performance(actor,"演员","",character,"角色","");
    }
    @Test void voicesCountDistinctWorksAndCharactersButKeepEveryScopedPerformance() {
        var works=List.of(RecommendationEngineTest.work(1,2,8,List.of()),RecommendationEngineTest.work(2,2,0,List.of()));
        var result=ViewingReportAnalyzer.analyze(works,Map.of(1L,input(1,List.of(role("p1","c1"),role("p1","c2"),role("p1","c1"),role("p2","c1")),List.of()),
                2L,input(2,List.of(role("p1","c1")),List.of())));
        var first=result.voices().stream().filter(v->v.uid().equals("p1")).findFirst().orElseThrow();
        assertEquals(2,first.workCount());assertEquals(2,first.characterCount());assertEquals(3,first.roles().size());
        assertEquals(1,first.scores().ratedCount());assertEquals(8.0,first.scores().averageRating());
        assertEquals(1,result.voices().stream().filter(v->v.uid().equals("p2")).findFirst().orElseThrow().workCount());
    }
    @Test void behaviorKeepsStatusesSeparateAndDoesNotTreatMissingScoresAsZero() {
        var works=List.of(RecommendationEngineTest.work(1,2,8,List.of("恋爱")),RecommendationEngineTest.work(2,4,0,List.of("恋爱")),
                RecommendationEngineTest.work(3,5,3,List.of("恋爱")),RecommendationEngineTest.work(4,1,0,List.of()),RecommendationEngineTest.work(5,3,0,List.of()));
        var studio=new ReportCredits.Studio("s","公司","");
        var report=ViewingReportAnalyzer.analyze(works,Map.of(1L,input(1,List.of(),List.of(studio,studio)),2L,input(2,List.of(),List.of(studio)),3L,input(3,List.of(),List.of(studio))));
        assertEquals(1,report.completed());assertEquals(5,report.behaviors().size());
        assertEquals(20.0,report.behaviors().get(3).share());assertNull(report.behaviors().get(3).scores().averageRating());
        assertEquals(1,report.behaviors().get(4).companyCovered());
        var company=report.companies().getFirst();assertEquals(3,company.allCount());assertEquals(1,company.completed());assertEquals(8.0,company.scores().averageRating());
        assertEquals(1L,company.statuses().get(5));
    }
    @Test void sourceRequiresOrganizationAndExactAnimationDutyAndScopedVoice() {
        var studio=new CreditModels.Entity("company","公司","","","PERSON",2,List.of(),List.of(),"v","");
        var publisher=new CreditModels.Entity("publisher","发行","","","PERSON",2,List.of(),List.of(),"v","");
        var actor=new CreditModels.Entity("actor","演员","","","PERSON",1,List.of(),List.of(),"v","");
        var character=new CreditModels.Entity("character","角色","","","CHARACTER",1,List.of(),List.of(),"v","");
        var page=new CreditModels.Page("anime:bangumi:1","STAFF",List.of(studio,publisher,actor),List.of(character),
                List.of(new CreditModels.StaffCredit("company",List.of(new CreditModels.Position("14","动画制作",null,null))),
                        new CreditModels.StaffCredit("publisher",List.of(new CreditModels.Position("x","发行",null,null))),
                        new CreditModels.StaffCredit("actor",List.of(new CreditModels.Position("14","动画制作",null,null)))),
                List.of(new CreditModels.CastCredit("bad","anime:bangumi:2","actor","character",0,"原声","VOICE",null,null,null,null,null,"field")),
                new CreditModels.Coverage(1,60,3,3,3,0,0,0,null,true,false,"COMPLETE"),new CreditModels.Source("BANGUMI","","","v",Instant.now(),false,false,"v"),"v");
        var input=ReportCredits.fromSnapshot(new SubjectSnapshotService.Snapshot(1,new SubjectSnapshotService.Section("CAST",List.of(),0,0,false,null,null),
                new SubjectSnapshotService.Section("STAFF",List.of(page),3,3,true,null,null),null));
        assertEquals(List.of("company"),input.studios().stream().map(ReportCredits.Studio::uid).toList());assertTrue(input.performances().isEmpty());
        assertFalse(ReportCredits.animationProduction("制作委员会"));assertFalse(ReportCredits.animationProduction("制作"));
    }
}
