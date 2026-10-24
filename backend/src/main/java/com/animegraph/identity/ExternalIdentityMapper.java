package com.animegraph.identity;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper
public interface ExternalIdentityMapper {
    @Select("SELECT provider,provider_id,entity_type,canonical_uid,status,source_url,source_revision,match_method,license_label FROM external_identity WHERE canonical_uid=#{uid} AND status IN ('CANDIDATE','REVIEWED') ORDER BY provider,provider_id LIMIT 30") List<Map<String,Object>> identities(String uid);
    @Insert("INSERT INTO external_identity(provider,provider_id,entity_type,canonical_uid,status,source_url,source_revision,match_method,license_label) VALUES(#{provider},#{providerId},#{entityType},#{canonicalUid},#{status},#{sourceUrl},#{sourceRevision},#{matchMethod},#{licenseLabel}) ON DUPLICATE KEY UPDATE provider=provider")
    void seed(Map<String,Object> row);
}
