package com.animegraph.viewer;

import static org.assertj.core.api.Assertions.*;
import com.animegraph.viewer.ViewerModels.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreferenceServiceTest {
    private Collection item(long id,int state,int score,List<String> tags) {return new Collection(id,state,score,"作品"+id,"original",null,"2020-01-01",12,0,9.9,"",tags,tags);}
    @Test void zeroMeansUnratedAndOnlyCompletedItemsAffectTaste() {
        var report=new PreferenceService().analyze(List.of(item(1,2,8,List.of("科幻","科幻","TV","2020")),item(2,2,0,List.of("科幻")),item(3,3,10,List.of("恋爱")),item(4,5,1,List.of("恐怖"))),"COMMUNITY");
        assertThat(report.summary().total()).isEqualTo(4);
        assertThat(report.summary().completed()).isEqualTo(2);
        assertThat(report.summary().rated()).isEqualTo(1);
        assertThat(report.summary().averageRating()).isEqualTo(8.0);
        assertThat(report.summary().unrated()).isEqualTo(1);
        assertThat(report.tags()).hasSize(1);
        assertThat(report.tags().getFirst().count()).isEqualTo(2);
        assertThat(report.tags().getFirst().adjustedRating()).isNull();
        assertThat(report.favorites()).extracting(Collection::subjectId).containsExactly(1L);
        assertThat(report.insights()).anyMatch(s -> s.contains("样本较少"));
    }
    @Test void emptyReportNeverProducesInventedScoresOrPreferenceClaims() {
        var report=new PreferenceService().analyze(List.of(),"PERSONAL");
        assertThat(report.summary().averageRating()).isNull();
        assertThat(report.summary().medianRating()).isNull();
        assertThat(report.tags()).isEmpty();
        assertThat(report.summary().tagCoverage()).isZero();
        assertThat(report.ratings()).allSatisfy(b -> assertThat(b.count()).isZero());
    }
    @Test void ratingShrinkageAndTagSourcesHaveIndependentEvidence() {
        var values=List.of(item(1,2,9,List.of("科幻")),item(2,2,8,List.of("科幻")),item(3,2,10,List.of("科幻")),item(4,2,5,List.of("日常")));
        var report=new PreferenceService().analyze(values,"COMMUNITY");
        assertThat(report.summary().averageRating()).isEqualTo(8.0);
        assertThat(report.tags().getFirst().averageRating()).isEqualTo(9.0);
        assertThat(report.tags().getFirst().adjustedRating()).isEqualTo(8.5);
        assertThat(report.tags().getFirst().share()).isEqualTo(75.0);
        assertThat(report.summary().medianRating()).isEqualTo(8.5);
        var separate=new Collection(9,2,8,"x","x",null,"",0,0,0,"",List.of("个人标签"),List.of("社区标签"));
        assertThat(new PreferenceService().analyze(List.of(separate),"PERSONAL").tags().getFirst().name()).isEqualTo("个人标签");
        assertThat(new PreferenceService().analyze(List.of(separate),"COMMUNITY").tags().getFirst().name()).isEqualTo("社区标签");
    }
    @Test void scoreRankingConsidersTagsOutsideTheTwentyMostFrequent() {
        var common=java.util.stream.IntStream.range(0,21).mapToObj(n -> "常见"+n).toList();
        var values=new java.util.ArrayList<Collection>();
        for(int i=0;i<4;i++) values.add(item(i,2,5,common));
        for(int i=4;i<7;i++) values.add(item(i,2,10,List.of("稀有高分")));
        var service=new PreferenceService();
        var frequent=service.analyze(values,"COMMUNITY","FREQUENCY");
        assertThat(frequent.tags()).noneMatch(t -> t.name().equals("稀有高分"));
        var ranked=service.analyze(values,"COMMUNITY","RATING");
        assertThat(ranked.tags().getFirst().name()).isEqualTo("稀有高分");
        assertThat(ranked.totalTags()).isEqualTo(22);
        assertThat(ranked.quality().ratingCoverage()).isEqualTo(100.0);
    }
}
