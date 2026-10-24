package com.animegraph.localgraph;

import com.animegraph.charactergraph.RelationType;
import com.animegraph.common.ApiException;
import java.util.*;
import static com.animegraph.localgraph.LocalGraphModels.*;
import org.springframework.http.HttpStatus;

/** Pure validation shared by editing and restore. Names are never identity keys. */
public final class LocalGraphRules {
    private LocalGraphRules() {}
    public static void validate(long subject,Document document,Map<String,Integer> official) {
        if(document==null || document.schemaVersion()!=1 || document.version()<0 || !("anime:bangumi:"+subject).equals(document.workUid()))throw bad("备份作品或格式不匹配");
        if(document.nodes()==null||document.edges()==null||document.nodes().size()>300||document.edges().size()>800)throw bad("每部作品最多300个本地实体、800条本地关系");
        var ids=new HashMap<String,String>();
        for(var node:document.nodes()) {
            if(node==null||!uuid(node.id(),"local:entity:")||!Set.of("CHARACTER","ORGANIZATION").contains(node.kind()))throw bad("本地实体编号或类型无效");
            if(ids.put(node.id(),node.kind())!=null)throw bad("本地实体编号重复");
            text(node.name(),100,true);text(node.gender(),40,false);text(node.description(),4000,false);text(node.notes(),2000,false);text(node.organizationType(),60,false);
            if(node.aliases()==null||node.aliases().size()>20)throw bad("别名最多20项");for(var alias:node.aliases())text(alias,100,true);
            image(node.imageUrl());
        }
        var signatures=new HashSet<String>();var edgeIds=new HashSet<String>();
        for(var edge:document.edges()) {
            if(edge==null||!uuid(edge.id(),"local:link:")||!edgeIds.add(edge.id()))throw bad("关系编号无效或重复");
            if(edge.source()==null||edge.target()==null||edge.source().equals(edge.target()))throw bad("关系不能连接自身");
            if(!ids.containsKey(edge.source())&&!official.containsKey(edge.source())||!ids.containsKey(edge.target())&&!official.containsKey(edge.target()))throw bad("关系端点必须属于当前作品；资料缺失时请先重新读取官方名册");
            try { if(!edge.directed() && RelationType.valueOf(edge.relationType()).directed())throw bad("该关系类型有方向，请核对起点与终点"); } catch(ApiException e){throw e;} catch(Exception e) { throw bad("关系类型无效，自定义关系请选择其他"); }
            text(edge.label(),60,true);
            if(!edge.directed() && Set.of("父亲","母亲","父","母","老师","教师","导师","学生","徒弟","师父","师傅","上司","下属","儿子","女儿").contains(edge.label().strip()))throw bad("该关系身份有方向，请启用有方向并核对起点与终点");
            text(edge.description(),2000,false);text(edge.evidence(),2000,false);text(edge.notes(),2000,false);
            if(edge.relationType().equals("MEMBER")) {
                if(!"ORGANIZATION".equals(ids.get(edge.target())) || !character(edge.source(),ids,official) || !edge.directed())throw bad("组织成员关系必须从角色指向本地组织，并保留方向");
            } else if(!character(edge.source(),ids,official)||!character(edge.target(),ids,official))throw bad("人物关系只允许连接角色；组织请使用成员关系");
            String a=edge.source(),b=edge.target();if(!edge.directed()&&a.compareTo(b)>0){String swap=a;a=b;b=swap;}
            String signature=a+"|"+b+"|"+edge.relationType()+"|"+edge.label().strip()+"|"+edge.directed();
            if(!signatures.add(signature))throw bad("已存在完全相同的关系，请编辑原记录");
        }
    }
    private static boolean character(String id,Map<String,String> local,Map<String,Integer> official) {
        return "CHARACTER".equals(local.get(id))||official.containsKey(id)&&official.get(id)!=4;
    }
    static boolean uuid(String id,String prefix) {
        if(id==null||!id.startsWith(prefix))return false;
        try{return UUID.fromString(id.substring(prefix.length())).toString().equals(id.substring(prefix.length()));}catch(Exception e){return false;}
    }
    static void text(String value,int limit,boolean required) {
        if(value==null){if(required)throw bad("名称不能为空");return;}
        if(value.length()>limit||required&&value.isBlank()||value.indexOf(0)>=0)throw bad("字段为空或超过长度限制");
    }
    static void image(String value) {
        if(value==null||value.isBlank())return;
        if(value.matches("https://lain\\.bgm\\.tv/[^\\s]{1,1000}"))return;
        if(!value.matches("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+"))throw bad("头像支持 Bangumi 图片地址或上传的 PNG/JPEG/WebP");
        byte[] bytes;try{bytes=Base64.getDecoder().decode(value.substring(value.indexOf(',')+1));}catch(Exception e){throw bad("头像编码无效");}
        if(bytes.length>512*1024||bytes.length<12)throw bad("头像不得超过512KB");
        boolean png=value.startsWith("data:image/png;")&&bytes[0]==(byte)137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71;
        boolean jpeg=value.startsWith("data:image/jpeg;")&&bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255;
        boolean webp=value.startsWith("data:image/webp;")&&new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")&&new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
        if(!png&&!jpeg&&!webp)throw bad("头像文件内容与图片类型不符");
    }
    static ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,"LOCAL_GRAPH_INVALID",message);}
}
