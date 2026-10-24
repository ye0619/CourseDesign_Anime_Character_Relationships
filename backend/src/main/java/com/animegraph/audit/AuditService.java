package com.animegraph.audit;

import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void record(Long actor, String action, String type, String uid, String summary) {
        jdbc.update("INSERT INTO operation_log(request_id,actor_id,action,target_type,target_uid,result,summary) VALUES(?,?,?,?,?,'SUCCESS',?)",
                MDC.get("requestId"), actor, action, type, uid, summary);
    }
}
