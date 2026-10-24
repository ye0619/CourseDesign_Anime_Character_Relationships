package com.animegraph.localgraph;
import static com.animegraph.localgraph.LocalGraphModels.*;
import com.animegraph.explore.SavedAnimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class LocalGraphServiceTest {
    @Test void existingValidatedAnchorsRemainEditableWithoutOfficialNetworkOrCache() {
        var repository=mock(LocalGraphRepository.class);var source=mock(SavedAnimeService.class);
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var service=new LocalGraphService(repository,source,factory.getValidator(),new ObjectMapper());
            String a=LocalGraphRulesTest.nodeId();var document=new Document(4,1,"anime:bangumi:1",List.of(LocalGraphRulesTest.node(a,"CHARACTER")),
                    List.of(LocalGraphRulesTest.link(a,"character:bangumi:8","OTHER","已有本地关系",true)));
            when(repository.read(document.workUid())).thenReturn(document);when(repository.replace(document)).thenReturn(document);
            assertEquals(document,service.save(1,document));verify(source,never()).graph(anyLong());verify(repository).replace(document);
        }
    }
}
