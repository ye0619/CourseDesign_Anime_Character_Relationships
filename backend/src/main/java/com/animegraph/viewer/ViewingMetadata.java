package com.animegraph.viewer;

import com.animegraph.viewer.ViewerModels.Collection;
import java.util.List;
import java.util.Set;

final class ViewingMetadata {
    static final Set<String> FORMATS=Set.of("TV","剧场版","OVA","OAD","WEB","短片","MV","CM","SP");
    private ViewingMetadata() {}
    static String year(Collection item) {return item.releaseDate()!=null && item.releaseDate().matches("(?:18|19|20|21)\\d{2}.*")?item.releaseDate().substring(0,4):"未知";}
    static String format(Collection item) {return item.communityTags().stream().filter(FORMATS::contains).findFirst().orElse("未标注");}
    static List<String> tags(Collection item,String source) {
        return ("PERSONAL".equals(source)?item.personalTags():item.communityTags()).stream().map(String::strip)
            .filter(t -> !t.isBlank() && !t.matches("(?i)\\d{4}([年].*)?|\\d{4}[-/].*|[1247]月(新番)?|TV|OVA|OAD|WEB|SP|MV|CM|剧场版|短片|日本|中国|美国|日本动画|中国动画|动画"))
            .distinct().toList();
    }
}
