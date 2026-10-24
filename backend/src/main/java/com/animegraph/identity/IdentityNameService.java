package com.animegraph.identity;

import com.animegraph.audit.AuditService;
import com.animegraph.common.ApiException;
import com.animegraph.graph.GraphStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityNameService {
    public record Input(String name,String locale,String evidenceText) {}
    public record Replacement(Long expectedVersion,String reason,List<Input> names) {}
    public record Detail(IdentityReviewMapper.Identity identity,List<IdentityNameMapper.Name> names,boolean activeForSearch) {}
    private final IdentityNameMapper names;private final IdentityReviewMapper identities;private final GraphStore graph;private final AuditService audit;private final ObjectMapper json;
    public IdentityNameService(IdentityNameMapper names,IdentityReviewMapper identities,GraphStore graph,AuditService audit,ObjectMapper json){this.names=names;this.identities=identities;this.graph=graph;this.audit=audit;this.json=json;}
    public Detail detail(long id){var identity=required(id);var rows=names.stored(id);return new Detail(identity,rows,identity.status().equals("REVIEWED")&&rows.stream().allMatch(n->n.reviewVersion()==identity.version()&&n.canonicalUid().equals(identity.canonicalUid())));}
    @Transactional public Detail replace(long id,Replacement input,long actor){
        var before=required(id);
        if(input.expectedVersion()==null||input.expectedVersion()!=before.version())throw conflict();
        if(!"REVIEWED".equals(before.status()))throw GraphStore.bad("只有已审核身份可以登记检索别名，候选不能使用");
        if(input.names()==null||input.names().size()>20)throw GraphStore.bad("每条身份最多20个别名；空列表用于明确清除");
        if(!before.entityType().equals(graph.entity(before.canonicalUid(),false).get("type")))throw GraphStore.bad("目标实体类型不一致");
        String reason=IdentityReviewService.text(input.reason(),500),revision=IdentityReviewService.text(before.sourceRevision(),100),url=IdentityReviewService.sourceUrl(before.sourceUrl(),before.provider()),license=IdentityReviewService.text(before.licenseLabel(),200);
        var rows=new ArrayList<IdentityNameMapper.Name>();var keys=new HashSet<String>();
        for(var item:input.names()){
            if(item==null)throw GraphStore.bad("别名资料不完整");
            String name=IdentityReviewService.text(item.name(),200),locale=IdentityReviewService.text(item.locale(),20),evidence=IdentityReviewService.text(item.evidenceText(),1000),key=normalize(name);
            if(name.chars().anyMatch(Character::isISOControl)||key.length()>200||!keys.add(key)||!Set.of("zh-Hans","zh-Hant","ja","en","und").contains(locale))throw GraphStore.bad("别名重复、语言不支持或名称含控制字符");
            rows.add(new IdentityNameMapper.Name(id,rows.size(),name,key,locale,evidence,before.canonicalUid(),url,revision,license,before.version()+1,null,before.provider(),before.providerId()));
        }
        var oldNames=names.stored(id);
        if(identities.update(id,before.version(),before.canonicalUid(),before.status(),before.sourceRevision(),before.evidenceText(),before.sourceUrl(),before.licenseLabel(),reason,actor,null)!=1)throw conflict();
        names.delete(id);for(var row:rows)names.insert(row);
        var result=detail(id);
        try{identities.event(id,"NAMES",actor,reason,json.writeValueAsString(snapshot(before,oldNames)),json.writeValueAsString(snapshot(result.identity(),result.names())));}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Cannot encode reviewed names",e);}
        audit.record(actor,"IDENTITY_NAMES","EXTERNAL_IDENTITY","identity:"+id,reason);return result;
    }
    private Map<String,Object> snapshot(IdentityReviewMapper.Identity identity,List<IdentityNameMapper.Name> rows){Map<String,Object> state=json.convertValue(identity,new TypeReference<LinkedHashMap<String,Object>>(){});state.put("names",rows);return state;}
    private IdentityReviewMapper.Identity required(long id){if(id<1)throw GraphStore.bad("身份编号不正确");var row=identities.find(id);if(row==null)throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","身份记录不存在");return row;}
    static String normalize(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).strip();}
    private static ApiException conflict(){return new ApiException(HttpStatus.CONFLICT,"IDENTITY_CHANGED","身份记录已更新，请重新读取后核对别名");}
}
