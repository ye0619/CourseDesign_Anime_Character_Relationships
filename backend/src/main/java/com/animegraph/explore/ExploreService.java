package com.animegraph.explore;

import com.animegraph.catalog.CatalogService;
import com.animegraph.common.*;
import com.animegraph.graph.GraphStore;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ExploreService {
    public record Meta(String onlineStatus,String localStatus,Instant fetchedAt,boolean cached,boolean stale,String notice) {}
    public record Result(PageResult<Map<String,Object>> online,PageResult<Map<String,Object>> local,Meta meta) {}
    public record Detail(Map<String,Object> entity,Map<String,Object> localEntity,Meta meta,String dataSource) {}
    private final CatalogService catalog;private final GraphStore graph;private final BangumiAnimeService online;
    public ExploreService(CatalogService catalog,GraphStore graph,BangumiAnimeService online) {this.catalog=catalog;this.graph=graph;this.online=online;}

    public Result search(String keyword,Integer year,String format,String mode,int page,int localPage) {
        return search(keyword,year,format,mode,"",page,localPage);
    }
    public Result search(String keyword,Integer year,String format,String mode,String source,int page,int localPage) {
        keyword=keyword==null?"":keyword.strip();format=format==null?"":format;
        PageResult.offset(page,12);PageResult.offset(localPage,6);
        if(keyword.length()>100 || year!=null && (year<1800 || year>2200) || !Set.of("","TV","剧场版","OVA","WEB").contains(format) || !Set.of("MIXED","ONLINE","LOCAL").contains(mode)) throw BangumiAnimeService.bad("搜索条件超出支持范围");
        if(!Set.of("","bangumi","fixture","manual").contains(source) || !source.isBlank() && !mode.equals("LOCAL")) throw BangumiAnimeService.bad("来源筛选仅适用于本地档案");
        var local=new PageResult<Map<String,Object>>(List.of(),localPage,6,0);var remote=new PageResult<Map<String,Object>>(List.of(),page,12,0);
        String localStatus="SKIPPED",onlineStatus="SKIPPED";var notes=new ArrayList<String>();
        if(!mode.equals("ONLINE")) try {local=catalog.list("ANIME",keyword,year,format,true,localPage,6,source.isBlank()?null:source);localStatus="OK";} catch(RuntimeException e) {localStatus="ERROR";notes.add("本地资料暂不可用，在线结果仍可浏览。");}
        BangumiAnimeService.Snapshot<PageResult<Map<String,Object>>> snapshot=null;
        if(!mode.equals("LOCAL")) try {snapshot=online.search(keyword,year,format,page,12);remote=snapshot.data();onlineStatus=snapshot.stale()?"STALE":"OK";} catch(ApiException e) {onlineStatus="ERROR";notes.add(e.status().value()==429?"Bangumi请求受限，请稍后重试。":"Bangumi在线资料暂不可用；本地资料可继续浏览。");}
        if(snapshot!=null && snapshot.stale()) notes.add("在线连接暂不可用，正在展示最近获取的缓存资料。");
        if(!format.isBlank() && !mode.equals("LOCAL")) notes.add("在线形式按Bangumi公共标签筛选，本地按档案形式筛选。");
        if(!remote.items().isEmpty()) {
            Map<String,Map<String,Object>> indexed=new HashMap<>();boolean known=true;
            try {for(var row:graph.read("MATCH (n:Entity:Anime) WHERE n.active=true AND n.uid IN $uids RETURN n AS entity LIMIT 12",Map.of("uids",remote.items().stream().map(e -> e.get("uid")).toList()))) {var e=GraphStore.castMap(row.get("entity"));indexed.put(String.valueOf(e.get("uid")),e);}}
            catch(RuntimeException e) {known=false;notes.add("暂不能核对在线作品是否已有本地图谱。");}
            final boolean checked=known;
            remote=new PageResult<>(remote.items().stream().map(e -> decorate(e,indexed.get(String.valueOf(e.get("uid"))),checked)).toList(),page,12,remote.total());
        }
        local=new PageResult<>(local.items().stream().map(e -> decorate(e,e,true)).toList(),local.page(),local.pageSize(),local.total());
        return new Result(remote,local,new Meta(onlineStatus,localStatus,snapshot==null?null:snapshot.fetchedAt(),snapshot!=null && snapshot.cached(),snapshot!=null && snapshot.stale(),String.join(" ",notes)));
    }
    public Detail detail(long id) {
        return detail(id,"AUTO");
    }
    public Detail detail(long id,String source) {
        if(id<1 || id>Integer.MAX_VALUE || !Set.of("AUTO","LOCAL","ONLINE").contains(source)) throw BangumiAnimeService.bad("作品编号或资料来源无效");
        Map<String,Object> local=null;String localStatus="OK";String note="";
        try {local=catalog.detail("ANIME","anime:bangumi:"+id);} catch(ApiException e) {if(e.status().value()!=404) {localStatus="ERROR";note="本地资料暂不可用。";}} catch(RuntimeException e) {localStatus="ERROR";note="本地资料暂不可用。";}
        boolean localFirst=source.equals("LOCAL") || source.equals("AUTO") && local!=null && hasCore(local);
        if(localFirst) {
            if(local==null) throw new ApiException(localStatus.equals("ERROR")?org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE:org.springframework.http.HttpStatus.NOT_FOUND,
                    localStatus.equals("ERROR")?"LOCAL_UNAVAILABLE":"LOCAL_MISSING","当前本地资料暂不可读，请重试或主动切换在线资料");
            return new Detail(decorate(local,local,true),local,new Meta("SKIPPED",localStatus,null,false,false,"当前读取本地档案；资料记录数不代表来源采集完整。"),"LOCAL");
        }
        try {
            var snapshot=online.detail(id);if(snapshot.stale()) note+=" 在线连接暂不可用，展示最近缓存。";
            return new Detail(decorate(merge(snapshot.data(),local),local,localStatus.equals("OK")),local,new Meta(snapshot.stale()?"STALE":"OK",localStatus,snapshot.fetchedAt(),snapshot.cached(),snapshot.stale(),note),"ONLINE");
        } catch(ApiException e) {
            if(local==null || source.equals("ONLINE")) throw e;
            return new Detail(decorate(local,local,true),local,new Meta("ERROR",localStatus,null,false,false,"Bangumi在线资料暂不可用，当前展示本地档案。"),"LOCAL");
        }
    }
    static boolean hasCore(Map<String,Object> value) {
        return value.get("name") instanceof String name && !name.isBlank()
                && value.get("summary") instanceof String summary && !summary.isBlank();
    }
    static Map<String,Object> merge(Map<String,Object> remote,Map<String,Object> local) {
        var result=new LinkedHashMap<>(remote);
        if(local!=null) local.forEach((key,value)->{if(value!=null && (!(value instanceof String text)||!text.isBlank())) result.put(key,value);});
        return result;
    }
    private Map<String,Object> decorate(Map<String,Object> entity,Map<String,Object> local,boolean checked) {
        var result=new LinkedHashMap<>(entity);result.put("localAvailable",local!=null);result.put("localChecked",checked);
        result.put("libraryStatus",!checked?"UNCHECKED":local!=null?"ARCHIVED":"ONLINE_ONLY");
        if(local!=null) result.put("localUid",local.get("uid"));return result;
    }
}
