package com.animegraph.importing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.animegraph.audit.AuditService;
import com.animegraph.common.ApiException;
import com.animegraph.graph.GraphStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImportValidationTest {
    private ImportService service() {return new ImportService(mock(ImportMapper.class),mock(GraphStore.class),new ObjectMapper(),mock(AuditService.class),"target/import-validation");}
    @Test void rejectsMalformedAndEmptyPackagesBeforeCreatingTasks() {
        var service=service();
        try {
            for(String input:new String[]{"<html>","null","{}","{\"schemaVersion\":1,\"source\":\"bangumi\",\"entities\":[],\"relations\":[]}","{\"schemaVersion\":1,\"source\":\"../escape\",\"entities\":[{}],\"relations\":[]}"}) assertThrows(ApiException.class,() -> service.parse(input.getBytes(StandardCharsets.UTF_8)));
        } finally {service.close();}
    }
    @Test void recordErrorsRemainAddressableWithoutRejectingTheWholePackage() {
        var service=service();
        try {
            var data=service.parse("{\"schemaVersion\":1,\"source\":\"fixture\",\"entities\":[{\"type\":\"Person\",\"sourceId\":\"p1\",\"name\":\"甲\"},null],\"relations\":[{}]}".getBytes(StandardCharsets.UTF_8));
            assertEquals(3,data.items().size());assertEquals("entity:1",data.items().get(1).key());assertEquals("relation:0",data.items().get(2).key());
        } finally {service.close();}
    }
    @Test void stableUidsSeparateKindsAndRejectPathOrQuerySyntax() {
        assertNotEquals(GraphStore.uid(GraphStore.Kind.ANIME,"fixture","1"),GraphStore.uid(GraphStore.Kind.PERSON,"fixture","1"));
        assertThrows(ApiException.class,() -> GraphStore.uid(GraphStore.Kind.PERSON,"fixture","../p1"));
        assertThrows(ApiException.class,() -> GraphStore.Kind.parse("Person) DELETE n"));
    }
}
