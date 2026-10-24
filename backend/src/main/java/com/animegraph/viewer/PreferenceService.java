package com.animegraph.viewer;

import com.animegraph.viewer.ViewerModels.Collection;
import java.util.*;
import java.util.stream.*;
import org.springframework.stereotype.Service;

@Service
public class PreferenceService {
    public record Bucket(String label,int count) {}
    public record Tag(String name,int count,int ratedCount,Double averageRating,Double adjustedRating,double share) {}
    public record Summary(int total,int completed,int rated,int unrated,Double averageRating,Double medianRating,int highRated,int dropped,int tagged,double tagCoverage) {}
    public record Quality(double ratingCoverage,int missingTags,int missingYear,int missingFormat) {}
    public record Report(Summary summary,List<Bucket> statuses,List<Bucket> ratings,List<Bucket> years,List<Bucket> formats,List<Tag> tags,List<Collection> favorites,List<String> insights,String tagSource,List<String> methodology,Quality quality,String tagSort,int totalTags) {}
    private static final Map<Integer,String> STATUSES=Map.of(1,"想看",2,"已看",3,"在看",4,"搁置",5,"抛弃");
    public Report analyze(List<Collection> all,String source) {
        return analyze(all,source,"FREQUENCY");
    }
    public Report analyze(List<Collection> all,String source,String sort) {
        boolean personal="PERSONAL".equals(source);
        var completed=all.stream().filter(c -> c.status()==2).toList();
        var rated=completed.stream().filter(c -> c.personalRating()>0).toList();
        var scores=rated.stream().mapToInt(Collection::personalRating).sorted().toArray();
        double exactAverage=Arrays.stream(scores).average().orElse(0);
        Double average=scores.length==0?null:round(exactAverage);
        Double median=scores.length==0?null:round((scores[(scores.length-1)/2]+scores[scores.length/2])/2.0);
        var grouped=new HashMap<String,List<Collection>>();
        int tagged=0;
        for(var item:completed) {
            var tags=ViewingMetadata.tags(item,source);
            if(!tags.isEmpty()) tagged++;
            for(String tag:tags) grouped.computeIfAbsent(tag,k -> new ArrayList<>()).add(item);
        }
        var allTags=grouped.entrySet().stream().map(entry -> {
            var values=entry.getValue();var ratings=values.stream().filter(c -> c.personalRating()>0).mapToInt(Collection::personalRating).summaryStatistics();
            return new Tag(entry.getKey(),values.size(),(int)ratings.getCount(),ratings.getCount()==0?null:round(ratings.getAverage()),
                ratings.getCount()<3?null:round((ratings.getSum()+3.0*exactAverage)/(ratings.getCount()+3)),percent(values.size(),completed.size()));
        }).sorted(Comparator.comparingInt(Tag::count).reversed().thenComparing(Tag::name)).toList();
        var tags=(sort.equals("RATING")?allTags.stream().sorted(Comparator.comparingDouble((Tag t) -> t.adjustedRating()==null?-1:t.adjustedRating()).reversed().thenComparing(Comparator.comparingInt(Tag::count).reversed()).thenComparing(Tag::name)):allTags.stream()).limit(20).toList();
        var statuses=IntStream.rangeClosed(1,5).mapToObj(s -> new Bucket(STATUSES.get(s),(int)all.stream().filter(c -> c.status()==s).count())).toList();
        var distribution=IntStream.rangeClosed(1,10).mapToObj(s -> new Bucket(String.valueOf(s),(int)rated.stream().filter(c -> c.personalRating()==s).count())).toList();
        var years=new TreeMap<String,Integer>();var formats=new TreeMap<String,Integer>();
        for(var item:completed) {
            String year=ViewingMetadata.year(item);
            years.merge(year,1,Integer::sum);
            formats.merge(ViewingMetadata.format(item),1,Integer::sum);
        }
        var favorites=rated.stream().filter(c -> c.personalRating()>=8).sorted(Comparator.comparingInt(Collection::personalRating).reversed().thenComparing(Collection::title)).limit(12).toList();
        var summary=new Summary(all.size(),completed.size(),rated.size(),completed.size()-rated.size(),average,median,(int)rated.stream().filter(c -> c.personalRating()>=8).count(),(int)all.stream().filter(c -> c.status()==5).count(),tagged,percent(tagged,completed.size()));
        var insights=new ArrayList<String>();
        if(completed.isEmpty()) insights.add("暂无已看完的动画记录。同步片单后，只有标记为“已看”的作品会参与偏好分析。");
        else if(completed.size()<5) insights.add("当前仅有"+completed.size()+"部已看作品，样本较少，先展示记录，不作稳定偏好判断。");
        else {
            insights.add("已看"+completed.size()+"部动画，其中"+rated.size()+"部有个人评分；未评分作品不按0分计算。");
            if(!allTags.isEmpty()) {var first=allTags.getFirst();insights.add("“"+first.name()+"”是已看作品中最常见的标签："+first.count()+"部，占已看记录的"+first.share()+"%。频次不等同于喜欢程度。");}
            var preference=allTags.stream().filter(t -> t.ratedCount()>=3 && t.adjustedRating()!=null && average!=null && t.adjustedRating()>average+.3).max(Comparator.comparingDouble(Tag::adjustedRating));
            preference.ifPresent(t -> insights.add("“"+t.name()+"”有"+t.ratedCount()+"部已评分样本，个人平均"+t.averageRating()+"分，高于整体平均；可作为候选偏好线索。"));
        }
        if(rated.size()<3) insights.add("有效个人评分不足3部，暂不判断高偏好标签。可在Bangumi补充评分后重新同步。");
        if(tagged<completed.size()) insights.add("有"+(completed.size()-tagged)+"部已看作品缺少可用标签，标签统计只覆盖现有资料。");
        return new Report(summary,statuses,distribution,buckets(years),buckets(formats),tags,favorites,insights,personal?"PERSONAL":"COMMUNITY",List.of(
            "仅“已看”动画参与评分、标签、发行年份和形式统计；观看状态统计使用全部已同步动画。",
            "0表示未评分，不能视为差评；社区评分与个人评分分别展示。",
            personal?"标签来自你在Bangumi填写的个人标签，每部作品每个标签只计一次。":"标签来自Bangumi条目摘要的前10个社区标签，每部作品每个标签只计一次；未读取完整标签库。",
            "常见年份、动画形式及地区标签从偏好标签中剔除。形式依据条目摘要现有标签识别，缺失时不猜测。",
            "标签校正评分=(该标签个人评分总和+3×整体个人均分)/(该标签已评分数+3)，至少3个评分样本才显示。",
            "发行年份不是观看年份；收藏更新时间不能证明观看时间。统计是偏好线索，不推断性格。",
            "标签排序在全部有效标签中进行，再展示前20项；点击标签和分布项可查看作为依据的已看作品。"),
            new Quality(percent(rated.size(),completed.size()),completed.size()-tagged,years.getOrDefault("未知",0),formats.getOrDefault("未标注",0)),sort,allTags.size());
    }
    private static List<Bucket> buckets(Map<String,Integer> map) {return map.entrySet().stream().map(e -> new Bucket(e.getKey(),e.getValue())).toList();}
    private static double percent(int value,int total) {return total==0?0:round(value*100.0/total);}
    private static double round(double value) {return Math.round(value*100)/100.0;}
}
