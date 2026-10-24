package com.animegraph.explore;

import com.animegraph.common.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Public, durable pages shared by comparison and private reports. Reads never fetch remotely. */
@Service
public class SubjectSnapshotService implements ApplicationRunner {
    public record Section(String kind, List<CreditModels.Page> pages, int total, int loadedRows,
                          boolean complete, Integer nextPage, String error) {}
    public record Job(String id, String status, int processed, String error) {}
    public record Snapshot(long subjectId, Section cast, Section staff, Job job) {}
    private final SubjectMetadataService metadata;
    private final MetadataMapper mapper;
    private final ObjectMapper json;
    private final Object[] locks = new Object[128];
    private final Map<Long, Job> jobs = Collections.synchronizedMap(new LinkedHashMap<>());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r -> {var t=new Thread(r,"public-metadata");t.setDaemon(true);return t;});
    public SubjectSnapshotService(SubjectMetadataService metadata, MetadataMapper mapper, ObjectMapper json) {
        this.metadata=metadata;this.mapper=mapper;this.json=json;Arrays.setAll(locks,i->new Object());
    }
    public void run(ApplicationArguments args) {mapper.recover();}
    @PreDestroy void close() {worker.shutdownNow();try{worker.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    private Object lock(long id) {return locks[Math.floorMod(Long.hashCode(id),locks.length)];}
    private void validate(long id) {if(id<1 || id>Integer.MAX_VALUE) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","作品编号不正确");}
    public Snapshot read(long id) {
        validate(id);var job=jobs.get(id);if(job==null)job=mapper.latest(id);return new Snapshot(id,section("CAST",pages(id,"CAST"),null),section("STAFF",pages(id,"STAFF"),null),job);
    }
    public Map<Long,Snapshot> readMany(List<Long> ids) {
        if(ids.isEmpty())return Map.of();if(ids.size()>5000)throw new IllegalArgumentException("Metadata batch too large");
        var sections=new HashMap<Long,Map<String,Section>>();
        for(var row:mapper.many(ids)) {try{List<CreditModels.Page> pages=json.readValue(row.payload(),new TypeReference<List<CreditModels.Page>>(){});sections.computeIfAbsent(row.subjectId(),k->new HashMap<>()).put(row.kind(),section(row.kind(),pages,null));}catch(Exception e){throw new IllegalStateException("Invalid metadata batch",e);}}
        var result=new HashMap<Long,Snapshot>();sections.forEach((id,parts)->result.put(id,new Snapshot(id,parts.getOrDefault("CAST",section("CAST",List.of(),null)),parts.getOrDefault("STAFF",section("STAFF",List.of(),null)),jobs.get(id))));return result;
    }
    public Snapshot start(long id, boolean refresh) {
        validate(id);
        synchronized(lock(id)) {
            var old=jobs.get(id);if(old!=null && Set.of("PENDING","RUNNING").contains(old.status()))return read(id);
            if(!refresh) {var snapshot=read(id);if(snapshot.cast().complete()&&snapshot.staff().complete())return snapshot;}
            if(worker.getQueue().remainingCapacity()==0)throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"METADATA_BUSY","资料补充队列繁忙，请稍后重试");
            var job=new Job(UUID.randomUUID().toString(),"PENDING",0,null);
            mapper.create(job.id(),id);remember(id,job);
            try{worker.execute(()->load(id,job.id(),refresh));}
            catch(RejectedExecutionException e){finish(id,job.id(),"FAILED",0,"资料补充队列繁忙");throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"METADATA_BUSY","资料补充队列繁忙，请稍后重试");}
            return read(id);
        }
    }
    private void remember(long id, Job job) {
        synchronized(jobs){jobs.put(id,job);if(jobs.size()>512) {var iterator=jobs.entrySet().iterator();while(jobs.size()>512&&iterator.hasNext()){var entry=iterator.next();if(!Set.of("RUNNING","PENDING").contains(entry.getValue().status()))iterator.remove();}}}
    }
    private void finish(long id,String job,String status,int processed,String error) {mapper.update(job,status,processed,error);remember(id,new Job(job,status,processed,error));}
    private void load(long id,String job,boolean refresh) {
        int processed=0;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
        finish(id,job,"RUNNING",0,null);
        var errors=new ArrayList<String>();
        try {
            for(String kind:List.of("CAST","STAFF")) {
              try {
                var loaded=new ArrayList<>(pages(id,kind));
                boolean reset=refresh || (!loaded.isEmpty() && loaded.get(0).source().fetchedAt().isBefore(Instant.now().minusSeconds(21600)));
                int count=0;
                while(count<10 && processed<20 && System.nanoTime()<deadline && !Thread.currentThread().isInterrupted()) {
                    var state=section(kind,loaded,null);if(!reset && state.complete())break;
                    Integer next=reset?1:state.nextPage();if(next==null)break;
                    var page=metadata.page(id,kind,next);
                    if(page.source().stale())throw new ApiException(HttpStatus.BAD_GATEWAY,"STALE_METADATA","来源暂不可用，旧分页仍保留，请稍后重试");
                    if(reset) {loaded.clear();reset=false;}
                    if(!loaded.isEmpty() && (loaded.get(0).coverage().total()!=page.coverage().total() || !loaded.get(0).source().adapterVersion().equals(page.source().adapterVersion()) || !loaded.get(0).source().namePolicyVersion().equals(page.source().namePolicyVersion())))
                        throw new ApiException(HttpStatus.CONFLICT,"METADATA_CHANGED","来源名单或规则已变化，请刷新资料重新补充");
                    loaded.add(page);mapper.save(id,kind,encode(loaded));count++;processed++;finish(id,job,"RUNNING",processed,null);
                    if(page.coverage().nextPage()==null)break;
                    Thread.sleep(350);
                }
              } catch(Exception e) {
                if(e instanceof InterruptedException){Thread.currentThread().interrupt();throw e;}
                errors.add((kind.equals("CAST")?"配音":"职员")+"："+(e instanceof ApiException?e.getMessage():"来源暂不可用，已保存分页仍可使用"));
              }
            }
            var snapshot=read(id);finish(id,job,snapshot.cast().complete()&&snapshot.staff().complete()?"COMPLETE":(!errors.isEmpty()&&snapshot.cast().pages().isEmpty()&&snapshot.staff().pages().isEmpty()?"FAILED":"PARTIAL"),processed,errors.isEmpty()?null:String.join("；",errors));
        }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();finish(id,job,"FAILED",processed,e instanceof ApiException?e.getMessage():"资料补充失败，已保存的分页可继续使用");}
    }
    private String encode(List<CreditModels.Page> pages) {try{return json.writeValueAsString(pages);}catch(Exception e){throw new IllegalStateException("Cannot save public metadata",e);}}
    private List<CreditModels.Page> pages(long id,String kind) {
        String raw=mapper.read(id,kind);if(raw==null)return List.of();
        try{return json.readValue(raw,new TypeReference<List<CreditModels.Page>>(){});}catch(Exception e){throw new IllegalStateException("Invalid saved public metadata",e);}
    }
    public static boolean collected(String kind,List<CreditModels.Page> pages) {
        if(pages.isEmpty()) return false;
        var first=pages.getFirst();
        for(int i=0;i<pages.size();i++) {
            var p=pages.get(i);var c=p.coverage();
            if(!p.kind().equals(kind)||!p.subjectUid().equals(first.subjectUid())||c.page()!=i+1
                    ||c.total()!=first.coverage().total()||c.omittedRows()!=0||c.omittedCredits()!=0
                    ||c.unknownCastRows()!=0||c.capped()
                    ||!p.source().adapterVersion().equals(first.source().adapterVersion())
                    ||!p.source().namePolicyVersion().equals(first.source().namePolicyVersion())) return false;
        }
        return pages.getLast().coverage().exhausted()
                && pages.stream().mapToInt(p->p.coverage().returnedRows()).sum()==first.coverage().total();
    }
    static Section section(String kind,List<CreditModels.Page> pages,String error) {
        int rows=pages.stream().mapToInt(p->p.coverage().returnedRows()).sum();
        if(pages.isEmpty())return new Section(kind,pages,0,0,false,1,error);
        var first=pages.getFirst();var last=pages.getLast();
        boolean fresh=pages.stream().noneMatch(p->p.source().stale()||p.source().fetchedAt().isBefore(Instant.now().minusSeconds(21600)));
        return new Section(kind,List.copyOf(pages),first.coverage().total(),rows,collected(kind,pages)&&fresh,last.coverage().nextPage(),error);
    }
}
