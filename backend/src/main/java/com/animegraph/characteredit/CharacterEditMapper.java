package com.animegraph.characteredit;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CharacterEditMapper {
    record Row(String uid,long version,String payload) {}
    @Select("SELECT uid,version,payload FROM character_edit WHERE uid=#{uid}") Row read(String uid);
    @Select("SELECT uid,version,payload FROM character_edit WHERE uid=#{uid} FOR UPDATE") Row lock(String uid);
    @Insert("INSERT IGNORE INTO character_edit(uid,version,payload) VALUES(#{uid},0,'{}')") void ensure(String uid);
    @Update("UPDATE character_edit SET payload=#{payload},version=version+1 WHERE uid=#{uid} AND version=#{version}") int replace(Row row);
    @Select("<script>SELECT uid,version,payload FROM character_edit WHERE uid IN <foreach collection='uids' item='uid' open='(' separator=',' close=')'>#{uid}</foreach></script>") List<Row> many(@Param("uids")List<String> uids);
    @Insert("INSERT INTO character_edit_backup(id,uid,version,payload) VALUES(#{id},#{row.uid},#{row.version},#{row.payload})") void backup(@Param("id")String id,@Param("row")Row row);
    @Select("SELECT id,version,created_at AS createdAt FROM character_edit_backup WHERE uid=#{uid} ORDER BY created_at DESC LIMIT 20") List<Map<String,Object>> backups(String uid);
    @Select("SELECT uid,version,payload FROM character_edit_backup WHERE uid=#{uid} AND id=#{id}") Row backupRead(@Param("uid")String uid,@Param("id")String id);
    @Delete("DELETE FROM character_edit_backup WHERE uid=#{uid} AND id NOT IN (SELECT id FROM (SELECT id FROM character_edit_backup WHERE uid=#{uid} ORDER BY created_at DESC,id DESC LIMIT 20) recent)") void trim(String uid);
    @Select("SELECT uid FROM character_edit WHERE JSON_SEARCH(payload,'one',CONCAT('%',#{keyword},'%'),NULL,'$.fields.names[*].text') IS NOT NULL ORDER BY uid LIMIT 1001") List<String> search(String keyword);
}
