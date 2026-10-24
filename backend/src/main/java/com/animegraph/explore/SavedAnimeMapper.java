package com.animegraph.explore;

import org.apache.ibatis.annotations.*;

@Mapper
public interface SavedAnimeMapper {
    record Row(long sourceId,String payload,String status,String errorMessage,java.time.Instant updatedAt) {}
    @Select("SELECT source_id,payload,status,error_message,updated_at FROM saved_anime WHERE source='bangumi' AND source_id=#{id}")
    Row read(long id);
    @Select("SELECT source,source_id,payload,previous_payload,status,error_message,CAST(updated_at AS CHAR) AS updated_at,CAST(requested_at AS CHAR) AS requested_at FROM saved_anime WHERE source='bangumi' AND source_id=#{id}")
    java.util.Map<String,Object> deletionSnapshot(long id);
    @Select("SELECT COUNT(*) FROM import_task WHERE source=#{source} AND status IN ('PENDING','RUNNING')")
    int activeImports(String source);
    @Delete("DELETE FROM saved_anime WHERE source='bangumi' AND source_id=#{id}")
    int delete(long id);
    @Insert("INSERT INTO saved_anime(source,source_id,status) VALUES('bangumi',#{id},'PENDING') ON DUPLICATE KEY UPDATE status='PENDING',error_message=NULL,requested_at=CURRENT_TIMESTAMP(6)")
    void request(long id);
    @Update("UPDATE saved_anime SET previous_payload=payload,payload=#{payload},status=#{status},error_message=#{error},updated_at=CURRENT_TIMESTAMP(6) WHERE source='bangumi' AND source_id=#{id}")
    void save(@Param("id")long id,@Param("payload")String payload,@Param("status")String status,@Param("error")String error);
    @Update("UPDATE saved_anime SET status=#{status},error_message=#{error} WHERE source='bangumi' AND source_id=#{id}")
    void status(@Param("id")long id,@Param("status")String status,@Param("error")String error);
    @Update("UPDATE saved_anime SET status='PARTIAL',error_message='服务重启，已保存内容保留；可重新更新' WHERE status IN ('PENDING','RUNNING')")
    void recover();
}
