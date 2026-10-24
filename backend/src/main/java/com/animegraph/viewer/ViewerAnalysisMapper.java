package com.animegraph.viewer;
import org.apache.ibatis.annotations.*;
import java.time.Instant;
import java.util.List;
@Mapper
public interface ViewerAnalysisMapper {
    @Select("SELECT payload FROM viewer_report WHERE viewer_id=#{id} AND binding_revision=#{revision} AND collection_time <=> #{at}")
    String report(@Param("id")String id,@Param("revision")String revision,@Param("at")Instant at);
    @Insert("INSERT INTO viewer_report(viewer_id,binding_revision,collection_time,payload) SELECT id,revision,last_synced_at,#{payload} FROM viewer_binding WHERE id=#{id} AND revision=#{revision} AND last_synced_at <=> #{at} ON DUPLICATE KEY UPDATE binding_revision=#{revision},collection_time=#{at},payload=#{payload},generated_at=CURRENT_TIMESTAMP(6)")
    int save(@Param("id")String id,@Param("revision")String revision,@Param("at")Instant at,@Param("payload")String payload);
    @Select("SELECT subject_id FROM viewer_recommendation_feedback WHERE viewer_id=#{id} AND feedback_type='NOT_INTERESTED'") List<Long> excluded(String id);
    @Insert("INSERT INTO viewer_recommendation_feedback(viewer_id,subject_id,feedback_type) SELECT id,#{subject},'NOT_INTERESTED' FROM viewer_binding WHERE id=#{id} AND revision=#{revision} ON DUPLICATE KEY UPDATE feedback_type='NOT_INTERESTED',updated_at=CURRENT_TIMESTAMP(6)")
    int exclude(@Param("id")String id,@Param("revision")String revision,@Param("subject")long subject);
    @Delete("DELETE f FROM viewer_recommendation_feedback f JOIN viewer_binding b ON b.id=f.viewer_id WHERE f.viewer_id=#{id} AND b.revision=#{revision} AND f.subject_id=#{subject}")
    void restore(@Param("id")String id,@Param("revision")String revision,@Param("subject")long subject);
}
