package com.animegraph.viewer;

import static org.assertj.core.api.Assertions.*;
import com.animegraph.common.ApiException;
import com.animegraph.viewer.ViewerModels.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollectionFilterTest {
    private Collection item(int score,String date,List<String> personal,List<String> community) {return new Collection(1,2,score,"星际故事","Space Story",null,date,12,12,9.0,"",personal,community);}
    @Test void combinedEvidenceFiltersUseExactTagsAndPersonalRatings() {
        var record=item(8,"2020-01-01",List.of("太空"),List.of("科幻","TV","2020"));
        assertThat(new CollectionFilter(2,"space","rating","COMMUNITY","科幻","2020","TV","HIGH",false).matches(record)).isTrue();
        assertThat(new CollectionFilter(2,"","updated","PERSONAL","科幻","","","",false).matches(record)).isFalse();
        assertThat(new CollectionFilter(2,"","updated","PERSONAL","太空","","","8",false).matches(record)).isTrue();
        assertThat(new CollectionFilter(2,"","updated","COMMUNITY","科","","","",false).matches(record)).isFalse();
        assertThat(new CollectionFilter(3,"","updated","COMMUNITY","","","","",false).matches(record)).isFalse();
    }
    @Test void missingDataFiltersFollowTheSameRulesAsStatistics() {
        var record=item(0,"",List.of(),List.of("2020","日本","TV"));
        assertThat(new CollectionFilter(2,"","updated","COMMUNITY","","UNKNOWN","TV","UNRATED",true).matches(record)).isTrue();
        assertThat(new CollectionFilter(2,"","updated","COMMUNITY","","","","RATED",false).matches(record)).isFalse();
        var noFormat=item(0,"",List.of(),List.of("科幻"));
        assertThat(new CollectionFilter(null,"","updated","COMMUNITY","","UNKNOWN","UNKNOWN","",false).matches(noFormat)).isTrue();
    }
    @Test void invalidFilterValuesAreRejectedInsteadOfSilentlyBroadeningTheResult() {
        for(String rating:List.of("0","11","8+","injection")) assertThatThrownBy(() -> new CollectionFilter(2,"","updated","COMMUNITY","","","",rating,false)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new CollectionFilter(2,"","updated","COMMUNITY","科幻","","","",true)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new CollectionFilter(2,"","updated","COMMUNITY","","tomorrow","","",false)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new CollectionFilter(2,"","updated","OTHER","","","","",false)).isInstanceOf(ApiException.class);
    }
}
