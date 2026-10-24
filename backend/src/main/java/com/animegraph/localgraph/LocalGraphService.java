package com.animegraph.localgraph;

import static com.animegraph.localgraph.LocalGraphModels.*;
import com.animegraph.explore.SavedAnimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class LocalGraphService {
    private final LocalGraphRepository repository;private final SavedAnimeService official;private final Validator validator;private final ObjectMapper json;
    public LocalGraphService(LocalGraphRepository repository,SavedAnimeService official,Validator validator,ObjectMapper json){this.repository=repository;this.official=official;this.validator=validator;this.json=json;}
    public String work(long id){if(id<1||id>Integer.MAX_VALUE)throw LocalGraphRules.bad("作品编号无效");return "anime:bangumi:"+id;}
    public Document read(long id){return repository.read(work(id));}
    public Document save(long id,Document document){
        work(id);if(document==null||!validator.validate(document).isEmpty())throw LocalGraphRules.bad("字段校验失败，请检查必填项与长度限制");
        try{if(json.writeValueAsBytes(document).length>8*1024*1024)throw LocalGraphRules.bad("本作本地资料总大小不得超过8MB");}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw LocalGraphRules.bad("本地资料格式不正确");}
        // Previously validated anchors remain valid when the official cache/network is unavailable.
        var previous=repository.read(work(id));var ids=new HashMap<String,Integer>();
        for(var edge:previous.edges())for(var uid:List.of(edge.source(),edge.target()))if(uid.matches("character:bangumi:[1-9]\\d*"))ids.put(uid,1);
        boolean newAnchor=document.edges().stream().anyMatch(edge->java.util.stream.Stream.of(edge.source(),edge.target())
                .anyMatch(uid->uid!=null&&uid.startsWith("character:bangumi:")&&!ids.containsKey(uid)));
        if(previous.version()==0||newAnchor) {
            var graph=official.graph(id);graph.nodes().forEach(node->ids.put(node.id(),node.characterType()==null?1:node.characterType()));
        }
        LocalGraphRules.validate(id,document,ids);return repository.replace(document);
    }
    public List<Map<String,Object>> backups(long id){return repository.backups(work(id));}
    public Document backup(long id,String backup){return repository.backup(work(id),backup);}
}
