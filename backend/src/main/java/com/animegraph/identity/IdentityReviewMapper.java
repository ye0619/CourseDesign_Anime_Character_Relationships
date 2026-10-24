package com.animegraph.identity;

import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface IdentityReviewMapper {
    record Identity(long id,String provider,String providerId,String entityType,String canonicalUid,String status,
                    String sourceUrl,String sourceRevision,String matchMethod,String licenseLabel,long version,
                    String evidenceText,String reviewReason,Long reviewedBy,Instant updatedAt) {}
    String FIELDS="id,provider,provider_id,entity_type,canonical_uid,status,source_url,source_revision,match_method,license_label,version,evidence_text,review_reason,reviewed_by,updated_at";
    @Select("SELECT "+FIELDS+" FROM external_identity WHERE id=#{id}") Identity find(long id);
    @Select("SELECT "+FIELDS+" FROM external_identity WHERE (#{provider}='' OR provider=#{provider}) AND (#{status}='' OR status=#{status}) AND (#{beforeId}=0 OR id<#{beforeId}) ORDER BY id DESC LIMIT #{limit}")
    List<Identity> list(@Param("provider")String provider,@Param("status")String status,@Param("beforeId")long beforeId,@Param("limit")int limit);
    @Insert("INSERT INTO external_identity(provider,provider_id,entity_type,canonical_uid,status,source_url,source_revision,match_method,license_label,evidence_text,review_reason,reviewed_by) VALUES(#{provider},#{providerId},#{entityType},#{canonicalUid},'CANDIDATE',#{sourceUrl},#{sourceRevision},#{matchMethod},#{licenseLabel},#{evidenceText},#{reason},#{actor})")
    @Options(useGeneratedKeys=true,keyProperty="id") void insert(Map<String,Object> row);
    @Update("UPDATE external_identity SET canonical_uid=#{uid},status=#{status},source_revision=#{revision},evidence_text=#{evidence},source_url=#{url},license_label=#{license},match_method=COALESCE(#{method},match_method),review_reason=#{reason},reviewed_by=#{actor},version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND version=#{version}")
    int update(@Param("id")long id,@Param("version")long version,@Param("uid")String uid,@Param("status")String status,@Param("revision")String revision,@Param("evidence")String evidence,@Param("url")String url,@Param("license")String license,@Param("reason")String reason,@Param("actor")long actor,@Param("method")String method);
    @Insert("INSERT INTO external_identity_event(identity_id,action,actor_id,reason,before_state,after_state) VALUES(#{id},#{action},#{actor},#{reason},#{before},#{after})")
    void event(@Param("id")long id,@Param("action")String action,@Param("actor")long actor,@Param("reason")String reason,@Param("before")String before,@Param("after")String after);
    @Select("SELECT id,action,actor_id AS actorId,reason,before_state AS beforeState,after_state AS afterState,created_at AS createdAt FROM external_identity_event WHERE identity_id=#{id} ORDER BY id DESC LIMIT 100")
    List<Map<String,Object>> history(long id);
}
