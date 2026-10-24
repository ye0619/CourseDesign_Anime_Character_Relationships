package com.animegraph.importing;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ImportMapper {
    String FIELDS="id,source,file_hash,payload_file,schema_version,status,total_count,success_count,failed_count,checkpoint,retry_of,created_by,started_at,finished_at,created_at,error_summary";
    record Task(long id,String source,String fileHash,String payloadFile,int schemaVersion,String status,int totalCount,int successCount,int failedCount,int checkpoint,Long retryOf,Long createdBy,Instant startedAt,Instant finishedAt,Instant createdAt,String errorSummary) {}
    record Entry(long id,long taskId,String recordKey,String recordType,String status,int attempts,String errorCode,String errorMessage) {}
    class NewTask {public Long id;public String source;public String fileHash;public String payloadFile;public int totalCount;public Long retryOf;public Long createdBy;}
    @Insert("INSERT INTO import_task(source,file_hash,payload_file,schema_version,total_count,retry_of,created_by) VALUES(#{source},#{fileHash},#{payloadFile},1,#{totalCount},#{retryOf},#{createdBy})")
    @Options(useGeneratedKeys=true,keyProperty="id") void create(NewTask task);
    @Select("SELECT "+FIELDS+" FROM import_task WHERE id=#{id}") Task task(long id);
    @Select("SELECT "+FIELDS+" FROM import_task ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<Task> list(@Param("offset")int offset,@Param("size")int size);
    @Select("SELECT count(*) FROM import_task") long count();
    @Insert("INSERT INTO import_record(task_id,record_key,record_type) VALUES(#{task},#{key},#{type})")
    void createEntry(@Param("task")long task,@Param("key")String key,@Param("type")String type);
    @Update("UPDATE import_record SET status=#{status},attempts=attempts+#{attempt},error_code=#{code},error_message=#{message} WHERE task_id=#{task} AND record_key=#{key}")
    void entry(@Param("task")long task,@Param("key")String key,@Param("status")String status,@Param("attempt")int attempt,@Param("code")String code,@Param("message")String message);
    @Update("UPDATE import_task SET status='RUNNING',started_at=CURRENT_TIMESTAMP(6) WHERE id=#{id}") void start(long id);
    @Update("UPDATE import_task SET success_count=(SELECT count(*) FROM import_record WHERE task_id=#{id} AND status='SUCCEEDED'),failed_count=(SELECT count(*) FROM import_record WHERE task_id=#{id} AND status='FAILED'),checkpoint=(SELECT count(*) FROM import_record WHERE task_id=#{id} AND status IN ('SUCCEEDED','FAILED')) WHERE id=#{id}")
    void counts(long id);
    @Update("UPDATE import_task SET status=#{status},error_summary=#{message},finished_at=CURRENT_TIMESTAMP(6) WHERE id=#{id}")
    void finish(@Param("id")long id,@Param("status")String status,@Param("message")String message);
    @Update("UPDATE import_task SET status='INTERRUPTED',error_summary='服务重启，等待管理员重试',finished_at=CURRENT_TIMESTAMP(6) WHERE status IN ('RUNNING','PENDING')") void recover();
    @Select("<script>SELECT id,task_id,record_key,record_type,status,attempts,error_code,error_message FROM import_record WHERE task_id=#{task}<if test='status != null and status != &quot;&quot;'> AND status=#{status}</if> ORDER BY id LIMIT #{size} OFFSET #{offset}</script>")
    List<Entry> entries(@Param("task")long task,@Param("status")String status,@Param("offset")int offset,@Param("size")int size);
    @Select("<script>SELECT count(*) FROM import_record WHERE task_id=#{task}<if test='status != null and status != &quot;&quot;'> AND status=#{status}</if></script>")
    long entryCount(@Param("task")long task,@Param("status")String status);
}
