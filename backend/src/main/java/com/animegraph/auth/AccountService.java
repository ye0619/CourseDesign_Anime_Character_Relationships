package com.animegraph.auth;

import com.animegraph.user.UserMapper;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class AccountService implements UserDetailsService {
    private final UserMapper mapper;
    public AccountService(UserMapper mapper) { this.mapper = mapper; }
    public UserDetails loadUserByUsername(String username) {
        var account = mapper.findByUsername(username);
        if (account == null) throw new UsernameNotFoundException("账号或密码不正确");
        return new AccountPrincipal(account, mapper.roles(account.id()));
    }
}
