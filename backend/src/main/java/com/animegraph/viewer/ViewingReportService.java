package com.animegraph.viewer;

import com.animegraph.common.ApiException;
import com.animegraph.explore.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ViewingReportService {
    public record PersonStat(String uid,String name,String role,Integer providerType,int workCount,int ratedCount,
                             Double averageRating,Double adjustedRating,List<ViewerModels.Collection> works,List<String> roles) {}
    public record WorkCoverage(long subjectId,String source,String version,ReportCredits.Section staff,ReportCredits.Section cast) {}
    public record SectionCounts(Map<String,Integer> collection,Map<String,Integer> freshness) {}
    public record CoverageSummary(SectionCounts staff,SectionCounts cast) {}
    public record ScopeReport(int completed,int rated,int metadataConsidered,int metadataCovered,List<PersonStat> personnel,
                              List<PersonStat> overall,CoverageSummary coverageSummary,int personnelTotal,int overallTotal) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Report(String version,Instant generatedAt,int completed,int rated,int metadataConsidered,int metadataCovered,
                         List<PersonStat> personnel,List<String> methodology,List<PersonStat> overall,List<WorkCoverage> coverage,
                         CoverageSummary coverageSummary,int personnelTotal,int overallTotal, List<ProfileDetails.Voice> voices, List<ProfileDetails.Company> companies,
                         List<ProfileDetails.Behavior> behaviors) {}
    public record Task(String status,int processed,int total,String error) {}
    public record Result(Report report,Task task,boolean collectionTruncated) {}

    private static class Active {
        final String revision;
        final Instant at;
        final Map<Long,Integer> attempts=new ConcurrentHashMap<>();
        volatile boolean cancel;
        volatile Task task=new Task("PENDING",0,0,null);
        Active(ViewerModels.Binding binding){revision=binding.revision();at=binding.lastSyncedAt();}
    }
    private final ViewerService viewers;
    private final ViewerAnalysisMapper mapper;
    private final SubjectSnapshotService snapshots;
    private final ObjectMapper json;
    private final LocalReportCredits local;
    private final Map<String,Active> tasks=new ConcurrentHashMap<>();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),
            operation->{var thread=new Thread(operation,"viewer-report");thread.setDaemon(true);return thread;});

    public ViewingReportService(ViewerService viewers,ViewerAnalysisMapper mapper,SubjectSnapshotService snapshots,ObjectMapper json,
                                LocalReportCredits local){
        this.viewers=viewers;this.mapper=mapper;this.snapshots=snapshots;this.json=json;this.local=local;
    }
    @PreDestroy void close(){
        tasks.values().forEach(task->task.cancel=true);worker.shutdownNow();
        try{worker.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    public Result read(String id){
        var state=viewers.snapshot(id);var binding=state.binding();
        String raw=mapper.report(id,binding.revision(),binding.lastSyncedAt());
        Report report=null;
        try{if(raw!=null)report=json.readValue(raw,Report.class);}
        catch(Exception e){throw new IllegalStateException("Invalid report",e);}
        var now=viewers.binding(id);
        if(now==null||!Objects.equals(binding.revision(),now.revision())||!Objects.equals(binding.lastSyncedAt(),now.lastSyncedAt()))
            throw new ApiException(HttpStatus.CONFLICT,"VIEWER_CHANGED","关联或片单已变化，请重新读取报告");
        var task=tasks.get(id);
        return new Result(report,matches(task,binding)?task.task:null,binding.truncated());
    }
    public Result start(String id){return start(id,true);}
    public synchronized Result start(String id,boolean supplement){
        var state=viewers.snapshot(id);var old=tasks.get(id);
        if(matches(old,state.binding())&&Set.of("PENDING","RUNNING").contains(old.task.status()))return read(id);
        if(tasks.size()>512)tasks.entrySet().removeIf(entry->!Set.of("PENDING","RUNNING").contains(entry.getValue().task.status()));
        var active=new Active(state.binding());
        if(matches(old,state.binding()))active.attempts.putAll(old.attempts);
        tasks.put(id,active);
        try{worker.execute(()->build(id,state,active,supplement));}
        catch(RejectedExecutionException e){
            tasks.remove(id,active);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"REPORT_BUSY","报告队列繁忙，请稍后重试");
        }
        return read(id);
    }
    public synchronized Result cancel(String id){
        var binding=viewers.snapshot(id).binding();var task=tasks.get(id);
        if(matches(task,binding))synchronized(task){
            task.cancel=true;task.task=new Task("CANCELLED",task.task.processed(),task.task.total(),null);
        }
        return read(id);
    }
    private boolean matches(Active active,ViewerModels.Binding binding){
        return active!=null&&active.revision.equals(binding.revision())&&Objects.equals(active.at,binding.lastSyncedAt());
    }
    private boolean current(String id,Active active){
        var binding=viewers.binding(id);
        return !active.cancel&&!Thread.currentThread().isInterrupted()&&binding!=null&&matches(active,binding)&&tasks.get(id)==active;
    }
    private void build(String id,ViewerService.ViewerData state,Active task,boolean supplement){
        var completed=state.collections();
        int processed=0,total=0;
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
        String error=null;
        try{
            if(!current(id,task))return;
            var saved=snapshots.readMany(completed.stream().map(ViewerModels.Collection::subjectId).toList());
            var batch=completed.stream().filter(work->{
                var snapshot=saved.get(work.subjectId());
                return supplement&&(snapshot==null||!snapshot.cast().complete()||!snapshot.staff().complete());
            }).sorted(Comparator.comparingInt(work->task.attempts.getOrDefault(work.subjectId(),0))).limit(100).toList();
            total=batch.size();progress(id,task,0,total,null);
            for(var item:batch){
                if(!current(id,task))return;
                if(System.nanoTime()>deadline){error="本批达到120秒期限，已保存资料可继续补充";break;}
                task.attempts.merge(item.subjectId(),1,Integer::sum);
                var snapshot=snapshots.read(item.subjectId());
                if(!snapshot.cast().complete()||!snapshot.staff().complete()){
                    try{
                        snapshot=snapshots.start(item.subjectId(),false);
                        while(current(id,task)&&System.nanoTime()<deadline&&snapshot.job()!=null
                                &&Set.of("PENDING","RUNNING").contains(snapshot.job().status())){
                            Thread.sleep(500);snapshot=snapshots.read(item.subjectId());
                        }
                    }catch(ApiException e){error=e.getMessage();}
                }
                if(System.nanoTime()>deadline)error="本批达到120秒期限，已保存资料可继续补充";
                processed++;progress(id,task,processed,total,error);
            }
            if(!current(id,task))return;
            var inputs=new HashMap<Long,ReportCredits.Work>();
            snapshots.readMany(completed.stream().map(ViewerModels.Collection::subjectId).toList())
                    .forEach((subject,snapshot)->inputs.put(subject,ReportCredits.fromSnapshot(snapshot)));
            local.read(completed.stream().map(ViewerModels.Collection::subjectId).toList()).forEach((subject,credits)->inputs.merge(subject,credits,ReportCredits::merge));
            var report=ViewingReportAnalyzer.analyze(completed,inputs);
            synchronized(task){
                if(!current(id,task))return;
                int written=mapper.save(id,task.revision,task.at,json.writeValueAsString(report));
                if(written==0)return;
                boolean allCovered=completed.stream().allMatch(work->{var input=inputs.get(work.subjectId());return input!=null&&input.staff().collectionStatus().equals("COMPLETE")&&input.cast().collectionStatus().equals("COMPLETE");});
                task.task=new Task(allCovered?"COMPLETE":"PARTIAL",processed,total,error);
            }
        }catch(Exception e){
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            synchronized(task){if(current(id,task))task.task=new Task("FAILED",processed,total,"报告生成失败，原有报告保留");}
        }
    }
    private void progress(String id,Active task,int processed,int total,String error){
        synchronized(task){if(current(id,task))task.task=new Task("RUNNING",processed,total,error);}
    }
    static Report analyze(List<ViewerModels.Collection> completed,List<SubjectSnapshotService.Snapshot> snapshots){
        var inputs=new HashMap<Long,ReportCredits.Work>();
        snapshots.forEach(snapshot->inputs.put(snapshot.subjectId(),ReportCredits.fromSnapshot(snapshot)));
        return ViewingReportAnalyzer.analyze(completed,inputs);
    }
}
