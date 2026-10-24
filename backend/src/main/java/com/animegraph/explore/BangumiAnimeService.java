package com.animegraph.explore;

import com.animegraph.common.*;
import com.animegraph.viewer.BangumiClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BangumiAnimeService {
    public record Snapshot<T>(T data,Instant fetchedAt,boolean cached,boolean stale) {}
    private record Entry(Object data,Instant at) {}
    private final BangumiClient client;
    private final Clock clock;
    private final Map<String,Entry> cache=Collections.synchronizedMap(new LinkedHashMap<>(128,.75f,true));
    private final ConcurrentHashMap<String,CompletableFuture<Snapshot<?>>> pending=new ConcurrentHashMap<>();
    private final Semaphore requests=new Semaphore(6);
    @org.springframework.beans.factory.annotation.Autowired
    public BangumiAnimeService(BangumiClient client) {this(client,Clock.systemUTC());}
    BangumiAnimeService(BangumiClient client,Clock clock) {this.client=client;this.clock=clock;}

    public Snapshot<PageResult<Map<String,Object>>> search(String keyword,Integer year,String format,int page,int size) {
        return search(keyword,year,format,page,size,false);
    }
    public Snapshot<PageResult<Map<String,Object>>> rankedCandidates(int page) {
        return search("",null,"",page,20,true);
    }
    private Snapshot<PageResult<Map<String,Object>>> search(String keyword,Integer year,String format,int page,int size,boolean ranked) {
        int offset=PageResult.offset(page,size);
        String key="search:"+List.of(keyword,year==null?"":year,format,page,size,ranked);
        return cached(key,() -> {
            var filter=new LinkedHashMap<String,Object>();filter.put("type",List.of(2));
            if(ranked)filter.put("rank",List.of(">0","<=300"));
            if(year!=null) filter.put("air_date",List.of(">="+year+"-01-01","<"+(year+1)+"-01-01"));
            if(!format.isBlank()) filter.put("meta_tags",List.of(format.equals("WEB")?"Web":format));
            var body=Map.<String,Object>of("keyword",keyword,"sort",keyword.isBlank()?"rank":"match","filter",filter);
            JsonNode response=client.publicAnimeRequest("/v0/search/subjects?limit="+size+"&offset="+offset,body);
            if(!response.path("data").isArray() || !response.path("total").canConvertToLong()) throw unavailable();
            var byId=new LinkedHashMap<String,Map<String,Object>>();
            for(var item:response.path("data")) if(item.path("type").asInt()==2 && item.path("id").asLong()>0) {var entity=entity(item);byId.put(String.valueOf(entity.get("uid")),entity);}
            return new PageResult<>(List.copyOf(byId.values()),page,size,Math.max(0,response.path("total").asLong()));
        });
    }
    public Snapshot<Map<String,Object>> detail(long id) {
        if(id<1 || id>Integer.MAX_VALUE) throw bad("Bangumi条目编号超出范围");
        return cached("subject:"+id,() -> {
            JsonNode item=client.publicAnimeRequest("/v0/subjects/"+id,null);
            if(item.path("type").asInt()!=2 || item.path("id").asLong()!=id) throw new ApiException(HttpStatus.NOT_FOUND,"NOT_ANIME","该条目不是动画作品");
            return entity(item);
        });
    }
    @SuppressWarnings("unchecked")
    private <T> Snapshot<T> cached(String key,Supplier<T> fetch) {
        Instant now=clock.instant();Entry old=cache.get(key);
        if(old!=null && old.at.plusSeconds(60).isAfter(now)) return new Snapshot<>((T)old.data,old.at,true,false);
        var promise=new CompletableFuture<Snapshot<?>>();var active=pending.putIfAbsent(key,promise);
        if(active!=null) {
            try {return (Snapshot<T>)active.get(8,TimeUnit.SECONDS);}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable();}
            catch(Exception e){if(e.getCause() instanceof ApiException api)throw api;throw unavailable();}
        }
        boolean acquired=false;
        try {
        // Another owner may have filled the cache before this owner was elected.
        Entry latest=cache.get(key);
        if(latest!=null && latest.at.plusSeconds(60).isAfter(clock.instant())) {
            var result=new Snapshot<>((T)latest.data,latest.at,true,false);promise.complete(result);return result;
        }
        acquired=requests.tryAcquire();
        if(!acquired) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"BANGUMI_BUSY","在线资料请求较多，请稍后重试");
        }
            T value=fetch.get();Instant at=clock.instant();
            synchronized(cache){cache.put(key,new Entry(value,at));while(cache.size()>128)cache.remove(cache.keySet().iterator().next());}
            var result=new Snapshot<>(value,at,false,false);promise.complete(result);return result;
        } catch(RuntimeException e) {
            boolean notFound=e instanceof ApiException api && api.status()==HttpStatus.NOT_FOUND;
            if(notFound) cache.remove(key);
            if(!notFound && old!=null && old.at.plusSeconds(600).isAfter(now)) {
                var result=new Snapshot<>((T)old.data,old.at,true,true);promise.complete(result);return result;
            }
            promise.completeExceptionally(e);
            throw e;
        } finally {if(acquired)requests.release();pending.remove(key,promise);}
    }
    static Map<String,Object> entity(JsonNode item) {
        var result=new LinkedHashMap<String,Object>();long id=item.path("id").asLong();
        result.put("uid","anime:bangumi:"+id);result.put("type","ANIME");result.put("source","bangumi");result.put("sourceId",String.valueOf(id));result.put("active",true);
        result.put("name",item.path("name").asText("未命名动画"));result.put("nameCn",item.path("name_cn").asText(""));result.put("summary",item.path("summary").asText(item.path("short_summary").asText("")));
        String image=item.path("images").path("common").asText("");if(image.isBlank()) image=item.path("images").path("large").asText("");
        result.put("imageUrl",image.matches("https://lain\\.bgm\\.tv/[^\\s]+")?image:"");
        String date=item.path("date").asText("");result.put("airDate",date);if(date.matches("[0-9]{4}-.*")) result.put("year",Integer.parseInt(date.substring(0,4)));
        result.put("format",item.path("platform").asText(""));result.put("score",item.path("rating").path("score").asDouble(item.path("score").asDouble()));
        result.put("rank",item.path("rating").path("rank").asInt(item.path("rank").asInt()));result.put("episodes",item.path("eps").asInt());
        result.put("ratingCount",item.path("rating").path("total").asInt());
        var tags=new LinkedHashSet<String>();for(var tag:item.path("tags"))if(tag.path("name").isTextual()&&!tag.path("name").asText().isBlank()&&tags.size()<100)tags.add(tag.path("name").asText());
        result.put("tags",List.copyOf(tags));
        result.put("onlineAvailable",true);result.put("subjectUrl","https://bgm.tv/subject/"+id);return Collections.unmodifiableMap(result);
    }
    static ApiException bad(String message) {return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message);}
    private static ApiException unavailable() {return new ApiException(HttpStatus.BAD_GATEWAY,"BANGUMI_INVALID_RESPONSE","Bangumi返回的动画资料格式不完整");}
}
