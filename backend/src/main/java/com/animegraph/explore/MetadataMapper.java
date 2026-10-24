package com.animegraph.explore;

import org.apache.ibatis.annotations.*;

@Mapper
public interface MetadataMapper {
    @Select("SELECT id,status,processed,error_message AS error FROM public_metadata_task WHERE subject_id=#{id} ORDER BY created_at DESC LIMIT 1")
    SubjectSnapshotService.Job latest(@Param("id") long id);
    record Stored(long subjectId,String kind,String payload) {}
    @Select("<script>SELECT subject_id,kind,payload FROM public_credit_snapshot WHERE subject_id IN <foreach item='value' collection='ids' open='(' separator=',' close=')'>#{value}</foreach></script>")
    java.util.List<Stored> many(@Param("ids") java.util.List<Long> ids);
    @Select("SELECT payload FROM public_credit_snapshot WHERE subject_id=#{id} AND kind=#{kind}")
    String read(@Param("id") long id, @Param("kind") String kind);
    @Insert("INSERT INTO public_credit_snapshot(subject_id,kind,payload) VALUES(#{id},#{kind},#{payload}) ON DUPLICATE KEY UPDATE payload=#{payload},updated_at=CURRENT_TIMESTAMP(6)")
    void save(@Param("id") long id, @Param("kind") String kind, @Param("payload") String payload);
    @Insert("INSERT INTO public_metadata_task(id,subject_id,status) VALUES(#{job},#{id},'PENDING')")
    void create(@Param("job") String job, @Param("id") long id);
    @Update("UPDATE public_metadata_task SET status=#{status},processed=#{processed},error_message=#{error},finished_at=CASE WHEN #{status} IN ('PENDING','RUNNING') THEN NULL ELSE CURRENT_TIMESTAMP(6) END WHERE id=#{job}")
    void update(@Param("job") String job, @Param("status") String status, @Param("processed") int processed, @Param("error") String error);
    @Update("UPDATE public_metadata_task SET status='FAILED',error_message='服务重启，已保存分页可继续补充',finished_at=CURRENT_TIMESTAMP(6) WHERE status IN ('PENDING','RUNNING')")
    void recover();
}
