package com.animegraph.viewer;

import java.util.*;

/** Deterministic content baseline with signed personal ratings and greedy diversity. */
public final class RecommendationEngine {
    private RecommendationEngine(){}
    public record Reason(String text,List<Long> evidenceSubjectIds) {}
    public record Item(Map<String,Object> entity,double score,double contentScore,double communityScore,double diversityScore,List<Reason> reasons) {}
    public record Result(String modelVersion,String mode,int positiveWorks,int candidateCount,List<Item> items,List<String> methodology) {}
    public static Set<String> tags(Map<String,Object> row){var result=new LinkedHashSet<String>();Object values=row.get("tags");if(values instanceof List<?> list)for(var tag:list)if(tag instanceof String text&&!text.isBlank()&&!text.matches("\\d{4}|TV|OVA|WEB|剧场版|日本|中国|美国|动画"))result.add(text);return result;}
    public static Result rank(List<ViewerModels.Collection> library,List<Map<String,Object>> candidates,Set<Long> feedback,int offset,String algorithm){
        var excluded=new HashSet<>(feedback);library.forEach(c->excluded.add(c.subjectId()));
        var rated=library.stream().filter(c->c.status()==2&&c.personalRating()>0).toList();
        var positive=rated.stream().filter(c->c.personalRating()>=8).toList();boolean personal=positive.size()>=5;
        var vector=new HashMap<String,Double>();for(var c:rated){double signal=(c.personalRating()-6)/4.0;for(var tag:new HashSet<>(c.communityTags()))vector.merge(tag,signal,Double::sum);}
        var frequencies=new HashMap<String,Integer>();candidates.forEach(c->tags(c).forEach(t->frequencies.merge(t,1,Integer::sum)));
        var weighted=new HashMap<String,Double>();vector.forEach((t,w)->weighted.put(t,w*(Math.log((1.0+candidates.size())/(1.0+frequencies.getOrDefault(t,0)))+1)));
        double norm=Math.sqrt(weighted.values().stream().mapToDouble(w->w*w).sum());
        var pool=new ArrayList<Item>();
        for(var candidate:candidates){long id=Long.parseLong(String.valueOf(candidate.get("sourceId")));if(excluded.contains(id))continue;var features=tags(candidate);
            double dot=0,length=0;for(var t:features){double weight=Math.log((1.0+candidates.size())/(1.0+frequencies.getOrDefault(t,0)))+1;dot+=weight*weighted.getOrDefault(t,0.0);length+=weight*weight;}
            double content=norm==0||length==0?0:Math.max(0,dot/(norm*Math.sqrt(length)));double quality=candidate.get("score") instanceof Number n?Math.max(0,Math.min(1,n.doubleValue()/10)):0;
            double score=algorithm.equals("POPULAR")||!personal?quality:algorithm.equals("CONTENT")?content:(.55*content+.10*quality)/.65;
            var reasons=new ArrayList<Reason>();
            if(personal&&!algorithm.equals("POPULAR")){var common=features.stream().filter(t->weighted.getOrDefault(t,0.0)>0).sorted(Comparator.<String>comparingDouble(t->weighted.getOrDefault(t,0.0)).reversed()).limit(3).toList();for(var tag:common){var evidence=positive.stream().filter(c->c.communityTags().contains(tag)).map(ViewerModels.Collection::subjectId).toList();if(!evidence.isEmpty())reasons.add(new Reason("高评分已看作品常见标签："+tag,evidence));}}
            reasons.add(new Reason("来源社区评分 "+Math.round(quality*100)/10.0+"/10；有界候选样本",List.of()));
            pool.add(new Item(candidate,score,content,quality,1,reasons));
        }
        pool.sort(Comparator.comparingDouble(Item::score).reversed().thenComparing(i->String.valueOf(i.entity().get("sourceId"))));
        var selected=new ArrayList<Item>();int limit=Math.min(pool.size(),Math.min(60,offset+10));
        while(selected.size()<limit){Item best=null;double bestScore=-Double.MAX_VALUE;for(var item:pool){double overlap=0;for(var chosen:selected)overlap=Math.max(overlap,jaccard(tags(item.entity()),tags(chosen.entity())));double score=algorithm.equals("HYBRID")?item.score()*.9+(1-overlap)*.1:item.score();if(score>bestScore){bestScore=score;best=new Item(item.entity(),score,item.contentScore(),item.communityScore(),1-overlap,item.reasons());}}if(best==null)break;final Object selectedId=best.entity().get("sourceId");selected.add(best);pool.removeIf(i->i.entity().get("sourceId").equals(selectedId));}
        return new Result("content-signed-tfidf/1",personal?algorithm:"COLD_START",positive.size(),candidates.size(),selected.stream().skip(offset).limit(10).toList(),List.of("默认排除所有已收藏状态及不感兴趣；想看也不重复推荐。","至少5部个人评分≥8的已看作品后启用个性化；低评分作为负向信号，未评分不当作喜欢。","使用来源现有标签构建TF-IDF余弦相似度；社区质量取来源评分/10。","当前演职员候选覆盖不足，未启用人员权重；可用内容/质量权重重新归一化，混合排序再加入10%多样性。","热门候选存在选择偏差；结果不代表全站最佳，也未使用多用户协同过滤。"));
    }
    public static double jaccard(Set<String>a,Set<String>b){var union=new HashSet<>(a);union.addAll(b);if(union.isEmpty())return 0;var common=new HashSet<>(a);common.retainAll(b);return common.size()/(double)union.size();}
}
