package com.animegraph.viewer;

import com.animegraph.common.ApiException;
import com.animegraph.viewer.ViewerModels.Collection;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;

public record CollectionFilter(Integer status,String keyword,String sort,String tagSource,String tag,String year,String format,String rating,boolean missingTags) {
    public CollectionFilter {
        keyword=clean(keyword);tag=clean(tag);year=clean(year);format=clean(format);rating=clean(rating);
        if(status!=null && (status<1||status>5)) throw bad("观看状态必须为1—5");
        if(keyword.length()>100 || tag.length()>100 || !Set.of("updated","rating","title").contains(sort)) throw bad("片单筛选参数不正确");
        if(!Set.of("COMMUNITY","PERSONAL").contains(tagSource)) throw bad("标签来源不正确");
        if(!year.isEmpty() && !year.equals("UNKNOWN") && !year.matches("(?:18|19|20|21)\\d{2}")) throw bad("发行年份不正确");
        if(!format.isEmpty() && !format.equals("UNKNOWN") && !ViewingMetadata.FORMATS.contains(format)) throw bad("动画形式不正确");
        if(!rating.isEmpty() && !Set.of("UNRATED","RATED","HIGH").contains(rating) && !rating.matches("[1-9]|10")) throw bad("个人评分筛选不正确");
        if(missingTags && !tag.isEmpty()) throw bad("缺少标签与指定标签不能同时筛选");
    }
    public boolean matches(Collection item) {
        return (status==null || item.status()==status)
            && (item.title()+" "+item.originalName()).toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))
            && (tag.isEmpty() || ViewingMetadata.tags(item,tagSource).contains(tag))
            && (!missingTags || ViewingMetadata.tags(item,tagSource).isEmpty())
            && (year.isEmpty() || ViewingMetadata.year(item).equals(year.equals("UNKNOWN")?"未知":year))
            && (format.isEmpty() || ViewingMetadata.format(item).equals(format.equals("UNKNOWN")?"未标注":format))
            && switch(rating) {case "" -> true;case "UNRATED" -> item.personalRating()==0;case "RATED" -> item.personalRating()>0;case "HIGH" -> item.personalRating()>=8;default -> item.personalRating()==Integer.parseInt(rating);};
    }
    private static String clean(String value) {return value==null?"":value.strip();}
    private static ApiException bad(String message) {return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message);}
}
