package com.animegraph.graph;
import com.animegraph.common.ApiException;
import java.util.*;
import org.springframework.http.HttpStatus;
public final class UniverseAnalysisService {
    private UniverseAnalysisService(){}
    public static Map<String,Object> analyze(UniverseService.Network network,String from,String to,int maxDepth){
        if(maxDepth<1||maxDepth>6)throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","路径深度限1—6跳");
        var adjacent=new LinkedHashMap<String,Set<String>>();var names=new HashMap<String,Object>();network.nodes().forEach(n->{adjacent.put(String.valueOf(n.get("id")),new LinkedHashSet<>());names.put(String.valueOf(n.get("id")),n.get("label"));});
        var types=new TreeMap<String,Integer>();var groups=new TreeMap<String,Set<String>>();
        for(var edge:network.edges()){String s=String.valueOf(edge.get("source")),t=String.valueOf(edge.get("target"));if(adjacent.containsKey(s)&&adjacent.containsKey(t)&&!s.equals(t)){adjacent.get(s).add(t);adjacent.get(t).add(s);types.merge(String.valueOf(edge.get("relationType")),1,Integer::sum);if(edge.get("relationType").equals("MEMBER")&&Boolean.TRUE.equals(edge.get("directed")))groups.computeIfAbsent(t,k->new HashSet<>()).add(s);}}
        var ranking=adjacent.entrySet().stream().sorted(Comparator.<Map.Entry<String,Set<String>>>comparingInt(e->e.getValue().size()).reversed().thenComparing(Map.Entry::getKey)).limit(20).map(e->Map.of("uid",e.getKey(),"label",names.get(e.getKey()),"degree",e.getValue().size())).toList();
        List<String> path=List.of();if(!from.isBlank()&&!to.isBlank()&&adjacent.containsKey(from)&&adjacent.containsKey(to)){var queue=new ArrayDeque<List<String>>();queue.add(List.of(from));var visited=new HashSet<String>();visited.add(from);while(!queue.isEmpty()){var current=queue.remove();String last=current.getLast();if(last.equals(to)){path=current;break;}if(current.size()-1>=maxDepth)continue;for(String next:adjacent.get(last))if(visited.add(next)){var extended=new ArrayList<>(current);extended.add(next);queue.add(extended);}}}
        return Map.of("version",network.version(),"nodeCount",network.nodes().size(),"edgeCount",network.edges().size(),"ranking",ranking,"relationTypes",types,"factions",groups.entrySet().stream().map(e->Map.of("uid",e.getKey(),"label",names.get(e.getKey()),"count",e.getValue().size())).toList(),"path",path,"methodology","基于当前有界子图；连接度按不同邻居去重。路径忽略方向但保留原关系证据，最多6跳；无路径仅表示当前范围未找到。");
    }
}
