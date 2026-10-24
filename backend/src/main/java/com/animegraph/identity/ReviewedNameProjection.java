package com.animegraph.identity;

import com.animegraph.graph.GraphStore;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Adds current reviewed names at response time, never to a source cache or graph snapshot. */
@Service
public class ReviewedNameProjection {
    private final IdentityNameMapper names;private final ObjectMapper json;
    public ReviewedNameProjection(IdentityNameMapper names,ObjectMapper json){this.names=names;this.json=json;}
    public List<String> search(String type,String keyword){if(keyword==null||keyword.isBlank())return List.of();try{var rows=names.search(type,IdentityNameService.normalize(keyword));if(rows.size()>1000)throw GraphStore.bad("别名匹配过多，请缩小搜索词");return rows;}catch(DataAccessException e){throw new com.animegraph.common.ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"NAME_LOOKUP_UNAVAILABLE","审核别名检索暂不可用，请稍后重试");}}
    public ObjectNode project(Object data,String... collections){
        ObjectNode result=json.valueToTree(data);var nodes=new ArrayList<ObjectNode>();
        if(collections.length==0)nodes.add(result);else for(String collection:collections)collect(result,collection.split("\\."),0,nodes);
        var uids=nodes.stream().map(ReviewedNameProjection::uid).filter(s->s.matches("(character|person|anime):[a-z0-9_-]+:[A-Za-z0-9_-]{1,100}")).distinct().toList();
        if(uids.isEmpty())return result;
        try{
            if(uids.size()>1200)throw new IllegalStateException("Name projection bound exceeded");
            var byUid=new HashMap<String,List<IdentityNameMapper.Name>>();
            for(int start=0;start<uids.size();start+=240){var rows=names.active(uids.subList(start,Math.min(start+240,uids.size())));if(rows.size()>4800)throw new IllegalStateException("Name result bound exceeded");for(var row:rows)byUid.computeIfAbsent(row.canonicalUid(),k->new ArrayList<>()).add(row);}
            for(var node:nodes){var rows=byUid.get(uid(node));if(rows==null)continue;var aliases=new LinkedHashSet<String>();for(var a:node.path("aliases"))if(a.isTextual())aliases.add(a.asText());ArrayNode assertions=node.path("nameAssertions").isArray()?(ArrayNode)node.get("nameAssertions"):node.putArray("nameAssertions");
                for(var row:rows){if(!row.name().equals(node.path("label").asText(node.path("nameCn").asText())))aliases.add(row.name());var assertion=assertions.addObject();assertion.put("name",row.name()).put("locale",row.locale()).put("kind","REVIEWED_ALIAS").put("provider",row.provider()).put("sourceUrl",row.sourceUrl()).put("sourceRevision",row.sourceRevision()).put("reviewStatus","REVIEWED").put("reviewedOn",row.reviewedAt().toString().substring(0,10)).putNull("retrievedAt");assertion.put("evidenceText",row.evidenceText()).put("licenseLabel",row.licenseLabel()).put("identityVersion",row.reviewVersion()).put("providerId",row.providerId());}
                var list=node.putArray("aliases");aliases.forEach(list::add);
            }
        }catch(DataAccessException|IllegalStateException e){result.put("nameNotice","已审核别名暂不可用；当前保留原有来源名称，请稍后重新读取。");if(result.path("meta").isObject())((ObjectNode)result.get("meta")).put("notice",result.path("meta").path("notice").asText()+" 已审核别名暂不可用，请稍后重新读取。");}
        return result;
    }
    private static String uid(JsonNode node){return node.path("id").asText(node.path("uid").asText(""));}
    private static void collect(JsonNode node,String[] path,int depth,List<ObjectNode> nodes) {
        if(node.isArray()) {for(var item:node) collect(item,path,depth,nodes);}
        else if(depth==path.length) {if(node.isObject()) nodes.add((ObjectNode)node);}
        else collect(node.path(path[depth]),path,depth+1,nodes);
    }
}
