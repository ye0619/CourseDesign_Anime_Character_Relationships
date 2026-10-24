package com.animegraph.identity;

import com.animegraph.audit.AuditService;
import com.animegraph.common.ApiException;
import com.animegraph.graph.GraphStore;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EvidenceImportService {
    public record Change(String assertionUid,String evidenceUid,String source,String sourceLabel,String target,String targetLabel,String relation,String scope,List<Long> subjectIds,String sourceUrl,String sourceRevision,String sourceField,String rawRelation,String action) {}
    public record Preview(String previewId,String batchId,String packageHash,String baseHash,String provider,String sourceVersion,String licenseLabel,Instant expiresAt,boolean canApply,List<String> blockers,List<Change> changes) {}
    private record Pending(long actor,EvidencePackage data,Preview preview) {}
    private final EvidenceImportRepository repository;private final ObjectMapper json;private final AuditService audit;private final Clock clock;
    private final Map<String,Pending> previews=new LinkedHashMap<>();
    @org.springframework.beans.factory.annotation.Autowired
    public EvidenceImportService(EvidenceImportRepository repository,ObjectMapper json,AuditService audit){this(repository,json,audit,Clock.systemUTC());}
    EvidenceImportService(EvidenceImportRepository repository,ObjectMapper json,AuditService audit,Clock clock){this.repository=repository;this.json=json;this.audit=audit;this.clock=clock;}
    public synchronized Preview preview(byte[] bytes,long actor){
        if(bytes.length>512*1024)throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"FILE_TOO_LARGE","证据包不得超过512KB");
        EvidencePackage data;try{data=EvidencePackage.parse(json.reader().with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(bytes),json);}catch(java.io.IOException e){throw GraphStore.bad("证据包不是有效JSON，不能包含重复字段");}
        var state=repository.snapshot(data);String hash=data.hash(json);var blockers=state.blockers(data,hash);
        var labels=new HashMap<String,String>();state.nodes().forEach(n->labels.put(n.uid(),n.label()));
        var changes=new ArrayList<Change>();for(var f:data.facts()){var old=state.facts().stream().filter(s->s.evidenceUid().equals(f.evidenceUid())).findFirst().orElseThrow();var action=old.assertionActive()==null?"ADD_ASSERTION":old.evidenceActive()==null?"ADD_EVIDENCE":"REUSE";changes.add(new Change(f.uid(),f.evidenceUid(),f.source(),labels.getOrDefault(f.source(),f.source()),f.target(),labels.getOrDefault(f.target(),f.target()),f.label(),f.scopeType(),f.subjectIds(),f.sourceUrl(),f.sourceRevision(),f.sourceField(),f.rawRelation(),action));}
        var now=clock.instant();previews.values().removeIf(p->!p.preview().expiresAt().isAfter(now));while(previews.size()>=32)previews.remove(previews.keySet().iterator().next());
        String id=UUID.randomUUID().toString();var preview=new Preview(id,data.batchId(),hash,state.hash(json),data.provider(),data.sourceVersion(),data.licenseLabel(),now.plusSeconds(600),blockers.isEmpty(),blockers,changes);previews.put(id,new Pending(actor,data,preview));return preview;
    }
    public synchronized Map<String,Object> apply(String previewId,long actor){
        var pending=previews.get(previewId);if(pending==null||pending.actor()!=actor||!pending.preview().expiresAt().isAfter(clock.instant()))throw new ApiException(HttpStatus.CONFLICT,"EVIDENCE_PREVIEW_EXPIRED","预览已过期或不属于当前维护账号，请重新上传预览");
        if(!pending.preview().canApply())throw GraphStore.bad("预览存在阻塞项，不能提交");
        var batch=repository.apply(pending.data(),pending.preview().baseHash(),actor);record(actor,"EVIDENCE_IMPORT",String.valueOf(batch.get("uid")),"审核导入证据 "+pending.data().facts().size()+" 条");return batch;
    }
    public Map<String,Object> disable(String batchId,String reason,long actor){validateBatch(batchId);var result=repository.disable("evidence-batch:"+batchId,reason(reason),actor);record(actor,"EVIDENCE_BATCH_DISABLE","evidence-batch:"+batchId,reason);return result;}
    public Map<String,Object> revoke(String uid,String reason,long actor){if(!uid.matches("assertion:[a-f0-9]{64}"))throw GraphStore.bad("关系断言编号不正确");var result=repository.revoke(uid,reason(reason),actor);record(actor,"ASSERTION_REVOKE",uid,reason);return result;}
    public List<Map<String,Object>> list(int limit){if(limit<1||limit>100)throw GraphStore.bad("批次列表上限为1—100");return repository.list(limit);}
    private static void validateBatch(String id){if(id==null||!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))throw GraphStore.bad("批次编号不正确");}
    private static String reason(String value){if(value==null||value.isBlank()||value.length()>500)throw GraphStore.bad("请填写1—500字的停用/撤回原因");return value.trim();}
    private void record(long actor,String action,String uid,String reason){try{audit.record(actor,action,"EVIDENCE",uid,reason);}catch(Exception e){LoggerFactory.getLogger(getClass()).warn("SQL audit unavailable; durable graph provenance retained for batch or assertion {}",uid);}}
}
