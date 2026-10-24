package com.animegraph.identity;

import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface IdentityNameMapper {
    record Name(long identityId,int ordinal,String name,String searchKey,String locale,String evidenceText,String canonicalUid,
                String sourceUrl,String sourceRevision,String licenseLabel,long reviewVersion,Instant reviewedAt,String provider,String providerId) {}
    String FIELDS="n.identity_id,n.ordinal,n.name,n.search_key,n.locale,n.evidence_text,n.canonical_uid,n.source_url,n.source_revision,n.license_label,n.review_version,n.reviewed_at,i.provider,i.provider_id";
    String ACTIVE="i.status='REVIEWED' AND i.version=n.review_version AND i.canonical_uid=n.canonical_uid";
    @Select("SELECT "+FIELDS+" FROM external_identity_name n JOIN external_identity i ON i.id=n.identity_id WHERE i.id=#{id} ORDER BY n.ordinal") List<Name> stored(long id);
    @Delete("DELETE FROM external_identity_name WHERE identity_id=#{id}") void delete(long id);
    @Insert("INSERT INTO external_identity_name(identity_id,ordinal,name,search_key,locale,evidence_text,canonical_uid,source_url,source_revision,license_label,review_version) VALUES(#{identityId},#{ordinal},#{name},#{searchKey},#{locale},#{evidenceText},#{canonicalUid},#{sourceUrl},#{sourceRevision},#{licenseLabel},#{reviewVersion})") void insert(Name name);
    @Select("<script>SELECT "+FIELDS+" FROM external_identity_name n JOIN external_identity i ON i.id=n.identity_id WHERE "+ACTIVE+" AND n.canonical_uid IN <foreach collection='uids' item='uid' open='(' separator=',' close=')'>#{uid}</foreach> ORDER BY n.canonical_uid,n.identity_id,n.ordinal LIMIT 4801</script>") List<Name> active(@Param("uids")List<String> uids);
    @Select("SELECT DISTINCT n.canonical_uid FROM external_identity_name n JOIN external_identity i ON i.id=n.identity_id WHERE "+ACTIVE+" AND i.entity_type=#{type} AND LOCATE(#{keyword},n.search_key)>0 ORDER BY n.canonical_uid LIMIT 1001") List<String> search(@Param("type")String type,@Param("keyword")String keyword);
}
