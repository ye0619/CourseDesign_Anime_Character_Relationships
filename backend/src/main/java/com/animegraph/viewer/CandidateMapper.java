package com.animegraph.viewer;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface CandidateMapper {
    @Select("SELECT payload FROM recommendation_candidate ORDER BY subject_id LIMIT 1000") List<String> all();
    @Insert("INSERT INTO recommendation_candidate(subject_id,payload) VALUES(#{id},#{payload}) ON DUPLICATE KEY UPDATE payload=#{payload},fetched_at=CURRENT_TIMESTAMP(6)")
    void save(@Param("id")long id,@Param("payload")String payload);
}
