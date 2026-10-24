package com.animegraph.identity;
import com.animegraph.audit.AuditService;
import com.animegraph.common.ApiException;
import com.animegraph.graph.GraphStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class IdentityNameServiceTest {
    final IdentityNameMapper names=mock(IdentityNameMapper.class);final IdentityReviewMapper identities=mock(IdentityReviewMapper.class);final GraphStore graph=mock(GraphStore.class);final AuditService audit=mock(AuditService.class);
    final IdentityNameService service=new IdentityNameService(names,identities,graph,audit,new ObjectMapper().findAndRegisterModules());
    @Test void candidateAndStaleVersionCannotPublishAliases(){when(identities.find(1)).thenReturn(IdentityReviewServiceTest.row("CANDIDATE",0));var input=new IdentityNameService.Replacement(0L,"reason",List.of());assertThrows(ApiException.class,()->service.replace(1,input,7));when(identities.find(1)).thenReturn(IdentityReviewServiceTest.row("REVIEWED",1));assertEquals(409,assertThrows(ApiException.class,()->service.replace(1,input,7)).status().value());verifyNoInteractions(names,audit,graph);}
    @Test void normalizedDuplicatesAndUnprovenOrUnsupportedNamesCannotWrite(){when(identities.find(1)).thenReturn(IdentityReviewServiceTest.row("REVIEWED",0));when(graph.entity(anyString(),eq(false))).thenReturn(Map.of("type","CHARACTER"));
        for(var rows:List.of(List.of(new IdentityNameService.Input("Ａ","en","proof"),new IdentityNameService.Input("a","en","proof")),List.of(new IdentityNameService.Input("n","en","")),List.of(new IdentityNameService.Input("n\n","unknown","proof"))))assertThrows(ApiException.class,()->service.replace(1,new IdentityNameService.Replacement(0L,"reason",rows),7));verifyNoInteractions(names,audit);
    }
}
