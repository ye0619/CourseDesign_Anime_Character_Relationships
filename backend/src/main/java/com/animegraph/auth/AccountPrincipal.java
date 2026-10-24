package com.animegraph.auth;

import com.animegraph.user.UserRecord;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AccountPrincipal(UserRecord account, List<String> roles) implements UserDetails, Serializable {
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
    }
    public String getPassword() { return account.passwordHash(); }
    public String getUsername() { return account.username(); }
    public boolean isEnabled() { return account.enabled(); }
}
