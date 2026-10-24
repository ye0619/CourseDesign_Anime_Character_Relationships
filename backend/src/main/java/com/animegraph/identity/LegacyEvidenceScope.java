package com.animegraph.identity;

import com.animegraph.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;

/** Frozen authorization scope of historical admin-only evidence batches.
 * This does not participate in work search, save, update or viewing reports.
 */
final class LegacyEvidenceScope {
    private final Set<String> allowed=new HashSet<>();
    LegacyEvidenceScope(ObjectMapper json) {
        try(var stream=new ClassPathResource("maintenance/legacy-evidence-scope.json").getInputStream()) {
            for(var row:json.readTree(stream).path("works"))allowed.add(row.path("uid").asText());
        }catch(Exception e){throw new IllegalStateException("Historical evidence scope unavailable",e);}
    }
    void require(String uid) {
        if(!allowed.contains(uid))throw new ApiException(HttpStatus.BAD_REQUEST,"EVIDENCE_SCOPE","超出旧证据批次的授权维护范围");
    }
}
