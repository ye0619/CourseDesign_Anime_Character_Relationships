package com.animegraph.config;

import com.animegraph.user.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@Order(20)
@ConditionalOnProperty(prefix="app.admin",name="enabled",havingValue="true")
public class InitialAdmin implements ApplicationRunner {
    private final UserMapper mapper;
    private final UserService users;
    private final String username;
    private final String password;
    public InitialAdmin(UserMapper mapper,UserService users,@Value("${app.admin.username:admin}")String username,
                        @Value("${app.admin.password:}")String password) {
        this.mapper=mapper;this.users=users;this.username=username;this.password=password;
    }
    public void run(ApplicationArguments arguments) {
        if(!password.isBlank() && mapper.findByUsername(username)==null) users.create(username,password,"系统管理员",List.of("ADMIN","USER"),null);
    }
}
