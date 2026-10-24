package com.animegraph.viewer;

import com.animegraph.common.ApiException;
import com.animegraph.explore.BangumiAnimeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CandidateService {
    public static final String DATASET_VERSION="bangumi-ranked-300/3";
    public record State(String status,int collected,String error,Instant updatedAt,String samplingRule) {}
    private final CandidateMapper mapper;private final BangumiAnimeService anime;private final ObjectMapper json;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"recommendation-candidates");t.setDaemon(true);return t;});
    private volatile State state=new State("IDLE",0,null,null,"Bangumi动画排名检索前15页，每页20部；90秒预算内最多300部，不代表全部动画或观众");
    public CandidateService(CandidateMapper mapper,BangumiAnimeService anime,ObjectMapper json){this.mapper=mapper;this.anime=anime;this.json=json;}
    @PreDestroy void close(){worker.shutdownNow();try{worker.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    public State state(){return state;}
    public synchronized State start(){
        if(state.status().equals("RUNNING"))return state;
        if(state.updatedAt()!=null&&state.updatedAt().isAfter(Instant.now().minusSeconds(60)))throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"CANDIDATE_COOLDOWN","刚刚更新候选，请稍后再试");
        state=new State("RUNNING",0,null,state.updatedAt(),state.samplingRule());
        worker.execute(()->{int count=0;var seen=new HashSet<Long>();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);try{for(int page=1;page<=15;page++){if(Thread.currentThread().isInterrupted())return;if(System.nanoTime()>deadline){state=new State("PARTIAL",count,"本轮达到90秒预算，可稍后更新",Instant.now(),state.samplingRule());return;}var snapshot=anime.rankedCandidates(page);if(snapshot.stale())throw new IllegalStateException("来源暂不可用");for(var item:snapshot.data().items()){var row=new LinkedHashMap<>(item);row.put("fetchedAt",snapshot.fetchedAt().toString());row.put("datasetVersion",DATASET_VERSION);long id=Long.parseLong(String.valueOf(item.get("sourceId")));mapper.save(id,json.writeValueAsString(row));seen.add(id);}count=seen.size();state=new State("RUNNING",count,null,snapshot.fetchedAt(),state.samplingRule());if(snapshot.data().items().size()<20)break;Thread.sleep(400);}state=new State("COMPLETE",count,null,Instant.now(),state.samplingRule());}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();state=new State(count>0?"PARTIAL":"FAILED",count,e instanceof ApiException?e.getMessage():"候选补充中断，已保存的公共样本仍可使用",Instant.now(),state.samplingRule());}});
        return state;
    }
    public List<Map<String,Object>> all(){var result=new ArrayList<Map<String,Object>>();for(var raw:mapper.all())try{{var row=json.readValue(raw,new TypeReference<Map<String,Object>>(){});if(DATASET_VERSION.equals(row.get("datasetVersion")))result.add(row);}}catch(Exception e){throw new IllegalStateException("Invalid candidate snapshot",e);}return result;}
    public Map<String,Object> community(){var data=all();var tags=new TreeMap<String,Integer>();var formats=new TreeMap<String,Integer>();var years=new TreeMap<String,Integer>();for(var row:data){for(var tag:RecommendationEngine.tags(row))tags.merge(tag,1,Integer::sum);formats.merge(Objects.toString(row.get("format"),"未知"),1,Integer::sum);years.merge(Objects.toString(row.get("year"),"未知"),1,Integer::sum);}return Map.of("state",state,"size",data.size(),"tags",tags,"formats",formats,"years",years,"subjects",data.stream().map(r->Map.of("id",r.get("sourceId"),"name",Objects.toString(r.get("nameCn"),"").isBlank()?r.get("name"):r.get("nameCn"),"fetchedAt",r.getOrDefault("fetchedAt",""))).toList(),"notice","仅Bangumi排名检索的有界样本分布；本次只有快照，不展示增长趋势。");}
}
