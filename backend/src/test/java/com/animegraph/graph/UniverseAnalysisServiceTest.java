package com.animegraph.graph;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UniverseAnalysisServiceTest {
    static Map<String,Object> edge(String a,String b,String type){return Map.of("source",a,"target",b,"relationType",type,"directed",true);}
    @Test void graphAnalysisDeduplicatesNeighborsAndBoundsPaths(){var nodes=new ArrayList<Map<String,Object>>();for(int i=0;i<8;i++)nodes.add(Map.of("id","c"+i,"label","角色"+i));var edges=new ArrayList<Map<String,Object>>();for(int i=0;i<7;i++)edges.add(edge("c"+i,"c"+(i+1),"FRIEND"));edges.add(edge("c0","c1","CLASSMATE"));var network=new UniverseService.Network(null,nodes,edges,false,"","test",null);var result=UniverseAnalysisService.analyze(network,"c0","c7",4);assertTrue(((List<?>)result.get("path")).isEmpty());assertEquals(List.of("c0","c1","c2"),UniverseAnalysisService.analyze(network,"c0","c2",4).get("path"));assertThrows(com.animegraph.common.ApiException.class,()->UniverseAnalysisService.analyze(network,"c0","c7",7));var ranking=(List<?>)result.get("ranking");assertEquals(2,((Map<?,?>)ranking.getFirst()).get("degree"));}
}
