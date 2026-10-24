package com.animegraph.identity;
import com.animegraph.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ReviewedNameProjectionTest {
    @Test void curatedNestedEntriesKeepReviewedAliasesAndMissingMembersWithoutChangingIdentity(){
        var name=new IdentityNameMapper.Name(1,0,"审核别名","审核别名","zh-Hans","verified","anime:bangumi:328609","https://bgm.tv/subject/328609","r1","fixture",2,Instant.EPOCH,"BANGUMI","328609");
        when(mapper.active(anyList())).thenReturn(List.of(name));
        var missing=new HashMap<String,Object>();missing.put("entity",null);
        var result=service.project(Map.of("items",List.of(Map.of("entity",Map.of("uid",name.canonicalUid(),"name","来源名")),missing)),"items.entity");
        assertEquals(name.canonicalUid(),result.at("/items/0/entity/uid").asText());
        assertEquals("审核别名",result.at("/items/0/entity/aliases/0").asText());
        assertTrue(result.at("/items/1/entity").isNull());verify(mapper).active(List.of(name.canonicalUid()));
    }
    final IdentityNameMapper mapper=mock(IdentityNameMapper.class);final ReviewedNameProjection service=new ReviewedNameProjection(mapper,new ObjectMapper().findAndRegisterModules());
    @Test void addsProvenanceWithoutChangingDisplayOriginalOrCachedSource(){
        var row=new IdentityNameMapper.Name(1,0,"外站别名","外站别名","zh-Hans","name explicitly identified in source","character:bangumi:87968","https://zh.moegirl.org.cn/index.php?curid=404558","r1","fixture license",4,Instant.parse("2026-10-22T00:00:00Z"),"MOEGIRL","404558");when(mapper.active(anyList())).thenReturn(List.of(row));
        var original=Map.of("nodes",List.of(Map.of("id",row.canonicalUid(),"label","项目首选名","originalName","source original","aliases",List.of("Bangumi alias"))));var result=service.project(original,"nodes");
        assertEquals("项目首选名",result.at("/nodes/0/label").asText());assertEquals("source original",result.at("/nodes/0/originalName").asText());assertEquals(2,result.at("/nodes/0/aliases").size());assertEquals("MOEGIRL",result.at("/nodes/0/nameAssertions/0/provider").asText());assertEquals(4,result.at("/nodes/0/nameAssertions/0/identityVersion").asInt());assertEquals(List.of("Bangumi alias"),original.get("nodes").getFirst().get("aliases"));
    }
    @Test void failedSqlKeepsSourceNamesAndExplainsMissingOverlay(){when(mapper.active(anyList())).thenThrow(new DataAccessResourceFailureException("unavailable"));var result=service.project(Map.of("nodes",List.of(Map.of("id","character:bangumi:87968","label","来源姓名","aliases",List.of("来源别名")))),"nodes");assertTrue(result.has("nameNotice"));assertEquals("来源别名",result.at("/nodes/0/aliases/0").asText());assertFalse(result.at("/nodes/0").has("nameAssertions"));}
    @Test void normalizationAndBoundedSearchDoNotBecomeSilentEmptyResults(){when(mapper.search("CHARACTER","abc")).thenReturn(List.of("character:bangumi:1"));assertEquals(List.of("character:bangumi:1"),service.search("CHARACTER","ＡＢＣ"));when(mapper.search("CHARACTER","down")).thenThrow(new DataAccessResourceFailureException("unavailable"));assertEquals(503,assertThrows(ApiException.class,()->service.search("CHARACTER","down")).status().value());assertEquals(List.of(),service.search("CHARACTER",""));}
}
