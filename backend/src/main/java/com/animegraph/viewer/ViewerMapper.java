package com.animegraph.viewer;

import static com.animegraph.viewer.ViewerModels.*;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ViewerMapper {
    String FIELDS="id,bangumi_id,username,nickname,avatar,binding_mode,revision,sync_status,sync_processed,remote_total,truncated,sync_error,last_synced_at";
    @Select("SELECT "+FIELDS+" FROM viewer_binding WHERE id=#{id}") Binding binding(String id);
    @Insert("INSERT INTO viewer_binding(id,bangumi_id,username,nickname,avatar,binding_mode,revision) VALUES(#{id},#{profile.id},#{profile.username},#{profile.nickname},#{profile.avatar},#{mode},#{revision}) ON DUPLICATE KEY UPDATE bangumi_id=VALUES(bangumi_id),username=VALUES(username),nickname=VALUES(nickname),avatar=VALUES(avatar),binding_mode=VALUES(binding_mode),revision=VALUES(revision),sync_status='IDLE',sync_processed=0,remote_total=0,truncated=false,sync_error=NULL,last_synced_at=NULL")
    void bind(@Param("id")String id,@Param("profile")Profile profile,@Param("mode")String mode,@Param("revision")String revision);
    @Update("UPDATE viewer_binding SET username=#{profile.username},nickname=#{profile.nickname},avatar=#{profile.avatar},binding_mode=#{mode},revision=#{revision},sync_status='IDLE',sync_error=NULL WHERE id=#{id}")
    void reauthorize(@Param("id")String id,@Param("profile")Profile profile,@Param("mode")String mode,@Param("revision")String revision);
    @Delete("DELETE FROM viewer_binding WHERE id=#{id}") void unbind(String id);
    @Delete("DELETE FROM viewer_collection WHERE viewer_id=#{id}") void clear(String id);
    @Delete("DELETE FROM viewer_report WHERE viewer_id=#{id}") void clearReport(String id);
    @Delete("DELETE FROM viewer_recommendation_feedback WHERE viewer_id=#{id}") void clearFeedback(String id);
    @Update("UPDATE viewer_binding SET sync_status='RUNNING',sync_processed=0,sync_error=NULL WHERE id=#{id} AND revision=#{revision} AND sync_status<>'RUNNING'")
    int start(@Param("id")String id,@Param("revision")String revision);
    @Update("UPDATE viewer_binding SET sync_processed=#{processed},remote_total=#{total} WHERE id=#{id} AND revision=#{revision}")
    void progress(@Param("id")String id,@Param("revision")String revision,@Param("processed")int processed,@Param("total")int total);
    @Update("UPDATE viewer_binding SET sync_status=#{status},sync_error=#{error},truncated=#{truncated},updated_at=CURRENT_TIMESTAMP(6),last_synced_at=CASE WHEN #{status}='SUCCEEDED' THEN CURRENT_TIMESTAMP(6) ELSE last_synced_at END WHERE id=#{id} AND revision=#{revision}")
    void finish(@Param("id")String id,@Param("revision")String revision,@Param("status")String status,@Param("error")String error,@Param("truncated")boolean truncated);
    @Update("UPDATE viewer_binding SET sync_status='INTERRUPTED',sync_error='同步因服务重启中断，请重新同步' WHERE sync_status='RUNNING'") void recover();
    @Insert("INSERT INTO viewer_collection(viewer_id,subject_id,status,personal_rating,title,original_name,image_url,release_date,episodes,watched_episodes,community_score,updated_at,personal_tags,community_tags) VALUES(#{viewer},#{item.subjectId},#{item.status},#{item.personalRating},#{item.title},#{item.originalName},#{item.imageUrl},#{item.releaseDate},#{item.episodes},#{item.watchedEpisodes},#{item.communityScore},#{item.updatedAt},#{personal},#{community})")
    void insert(@Param("viewer")String viewer,@Param("item")Collection item,@Param("personal")String personal,@Param("community")String community);
    @Select("SELECT * FROM viewer_collection WHERE viewer_id=#{id} ORDER BY updated_at DESC,subject_id DESC") List<Map<String,Object>> collections(String id);
}
