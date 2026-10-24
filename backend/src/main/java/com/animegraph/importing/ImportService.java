package com.animegraph.importing;

import com.animegraph.audit.AuditService;
import com.animegraph.common.*;
import com.animegraph.graph.GraphStore;
import com.fasterxml.jackson.databind.*;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.neo4j.driver.exceptions.Neo4jException;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

@Service
public class ImportService {
    private static final Logger LOG=LoggerFactory.getLogger(ImportService.class);
    private final ImportMapper mapper;
    private final GraphStore graph;
    private final ObjectMapper json;
    private final AuditService audit;
    private final Path storage;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon(true).name("graph-import").factory());
    public ImportService(ImportMapper mapper,GraphStore graph,ObjectMapper json,AuditService audit,@Value("${app.import.storage:../.local/imports}")String storage) {
        this.mapper=mapper;this.graph=graph;this.json=json;this.audit=audit;this.storage=Path.of(storage).toAbsolutePath().normalize();
    }
    @EventListener(ApplicationReadyEvent.class) public void recover() {mapper.recover();}
    @PreDestroy public void close() {worker.shutdownNow();}
    public record PackageData(String source,JsonNode root,List<Item> items) {}
    public record Item(String key,JsonNode data,boolean entity) {}
    public PackageData parse(byte[] bytes) {
        if(bytes.length>20*1024*1024) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"FILE_TOO_LARGE","文件不得超过20MB");
        try {
            JsonNode root=json.readTree(bytes);
            if(root==null || !root.isObject() || !root.path("schemaVersion").isIntegralNumber() || root.path("schemaVersion").asInt()!=1 || !root.path("entities").isArray() || !root.path("relations").isArray()) throw GraphStore.bad("导入包需包含schemaVersion=1、entities和relations数组");
            String source=GraphStore.required(root,"source");GraphStore.uid(GraphStore.Kind.ANIME,source,"validation");
            List<Item> items=new ArrayList<>();
            int index=0;for(JsonNode node:root.get("entities")) items.add(new Item("entity:"+index++,node,true));
            index=0;for(JsonNode node:root.get("relations")) items.add(new Item("relation:"+index++,node,false));
            if(items.isEmpty() || items.size()>50000) throw GraphStore.bad("导入记录数需为1—50000条");
            graph.validatePackageScope(source,root);
            return new PackageData(source,root,List.copyOf(items));
        } catch(IOException e) {throw GraphStore.bad("文件不是有效UTF-8 JSON");}
    }
    @Transactional
    public ImportMapper.Task create(byte[] bytes,Long actor,Long retryOf) {
        var data=parse(bytes);
        var task=new ImportMapper.NewTask();task.source=data.source();task.fileHash=GraphStore.hash(bytes);
        task.payloadFile=UUID.randomUUID()+".json";task.totalCount=data.items().size();task.retryOf=retryOf;task.createdBy=actor;
        try {mapper.create(task);} catch(DuplicateKeyException e) {throw new ApiException(HttpStatus.CONFLICT,"IMPORT_ALREADY_RUNNING","相同文件已有活动导入任务");}
        try {Files.createDirectories(storage);Files.write(storage.resolve(task.payloadFile),bytes,StandardOpenOption.CREATE_NEW);}
        catch(IOException e) {throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","导入文件无法保存");}
        for(Item item:data.items()) mapper.createEntry(task.id,item.key(),item.entity()?"ENTITY":item.data().path("type").asText("UNKNOWN"));
        audit.record(actor,"IMPORT_CREATE","IMPORT",String.valueOf(task.id),"导入记录 "+data.items().size()+" 条，来源 "+data.source());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {worker.submit(() -> run(task.id));}
        });
        return mapper.task(task.id);
    }
    @Transactional
    public ImportMapper.Task retry(long id,Long actor) {
        var old=required(id);
        if(!Set.of("FAILED","PARTIAL_FAILED","INTERRUPTED").contains(old.status())) throw new ApiException(HttpStatus.CONFLICT,"IMPORT_NOT_RETRYABLE","仅失败、部分失败或中断的任务可以重试");
        try {return create(Files.readAllBytes(payload(old.payloadFile())),actor,id);}
        catch(IOException e) {throw new ApiException(HttpStatus.NOT_FOUND,"PAYLOAD_NOT_FOUND","原始导入文件不存在");}
    }
    public ImportMapper.Task required(long id) {
        var task=mapper.task(id);if(task==null) throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","导入任务不存在");return task;
    }
    public PageResult<ImportMapper.Task> list(int page,int size) {return new PageResult<>(mapper.list(PageResult.offset(page,size),size),page,size,mapper.count());}
    public PageResult<ImportMapper.Entry> entries(long id,String status,int page,int size) {
        required(id);return new PageResult<>(mapper.entries(id,status,PageResult.offset(page,size),size),page,size,mapper.entryCount(id,status));
    }
    private Path payload(String filename) {
        if(!filename.matches("[a-f0-9-]{36}\\.json")) throw new IllegalStateException("Invalid stored payload filename");
        return storage.resolve(filename);
    }
    private void run(long id) {
        try {
            var task=required(id);var data=parse(Files.readAllBytes(payload(task.payloadFile())));
            mapper.start(id);
            var ordered=new ArrayList<>(data.items());
            ordered.sort(Comparator.comparingInt(item -> item.entity()?0:switch(item.data().path("type").asText()) {case "APPEARS_IN" -> 1;case "VOICED","CHARACTER_RELATION" -> 3;default -> 2;}));
            boolean fatal=false;int processed=0;
            for(Item item:ordered) {
                if(Thread.currentThread().isInterrupted()) {mapper.finish(id,"INTERRUPTED","导入被中断，可重试");return;}
                mapper.entry(id,item.key(),"RUNNING",1,null,null);
                try {
                    if(item.entity()) graph.upsertEntity(data.source(),item.data(),String.valueOf(id));
                    else graph.upsertRelation(data.source(),item.data(),String.valueOf(id));
                    mapper.entry(id,item.key(),"SUCCEEDED",0,null,null);
                } catch(ApiException | IllegalArgumentException exception) {
                    String message=exception.getMessage();
                    mapper.entry(id,item.key(),"FAILED",0,"INVALID_RECORD",message.length()>950?message.substring(0,950):message);
                } catch(Neo4jException exception) {
                    LOG.error("Graph write failed taskId={} record={}",id,item.key(),exception);
                    mapper.entry(id,item.key(),"FAILED",0,"GRAPH_UNAVAILABLE","图数据库不可用，请检查服务后重试");fatal=true;break;
                }
                if(++processed % 25==0) mapper.counts(id);
            }
            mapper.counts(id);var finalTask=required(id);
            mapper.finish(id,fatal?"FAILED":finalTask.failedCount()>0?"PARTIAL_FAILED":"SUCCEEDED",fatal?"图数据库不可用":finalTask.failedCount()>0?"存在失败记录，详见记录列表":null);
        } catch(Exception exception) {
            LOG.error("Import task failed taskId={}",id,exception);
            try {mapper.counts(id);mapper.finish(id,"FAILED","任务执行失败，请检查服务并重试");} catch(Exception ignored) {LOG.error("Cannot persist import failure taskId={}",id);}
        }
    }
}
