package com.animegraph.identity;
import com.animegraph.audit.AuditService;
import com.animegraph.common.ApiException;
import com.animegraph.graph.GraphStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentityReviewServiceTest {
    IdentityReviewMapper mapper;GraphStore graph;AuditService audit;IdentityReviewService service;
    @BeforeEach void setup(){mapper=mock(IdentityReviewMapper.class);graph=mock(GraphStore.class);audit=mock(AuditService.class);service=new IdentityReviewService(mapper,graph,audit,new ObjectMapper().findAndRegisterModules());when(graph.entity(anyString(),eq(false))).thenReturn(Map.of("type","CHARACTER"));}
    static IdentityReviewMapper.Identity row(String status,long version){return new IdentityReviewMapper.Identity(1,"PROJECT","fixture","CHARACTER","character:bangumi:87968",status,"https://example.org/proof","r1","MANUAL","fixture license",version,"stable source ID and explicit original name","reason",7L,Instant.EPOCH);}
    static IdentityReviewService.Decision decision(long version,String action,String target){return new IdentityReviewService.Decision(version,action,target,"核对后操作",null,null,null,null);}
    @Test void createsCandidateWithoutImplicitReview() {
        doAnswer(i->{Map<String,Object> m=i.getArgument(0);m.put("id",1L);return null;}).when(mapper).insert(anyMap());when(mapper.find(1)).thenReturn(row("CANDIDATE",0));
        var result=service.create(new IdentityReviewService.Candidate("PROJECT","fixture","CHARACTER","character:bangumi:87968","https://example.org/proof","r1","license","original proof","review reason"),7);
        assertEquals("CANDIDATE",result.status());verify(mapper).event(eq(1L),eq("CREATE"),eq(7L),anyString(),isNull(),contains("CANDIDATE"));verify(audit).record(eq(7L),eq("IDENTITY_CREATE"),anyString(),anyString(),anyString());
    }
    @Test void staleVersionAndConcurrentUpdateCannotWriteHistory(){
        when(mapper.find(1)).thenReturn(row("CANDIDATE",1));assertThrows(ApiException.class,()->service.decide(1,decision(0,"REVIEW",null),7));verify(mapper,never()).event(anyLong(),anyString(),anyLong(),anyString(),any(),anyString());
        when(mapper.find(1)).thenReturn(row("CANDIDATE",0));assertThrows(ApiException.class,()->service.decide(1,decision(0,"REVIEW",null),7));verifyNoInteractions(audit);
    }
    @Test void remapAlwaysReturnsToCandidateAndKeepsBeforeState(){
        when(mapper.find(1)).thenReturn(row("REVIEWED",0),row("CANDIDATE",1));when(mapper.update(anyLong(),anyLong(),anyString(),anyString(),any(),any(),anyString(),anyString(),anyString(),anyLong(),any())).thenReturn(1);
        service.decide(1,decision(0,"REMAP","character:bangumi:87973"),7);
        verify(mapper).update(eq(1L),eq(0L),eq("character:bangumi:87973"),eq("CANDIDATE"),any(),any(),anyString(),anyString(),anyString(),eq(7L),isNull());verify(mapper).event(eq(1L),eq("REMAP"),eq(7L),anyString(),contains("REVIEWED"),contains("CANDIDATE"));
    }
    @Test void revokedAndRejectedRequireExplicitNewRevisionBeforeReview(){
        for(String status:List.of("REVOKED","REJECTED")){when(mapper.find(1)).thenReturn(row(status,0));assertThrows(ApiException.class,()->service.decide(1,decision(0,"REVIEW",null),7));assertThrows(ApiException.class,()->service.decide(1,decision(0,"REMAP","character:bangumi:87973"),7));var same=new IdentityReviewService.Decision(0L,"REOPEN",null,"reason","r1","new proof","https://example.org/proof","license");assertThrows(ApiException.class,()->service.decide(1,same,7));}
        verifyNoInteractions(audit);
    }
    @Test void reviewCannotSilentlyRemapOrApproveMissingProof(){when(mapper.find(1)).thenReturn(row("CANDIDATE",0));assertThrows(ApiException.class,()->service.decide(1,decision(0,"REVIEW","character:bangumi:87973"),7));var r=row("CANDIDATE",0);when(mapper.find(1)).thenReturn(new IdentityReviewMapper.Identity(r.id(),r.provider(),r.providerId(),r.entityType(),r.canonicalUid(),r.status(),r.sourceUrl(),null,r.matchMethod(),r.licenseLabel(),0,null,"",null,r.updatedAt()));assertThrows(ApiException.class,()->service.decide(1,decision(0,"REVIEW",null),7));}
    @Test void validatesTypeSourceAndBoundedListing(){assertThrows(ApiException.class,()->service.create(new IdentityReviewService.Candidate("PROJECT","fixture","PERSON","character:bangumi:87968","https://example.org","r1","l","e","r"),7));assertThrows(ApiException.class,()->service.list("unknown","",0,30));assertThrows(ApiException.class,()->service.list("","",0,51));assertThrows(ApiException.class,()->service.list("","",-1,30));verifyNoInteractions(mapper);}
    @Test void rejectsSpoofedSourceUrls(){for(String url:List.of("https://wikidata.org.evil.test/Q1","http://www.wikidata.org/Q1","https://u:p@www.wikidata.org/Q1","https://www.wikidata.org:444/Q1","javascript:alert(1)"))assertThrows(ApiException.class,()->IdentityReviewService.sourceUrl(url,"WIKIDATA"));assertEquals("https://www.wikidata.org/wiki/Q1",IdentityReviewService.sourceUrl("https://www.wikidata.org/wiki/Q1","WIKIDATA"));}
    @Test void titleMetadataCannotBeReviewedOrAmendedWithIdenticalEvidence(){
        var r=row("CANDIDATE",0);var title=new IdentityReviewMapper.Identity(r.id(),r.provider(),r.providerId(),r.entityType(),r.canonicalUid(),r.status(),r.sourceUrl(),r.sourceRevision(),"TITLE_ONLY",r.licenseLabel(),r.version(),r.evidenceText(),r.reviewReason(),r.reviewedBy(),r.updatedAt());
        when(mapper.find(1)).thenReturn(title);
        assertThrows(ApiException.class,()->service.decide(1,decision(0,"REVIEW",null),7));
        assertThrows(ApiException.class,()->service.decide(1,new IdentityReviewService.Decision(0L,"AMEND",null,"reason","r1",r.evidenceText(),r.sourceUrl(),r.licenseLabel()),7));
        verifyNoInteractions(audit);verify(mapper,never()).update(anyLong(),anyLong(),anyString(),anyString(),any(),any(),anyString(),anyString(),anyString(),anyLong(),any());
    }
    @Test void candidateMethodIsExplicitAndNeverAcceptsReviewedFlags(){
        assertThrows(ApiException.class,()->service.create(new IdentityReviewService.Candidate("PROJECT","fixture","CHARACTER","character:bangumi:87968","https://example.org","r1","l","e","r","REVIEWED"),7));
        verifyNoInteractions(mapper);
    }
}
