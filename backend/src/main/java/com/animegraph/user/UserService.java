package com.animegraph.user;

import com.animegraph.audit.AuditService;
import com.animegraph.auth.AccountPrincipal;
import com.animegraph.common.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserMapper mapper;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    public UserService(UserMapper mapper, PasswordEncoder encoder, AuditService audit) {
        this.mapper=mapper; this.encoder=encoder; this.audit=audit;
    }
    public record UserView(long id,String username,String displayName,boolean enabled,List<String> roles) {}
    public UserView view(UserRecord row) { return new UserView(row.id(),row.username(),row.displayName(),row.enabled(),mapper.roles(row.id())); }
    public UserView me(AccountPrincipal principal) {
        if (principal==null) throw new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","请先登录");
        return view(required(principal.account().id()));
    }
    public PageResult<UserView> list(int page,int size) {
        int offset=PageResult.offset(page,size);
        return new PageResult<>(mapper.list(offset,size).stream().map(this::view).toList(),page,size,mapper.count());
    }
    @Transactional
    public UserView create(String username,String password,String displayName,List<String> roles,Long actor) {
        if(username==null || !username.matches("[a-zA-Z0-9_.-]{3,50}")) bad("用户名需为3—50位字母、数字或_.-");
        checkPassword(password);
        String normalized=username.toLowerCase(Locale.ROOT);
        if(mapper.findByUsername(normalized)!=null) throw new ApiException(HttpStatus.CONFLICT,"USER_EXISTS","用户名已存在");
        var checked=checkRoles(roles);
        if(displayName!=null && displayName.length()>100) bad("显示名称最多100字");
        try {mapper.insert(normalized,encoder.encode(password),displayName==null||displayName.isBlank()?normalized:displayName);}
        catch(org.springframework.dao.DuplicateKeyException exception) {throw new ApiException(HttpStatus.CONFLICT,"USER_EXISTS","用户名已存在");}
        var created=mapper.findByUsername(normalized);
        checked.forEach(role -> mapper.assignRole(created.id(),role));
        audit.record(actor,"USER_CREATE","USER",String.valueOf(created.id()),"创建账号 "+normalized);
        return view(created);
    }
    @Transactional
    public UserView update(long id,String displayName,Boolean enabled,List<String> roles,Long actor) {
        mapper.lockAdminRole();
        var old=required(id);
        var newRoles=roles==null?mapper.roles(id):checkRoles(roles);
        boolean active=enabled==null?old.enabled():enabled;
        if(old.enabled() && mapper.roles(id).contains("ADMIN") && (!active || !newRoles.contains("ADMIN")) && mapper.enabledAdminCount()<=1) {
            throw new ApiException(HttpStatus.CONFLICT,"LAST_ADMIN","不能停用或移除最后一个有效管理员");
        }
        mapper.update(id,displayName==null?old.displayName():displayName,active);
        mapper.clearRoles(id);
        newRoles.forEach(role -> mapper.assignRole(id,role));
        audit.record(actor,"USER_UPDATE","USER",String.valueOf(id),"更新账号及权限");
        return view(required(id));
    }
    @Transactional
    public void resetPassword(long id,String password,Long actor) {
        required(id); checkPassword(password);
        mapper.password(id,encoder.encode(password));
        audit.record(actor,"PASSWORD_RESET","USER",String.valueOf(id),"重置密码并撤销旧会话");
    }
    public UserRecord required(long id) {
        var row=mapper.findById(id);
        if(row==null) throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","账号不存在");
        return row;
    }
    private List<String> checkRoles(List<String> roles) {
        if(roles==null || roles.isEmpty() || roles.stream().anyMatch(role -> !Set.of("ADMIN","USER").contains(role))) bad("请选择有效角色");
        return roles.stream().distinct().sorted().toList();
    }
    private void checkPassword(String password) {
        if(password==null || password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72) bad("密码至少12个字符且UTF-8长度不超过72字节");
    }
    private void bad(String message) { throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message); }
}
