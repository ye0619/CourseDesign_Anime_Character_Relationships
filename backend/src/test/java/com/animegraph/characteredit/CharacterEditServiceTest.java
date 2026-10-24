package com.animegraph.characteredit;

import com.animegraph.common.ApiException;
import com.animegraph.identity.NameResolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CharacterEditServiceTest {
    final ObjectMapper json=new ObjectMapper();final CharacterEditMapper mapper=mock(CharacterEditMapper.class);
    final CharacterEditService service=new CharacterEditService(mapper,json);
    final String uid="character:bangumi:15143";
    @Test void absentEmptyAndRestoreStayDifferentWithoutChangingSourceIdentity() {
        var node=json.createObjectNode().put("id",uid).put("label","来源名字").put("description","来源简介");
        service.apply(node,new CharacterEditService.Edit(uid,2,1,"v",json.createObjectNode().put("description",""),Map.of()));
        assertEquals("",node.get("description").asText());assertEquals("来源简介",node.path("sourceOriginal").path("description").asText());assertEquals(uid,node.path("id").asText());
        var source=json.createObjectNode().put("id",uid).put("label","来源名字");service.apply(source,new CharacterEditService.Edit(uid,3,1,"v",json.createObjectNode(),Map.of()));assertEquals("来源名字",source.path("label").asText());assertFalse(source.path("localModified").asBoolean());
    }
    @Test void staleRevisionIsRejectedBeforeAnyBackupOrWrite() {
        when(mapper.lock(uid)).thenReturn(new CharacterEditMapper.Row(uid,4,"{}"));
        var error=assertThrows(ApiException.class,()->service.save(uid,new CharacterEditService.Edit(uid,3,1,"v",json.createObjectNode(),Map.of())));
        assertEquals(409,error.status().value());verify(mapper,never()).backup(anyString(),any());verify(mapper,never()).replace(any());
    }
    @Test void sourceNamesResolveThroughSharedNamePolicyAndAllAliasesRemain() {
        var fields=json.createObjectNode();fields.putArray("names").addObject().put("id","en").put("text","English").put("locale","en").put("kind","PRIMARY");
        fields.withArray("names").addObject().put("id","zh").put("text","中文").put("locale","zh-Hans").put("kind","ALIAS");
        var node=json.createObjectNode().put("id",uid).put("label","来源名字");service.apply(node,new CharacterEditService.Edit(uid,1,1,"v",fields,Map.of()));assertEquals("中文",node.path("label").asText());assertEquals(2,node.path("aliases").size());assertEquals("LOCAL",node.path("nameAssertions").get(0).path("provider").asText());
    }
    @Test void unsafeAvatarAndUnboundedNamesAreRejectedBeforeDatabaseWrite() {
        assertThrows(ApiException.class,()->service.save(uid,new CharacterEditService.Edit(uid,0,1,"",json.createObjectNode().put("imageUrl","https://example.com/x.jpg"),Map.of())));
        assertThrows(ApiException.class,()->CharacterEditService.validateCrop(json.createObjectNode().put("mode","portrait").put("imageFingerprint","url|200x200")));
        verify(mapper,never()).ensure(anyString());
    }
    @Test void multipleLanguageSelectionDoesNotGuessUnknownLocale() {
        var names=List.of(new NameResolutionService.LocalName("a","English","en","PRIMARY","LOCAL","MANUAL"),new NameResolutionService.LocalName("b","日本語","und","ALIAS","LOCAL","MANUAL"));
        assertEquals("English",NameResolutionService.localDisplay(names,null,List.of("ja")));assertEquals("日本語",NameResolutionService.localDisplay(names,"b",List.of()));
        assertThrows(ApiException.class,()->NameResolutionService.localDisplay(names,"missing",List.of()));
    }
}
