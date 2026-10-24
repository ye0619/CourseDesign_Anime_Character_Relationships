package com.animegraph.viewer;

import static com.animegraph.viewer.ViewerModels.*;
import com.animegraph.common.*;
import com.fasterxml.jackson.databind.*;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ViewerService implements ApplicationRunner {
    private final ViewerMapper mapper;
    private final BangumiClient bangumi;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final PreferenceService preferences;
    private final Object[] locks=new Object[64];
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r -> {var thread=new Thread(r,"bangumi-collection-sync");thread.setDaemon(true);return thread;});
    public ViewerService(ViewerMapper mapper,BangumiClient bangumi,ObjectMapper json,TransactionTemplate transactions,PreferenceService preferences) {
        this.mapper=mapper;this.bangumi=bangumi;this.json=json;this.transactions=transactions;this.preferences=preferences;
        Arrays.setAll(locks,i -> new Object());
    }
    public void run(ApplicationArguments args) {mapper.recover();}
    @PreDestroy void close() {worker.shutdownNow();try {worker.awaitTermination(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    private Object lock(String id) {return locks[Math.floorMod(id.hashCode(),locks.length)];}
    public Binding binding(String id) {return id==null?null:mapper.binding(id);}
    public record ViewerData(Binding binding,List<ViewerModels.Collection> collections) {}
    public ViewerData snapshot(String id) {if(id==null)require(null);synchronized(lock(id)){return new ViewerData(require(id),read(id));}}
    public State state(String id,boolean hasToken) {
        var b=binding(id);
        if(b==null) return new State(false,null,null,false,false,"IDLE",0,0,false,null,null);
        return new State(true,new Profile(b.bangumiId(),b.username(),b.nickname(),b.avatar()),b.bindingMode(),"TOKEN".equals(b.bindingMode()),"TOKEN".equals(b.bindingMode())&&!hasToken,b.syncStatus(),b.syncProcessed(),b.remoteTotal(),b.truncated(),b.syncError(),b.lastSyncedAt());
    }
    public Profile verify(String username,String token) {
        if(token!=null && (!token.matches("[A-Za-z0-9_.~-]{16,3000}"))) throw bad("令牌格式不正确，请复制Bangumi Access Token，不要填写密码");
        return bangumi.profile(username,token);
    }
    public void bind(String id,Profile profile,String mode) {
        synchronized(lock(id)) {
            var old=binding(id);if(old!=null && old.syncStatus().equals("RUNNING")) throw conflict("当前正在同步，请等待结束后切换账号");
            transactions.executeWithoutResult(tx -> {
                if(old!=null && old.bangumiId()==profile.id() && !(old.bindingMode().equals("TOKEN") && mode.equals("PUBLIC"))) mapper.reauthorize(id,profile,mode,UUID.randomUUID().toString());
                else {mapper.clearReport(id);mapper.clearFeedback(id);mapper.clear(id);mapper.bind(id,profile,mode,UUID.randomUUID().toString());}
            });
        }
    }
    public void unbind(String id) {if(id!=null) synchronized(lock(id)) {mapper.unbind(id);}}
    public void sync(String id,String token) {
        synchronized(lock(id)) {
            var b=require(id);
            if("TOKEN".equals(b.bindingMode()) && token==null) throw new ApiException(HttpStatus.CONFLICT,"BINDING_REAUTH_REQUIRED","当前Bangumi授权会话已结束，请重新验证令牌后同步；已有片单仍可查看");
            if(b.syncStatus().equals("RUNNING")) throw conflict("当前已有同步任务，请等待完成");
            if(b.lastSyncedAt()!=null && b.lastSyncedAt().isAfter(java.time.Instant.now().minusSeconds(60))) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"SYNC_COOLDOWN","刚刚同步完成，请在60秒后更新");
            if(mapper.start(id,b.revision())!=1) throw conflict("账号状态已变更，请刷新页面");
            try {worker.execute(() -> synchronize(b,token));}
            catch(RejectedExecutionException e) {mapper.finish(id,b.revision(),"FAILED","同步队列繁忙，请稍后重试",false);throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"SYNC_BUSY","同步队列繁忙，请稍后重试");}
        }
    }
    private void synchronize(Binding binding,String token) {
        String id=binding.id(),revision=binding.revision();
        try {
            var rows=new LinkedHashMap<Long,ViewerModels.Collection>();int offset=0,total=0;
            do {
                if(Thread.currentThread().isInterrupted() || !current(id,revision)) return;
                var page=bangumi.collections(binding.username(),offset,token);total=page.total();
                if(page.items().isEmpty() && offset<total) throw new ApiException(HttpStatus.BAD_GATEWAY,"BANGUMI_INCOMPLETE","来源返回不完整的片单，已保留上次同步结果，请稍后重试");
                for(var item:page.items()) rows.put(item.subjectId(),item);
                mapper.progress(id,revision,rows.size(),total);offset+=100;
                if(offset<Math.min(total,5000)) Thread.sleep(350);
            } while(offset<Math.min(total,5000));
            final boolean truncated=rows.size()<total;
            synchronized(lock(id)) {
                if(!current(id,revision)) return;
                transactions.executeWithoutResult(tx -> {
                    mapper.clear(id);
                    for(var item:rows.values()) mapper.insert(id,item,encode(item.personalTags()),encode(item.communityTags()));
                    mapper.finish(id,revision,"SUCCEEDED",null,truncated);
                });
            }
        } catch(Exception e) {
            if(e instanceof InterruptedException) Thread.currentThread().interrupt();
            synchronized(lock(id)) {if(current(id,revision)) mapper.finish(id,revision,"FAILED",e instanceof ApiException?e.getMessage():"同步暂时失败，请重试；已保存的片单保持不变",binding.truncated());}
        }
    }
    private boolean current(String id,String revision) {var b=binding(id);return b!=null && b.revision().equals(revision);}
    public PageResult<ViewerModels.Collection> collections(String id,Integer status,String keyword,int page,int size,String sort) {
        var result=collections(id,new CollectionFilter(status,keyword,sort,"COMMUNITY","","","","",false),page,size);
        return new PageResult<>(result.items(),result.page(),result.pageSize(),result.total());
    }
    public CollectionPage collections(String id,CollectionFilter filter,int page,int size) {
        require(id);int offset=PageResult.offset(page,size);var all=read(id);
        var items=all.stream().filter(filter::matches).sorted(
            filter.sort().equals("rating")?Comparator.comparingInt(ViewerModels.Collection::personalRating).reversed().thenComparing(ViewerModels.Collection::title).thenComparingLong(ViewerModels.Collection::subjectId):
            filter.sort().equals("title")?Comparator.comparing(ViewerModels.Collection::title).thenComparingLong(ViewerModels.Collection::subjectId):Comparator.comparing(ViewerModels.Collection::updatedAt).reversed().thenComparingLong(ViewerModels.Collection::subjectId)).toList();
        var tagCounts=new HashMap<String,Integer>();all.forEach(c -> ViewingMetadata.tags(c,filter.tagSource()).forEach(t -> tagCounts.merge(t,1,Integer::sum)));
        var facets=new Facets(all.stream().map(ViewingMetadata::year).distinct().sorted(Comparator.reverseOrder()).toList(),
            all.stream().map(ViewingMetadata::format).distinct().sorted().toList(),tagCounts.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).limit(100).map(Map.Entry::getKey).toList());
        return new CollectionPage(items.stream().skip(offset).limit(size).toList(),page,size,items.size(),facets);
    }
    public PreferenceService.Report preferences(String id,String source) {
        return preferences(id,source,"FREQUENCY");
    }
    public PreferenceService.Report preferences(String id,String source,String sort) {
        require(id);if(!Set.of("COMMUNITY","PERSONAL").contains(source)) throw bad("标签来源不正确");
        if(!Set.of("FREQUENCY","RATING").contains(sort)) throw bad("标签排序不正确");
        return preferences.analyze(read(id),source,sort);
    }
    private List<ViewerModels.Collection> read(String id) {
        return mapper.collections(id).stream().map(r -> new ViewerModels.Collection(number(r,"subject_id").longValue(),number(r,"status").intValue(),number(r,"personal_rating").intValue(),text(r,"title"),text(r,"original_name"),text(r,"image_url"),text(r,"release_date"),number(r,"episodes").intValue(),number(r,"watched_episodes").intValue(),number(r,"community_score").doubleValue(),text(r,"updated_at"),decode(r.get("personal_tags")),decode(r.get("community_tags")))).toList();
    }
    private Number number(Map<String,Object> row,String key) {return row.get(key) instanceof Number n?n:0;}
    private String text(Map<String,Object> row,String key) {return Objects.toString(row.get(key),"");}
    private List<String> decode(Object value) {try{return BangumiClient.tags(json.readTree(value instanceof byte[] b?new String(b,java.nio.charset.StandardCharsets.UTF_8):String.valueOf(value)));}catch(Exception e){throw new IllegalStateException("Invalid saved tags");}}
    private String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private Binding require(String id) {var b=binding(id);if(b==null) throw new ApiException(HttpStatus.NOT_FOUND,"BINDING_REQUIRED","请先关联Bangumi账号");return b;}
    private static ApiException bad(String message) {return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message);}
    private static ApiException conflict(String message) {return new ApiException(HttpStatus.CONFLICT,"SYNC_CONFLICT",message);}
}
