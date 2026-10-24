package com.animegraph.identity;

import com.animegraph.audit.AuditService;
import com.animegraph.common.ApiException;
import com.animegraph.graph.GraphStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityReviewService {
    public record Candidate(String provider,String providerId,String entityType,String canonicalUid,String sourceUrl,String sourceRevision,String licenseLabel,String evidenceText,String reason,String matchMethod) {
        public Candidate(String provider,String providerId,String entityType,String canonicalUid,String sourceUrl,String sourceRevision,String licenseLabel,String evidenceText,String reason){this(provider,providerId,entityType,canonicalUid,sourceUrl,sourceRevision,licenseLabel,evidenceText,reason,null);}
    }
    public record Decision(Long expectedVersion,String decision,String targetUid,String reason,String newRevision,String newEvidence,String newSourceUrl,String newLicenseLabel) {}
    public record Detail(IdentityReviewMapper.Identity identity,List<Map<String,Object>> history) {}
    private static final Set<String> PROVIDERS=Set.of("BANGUMI","MOEGIRL","ACDB","WIKIDATA","PROJECT");
    private static final Set<String> STATUSES=Set.of("CANDIDATE","REVIEWED","REJECTED","REVOKED");
    private final IdentityReviewMapper mapper;
    private final GraphStore graph;
    private final AuditService audit;
    private final ObjectMapper json;
    public IdentityReviewService(IdentityReviewMapper mapper,GraphStore graph,AuditService audit,ObjectMapper json){this.mapper=mapper;this.graph=graph;this.audit=audit;this.json=json;}
    public List<IdentityReviewMapper.Identity> list(String provider,String status,long beforeId,int limit){
        if(!provider.isEmpty()&&!PROVIDERS.contains(provider)||!status.isEmpty()&&!STATUSES.contains(status)||beforeId<0||limit<1||limit>50)throw GraphStore.bad("筛选条件或读取数量不正确");
        return mapper.list(provider,status,beforeId,limit);
    }
    public Detail detail(long id){return new Detail(required(id),mapper.history(id));}
    @Transactional public IdentityReviewMapper.Identity create(Candidate c,long actor){
        String provider=text(c.provider(),24),providerId=text(c.providerId(),150),type=text(c.entityType(),24),uid=text(c.canonicalUid(),200);
        if(!PROVIDERS.contains(provider)||!Set.of("CHARACTER","PERSON","ANIME").contains(type))throw GraphStore.bad("来源或实体类型不正确");
        if(providerId.chars().anyMatch(Character::isISOControl))throw GraphStore.bad("来源编号不能包含控制字符");
        if(Set.of("BANGUMI","ACDB").contains(provider)&&!providerId.matches("[1-9][0-9]{0,12}")||provider.equals("WIKIDATA")&&!providerId.matches("Q[1-9][0-9]{0,12}"))throw GraphStore.bad("来源编号格式不正确");
        target(uid,type);String url=sourceUrl(c.sourceUrl(),provider),revision=text(c.sourceRevision(),100),license=text(c.licenseLabel(),200),evidence=text(c.evidenceText(),2000),reason=text(c.reason(),500);
        String method=c.matchMethod()==null?"MANUAL":c.matchMethod();if(!Set.of("MANUAL","TITLE_ONLY").contains(method))throw GraphStore.bad("匹配方法须为MANUAL或TITLE_ONLY");
        var row=new HashMap<String,Object>();row.putAll(Map.of("provider",provider,"providerId",providerId,"entityType",type,"canonicalUid",uid,"sourceUrl",url,"sourceRevision",revision,"licenseLabel",license,"evidenceText",evidence,"reason",reason,"actor",actor));row.put("matchMethod",method);
        try{mapper.insert(row);}catch(DuplicateKeyException e){throw conflict("该来源编号已存在，请查看原记录；不会覆盖已有审核状态");}
        var result=required(((Number)row.get("id")).longValue());log(result.id(),"CREATE",actor,reason,null,result);return result;
    }
    @Transactional public IdentityReviewMapper.Identity decide(long id,Decision input,long actor){
        if(input.expectedVersion()==null||input.expectedVersion()<0)throw GraphStore.bad("必须提供当前记录版本");
        var before=required(id);if(before.version()!=input.expectedVersion())throw conflict("身份记录已被更新，请重新读取后核对");
        String reason=text(input.reason(),500),action=text(input.decision(),24),uid=before.canonicalUid(),status,revision=before.sourceRevision(),evidence=before.evidenceText(),url=before.sourceUrl(),license=before.licenseLabel();
        switch(action){
            case "REVIEW" -> {state(before.status(),"CANDIDATE");if("TITLE_ONLY".equals(before.matchMethod()))throw GraphStore.bad("仅标题对应的候选须先补充原名与作品依据，不能直接审核");if(input.targetUid()!=null&&!input.targetUid().isBlank()&&!input.targetUid().equals(uid))throw GraphStore.bad("审核不能同时改绑，请先单独改绑");target(uid,before.entityType());text(revision,100);text(evidence,2000);status="REVIEWED";}
            case "REJECT" -> {state(before.status(),"CANDIDATE");status="REJECTED";}
            case "REVOKE" -> {state(before.status(),"REVIEWED");status="REVOKED";}
            case "REMAP" -> {if(!Set.of("CANDIDATE","REVIEWED").contains(before.status()))throw conflict("已驳回或撤回的身份须先凭新修订重开审核");uid=text(input.targetUid(),200);if(uid.equals(before.canonicalUid()))throw GraphStore.bad("改绑目标须与原实体不同");target(uid,before.entityType());status="CANDIDATE";}
            case "AMEND" -> {state(before.status(),"CANDIDATE");status="CANDIDATE";}
            case "REOPEN" -> {if(!Set.of("REJECTED","REVOKED").contains(before.status()))throw conflict("仅已驳回或撤回的身份可重开");revision=text(input.newRevision(),100);evidence=text(input.newEvidence(),2000);if(revision.equals(before.sourceRevision()))throw GraphStore.bad("重开须提供新的来源修订与核验文字");status="CANDIDATE";}
            default -> throw GraphStore.bad("不支持的审核操作");
        }
        if(!Set.of("REMAP","REVIEW").contains(action)&&input.targetUid()!=null&&!input.targetUid().isBlank())throw GraphStore.bad("此操作不接受改绑目标");
        String method=null;
        if(Set.of("REOPEN","AMEND").contains(action)){revision=text(input.newRevision(),100);evidence=text(input.newEvidence(),2000);url=sourceUrl(input.newSourceUrl(),before.provider());license=text(input.newLicenseLabel(),200);if("TITLE_ONLY".equals(before.matchMethod())&&evidence.equals(before.evidenceText()))throw GraphStore.bad("须以新核验依据替换标题元信息，不能原样审核");method="MANUAL";}
        else if(input.newRevision()!=null||input.newEvidence()!=null||input.newSourceUrl()!=null||input.newLicenseLabel()!=null)throw GraphStore.bad("新资料仅用于补充候选或重开审核");
        if(mapper.update(id,before.version(),uid,status,revision,evidence,url,license,reason,actor,method)!=1)throw conflict("身份记录已被其他维护者更新，请重新读取");
        var result=required(id);log(id,action,actor,reason,before,result);return result;
    }
    private IdentityReviewMapper.Identity required(long id){if(id<1)throw GraphStore.bad("身份记录编号不正确");var row=mapper.find(id);if(row==null)throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","身份记录不存在");return row;}
    private void target(String uid,String type){
        if(!uid.matches("(character|person|anime):[a-z0-9_-]+:[A-Za-z0-9_-]{1,100}")||!uid.startsWith(type.toLowerCase(Locale.ROOT)+":"))throw GraphStore.bad("稳定实体编号与类型不一致");
        if(!type.equals(graph.entity(uid,false).get("type")))throw GraphStore.bad("目标实体类型不一致");
    }
    private void log(long id,String action,long actor,String reason,Object before,Object after){
        try{mapper.event(id,action,actor,reason,before==null?null:json.writeValueAsString(before),json.writeValueAsString(after));}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Cannot encode identity history",e);}
        audit.record(actor,"IDENTITY_"+action,"EXTERNAL_IDENTITY","identity:"+id,reason);
    }
    private static void state(String current,String expected){if(!expected.equals(current))throw conflict("该状态不能执行此操作，请重新核对当前记录");}
    private static ApiException conflict(String message){return new ApiException(HttpStatus.CONFLICT,"IDENTITY_CHANGED",message);}
    static String text(String value,int max){if(value==null||value.isBlank()||value.length()>max||value.chars().anyMatch(c->c<32&&c!='\n'&&c!='\t'))throw GraphStore.bad("核验资料不能为空、过长或包含控制字符");return value.strip();}
    static String sourceUrl(String input,String provider){
        String value=text(input,1000);try{var uri=URI.create(value);String host=uri.getHost();if(!"https".equals(uri.getScheme())||host==null||uri.getUserInfo()!=null||uri.getPort()!=-1)throw new IllegalArgumentException();
            Set<String> hosts=switch(provider){case "BANGUMI"->Set.of("bgm.tv","bangumi.tv","chii.in");case "MOEGIRL"->Set.of("zh.moegirl.org.cn","mzh.moegirl.org.cn","zh.moegirl.org");case "ACDB"->Set.of("animecharactersdatabase.com","www.animecharactersdatabase.com");case "WIKIDATA"->Set.of("www.wikidata.org","wikidata.org");default->Set.of(host);};if(!hosts.contains(host.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException();return value;
        }catch(IllegalArgumentException e){throw GraphStore.bad("来源链接须为对应站点的HTTPS链接，不能含登录信息或自定义端口");}
    }
}
