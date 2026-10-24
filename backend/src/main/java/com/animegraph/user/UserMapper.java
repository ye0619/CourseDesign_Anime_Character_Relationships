package com.animegraph.user;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {
    String FIELDS = "id,username,password_hash,display_name,enabled,auth_version,created_at";
    @Select("SELECT " + FIELDS + " FROM sys_user WHERE username=LOWER(#{username})")
    UserRecord findByUsername(String username);
    @Select("SELECT " + FIELDS + " FROM sys_user WHERE id=#{id}")
    UserRecord findById(long id);
    @Select("SELECT r.code FROM sys_role r JOIN sys_user_role ur ON r.id=ur.role_id WHERE ur.user_id=#{id} ORDER BY r.code")
    List<String> roles(long id);
    @Insert("INSERT INTO sys_user(username,password_hash,display_name) VALUES(#{username},#{passwordHash},#{displayName})")
    void insert(@Param("username") String username,@Param("passwordHash") String passwordHash,@Param("displayName") String displayName);
    @Insert("INSERT INTO sys_user_role(user_id,role_id) SELECT #{id},id FROM sys_role WHERE code=#{role}")
    void assignRole(@Param("id") long id,@Param("role") String role);
    @Delete("DELETE FROM sys_user_role WHERE user_id=#{id}") void clearRoles(long id);
    @Select("SELECT " + FIELDS + " FROM sys_user ORDER BY id LIMIT #{size} OFFSET #{offset}")
    List<UserRecord> list(@Param("offset") int offset,@Param("size") int size);
    @Select("SELECT count(*) FROM sys_user") long count();
    @Select("SELECT id FROM sys_role WHERE code='ADMIN' FOR UPDATE") long lockAdminRole();
    @Select("SELECT count(DISTINCT u.id) FROM sys_user u JOIN sys_user_role ur ON u.id=ur.user_id JOIN sys_role r ON r.id=ur.role_id WHERE u.enabled=true AND r.code='ADMIN'")
    long enabledAdminCount();
    @Update("UPDATE sys_user SET display_name=#{displayName},enabled=#{enabled},auth_version=auth_version+1 WHERE id=#{id}")
    void update(@Param("id") long id,@Param("displayName") String displayName,@Param("enabled") boolean enabled);
    @Update("UPDATE sys_user SET password_hash=#{hash},auth_version=auth_version+1 WHERE id=#{id}")
    void password(@Param("id") long id,@Param("hash") String hash);
}
