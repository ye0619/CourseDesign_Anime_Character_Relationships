package com.animegraph.identity;

import com.animegraph.audit.AuditService;
import com.animegraph.common.ApiException;
import com.animegraph.graph.GraphStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.*;
import org.neo4j.driver.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="identity.integration",matches="true")
class IdentityReviewIntegrationTest {
    static final AtomicBoolean FAIL_AUDIT=new AtomicBoolean();
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource(env("MYSQL_URL"),env("MYSQL_USER"),env("MYSQL_PASSWORD"));}
        @Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
        @Bean SqlSessionFactory factory(DataSource source)throws Exception{var factory=new SqlSessionFactoryBean();factory.setDataSource(source);var config=new org.apache.ibatis.session.Configuration();config.setMapUnderscoreToCamelCase(true);config.setArgNameBasedConstructorAutoMapping(true);config.addMapper(IdentityNameMapper.class);config.addMapper(IdentityReviewMapper.class);config.addMapper(ExternalIdentityMapper.class);factory.setConfiguration(config);return factory.getObject();}
        @Bean IdentityReviewMapper mapper(SqlSessionFactory factory){return new SqlSessionTemplate(factory).getMapper(IdentityReviewMapper.class);}
        @Bean IdentityNameMapper nameMapper(SqlSessionFactory factory){return new SqlSessionTemplate(factory).getMapper(IdentityNameMapper.class);}
        @Bean IdentityNameService nameService(IdentityNameMapper names,IdentityReviewMapper identities,GraphStore graph,AuditService audit,ObjectMapper json){return new IdentityNameService(names,identities,graph,audit,json);}
        @Bean ReviewedNameProjection projection(IdentityNameMapper names,ObjectMapper json){return new ReviewedNameProjection(names,json);}
        @Bean ExternalIdentityMapper publicMapper(SqlSessionFactory factory){return new SqlSessionTemplate(factory).getMapper(ExternalIdentityMapper.class);}
        @Bean JdbcTemplate jdbc(DataSource source){return new JdbcTemplate(source);}
        @Bean ObjectMapper json(){return new ObjectMapper().findAndRegisterModules();}
        @Bean(destroyMethod="close") Driver driver(){return GraphDatabase.driver(env("NEO4J_URI"),AuthTokens.basic(env("NEO4J_USERNAME"),env("NEO4J_PASSWORD")));}
        @Bean GraphStore graph(Driver driver,ObjectMapper json){return new GraphStore(driver,env("NEO4J_DATABASE"),json);}
        @Bean AuditService audit(JdbcTemplate jdbc){return new AuditService(jdbc){@Override public void record(Long actor,String action,String type,String uid,String summary){if(FAIL_AUDIT.get())throw new IllegalStateException("Injected audit failure");super.record(actor,action,type,uid,summary);}};}
        @Bean IdentityReviewService service(IdentityReviewMapper mapper,GraphStore graph,AuditService audit,ObjectMapper json){return new IdentityReviewService(mapper,graph,audit,json);}
    }
    static String env(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalStateException("Missing integration configuration "+key);return value;}
    static IdentityReviewService.Candidate candidate(String key){return new IdentityReviewService.Candidate("PROJECT",key,"CHARACTER","character:bangumi:87968","https://example.org/identity-fixture","fixture-r1","Integration fixture only","Read-only target identity check, synthetic fixture proof","integration fixture");}
    static IdentityReviewService.Decision decision(long version,String action,String target){return new IdentityReviewService.Decision(version,action,target,"integration fixture",null,null,null,null);}
    @Test void realSqlTransactionHistoryConcurrencyAndSeedWithdrawal()throws Exception{
        var ids=new ArrayList<Long>();var prefix="identity-fixture-"+UUID.randomUUID();
        try(var context=new AnnotationConfigApplicationContext(Config.class)){
            var service=context.getBean(IdentityReviewService.class);var mapper=context.getBean(IdentityReviewMapper.class);var publicMapper=context.getBean(ExternalIdentityMapper.class);var jdbc=context.getBean(JdbcTemplate.class);
            long actor=jdbc.queryForObject("SELECT id FROM sys_user WHERE enabled=true ORDER BY id LIMIT 1",Long.class);
            try{
                var row=service.create(candidate(prefix),actor);ids.add(row.id());assertEquals("CANDIDATE",row.status());assertThrows(ApiException.class,()->service.create(candidate(prefix),actor));
                assertTrue(publicMapper.identities("character:bangumi:87968").stream().anyMatch(r->prefix.equals(r.get("provider_id"))&&"CANDIDATE".equals(r.get("status"))));
                row=service.decide(row.id(),decision(0,"REVIEW",null),actor);assertEquals("REVIEWED",row.status());
                row=service.decide(row.id(),decision(1,"REMAP","character:bangumi:87973"),actor);assertEquals("CANDIDATE",row.status());assertEquals("character:bangumi:87973",row.canonicalUid());
                assertTrue(publicMapper.identities("character:bangumi:87968").stream().noneMatch(r->prefix.equals(r.get("provider_id"))));
                row=service.decide(row.id(),decision(2,"REVIEW",null),actor);row=service.decide(row.id(),decision(3,"REVOKE",null),actor);
                publicMapper.seed(Map.of("provider","PROJECT","providerId",prefix,"entityType","CHARACTER","canonicalUid","character:bangumi:87968","status","REVIEWED","sourceUrl","https://example.org/stale","sourceRevision","old","matchMethod","MANUAL","licenseLabel","old"));
                assertEquals("REVOKED",mapper.find(row.id()).status());assertEquals(4,mapper.find(row.id()).version());assertTrue(publicMapper.identities("character:bangumi:87973").stream().noneMatch(r->prefix.equals(r.get("provider_id"))));
                row=service.decide(row.id(),new IdentityReviewService.Decision(4L,"REOPEN",null,"new fixture revision","fixture-r2","new fixture proof","https://example.org/new-fixture","fixture license"),actor);assertEquals("CANDIDATE",row.status());
                final long id=row.id();int eventCount=mapper.history(id).size();FAIL_AUDIT.set(true);
                try{assertThrows(IllegalStateException.class,()->service.decide(id,decision(5,"REVIEW",null),actor));}finally{FAIL_AUDIT.set(false);}
                assertEquals(5,mapper.find(id).version());assertEquals("CANDIDATE",mapper.find(id).status());assertEquals(eventCount,mapper.history(id).size());
                row=service.decide(id,decision(5,"REVIEW",null),actor);assertEquals(6,row.version());assertEquals(7,mapper.history(id).size());assertEquals(7,jdbc.queryForObject("SELECT count(*) FROM operation_log WHERE target_type='EXTERNAL_IDENTITY' AND target_uid=?",Integer.class,"identity:"+id));
                // Case-sensitive source IDs remain distinct; neither is inferred from a name.
                var lower=service.create(candidate(prefix+"-a"),actor);ids.add(lower.id());var upper=service.create(candidate(prefix+"-A"),actor);ids.add(upper.id());assertNotEquals(lower.id(),upper.id());
                jdbc.update("UPDATE external_identity SET evidence_text=NULL WHERE id=?",upper.id());
                var amended=service.decide(upper.id(),new IdentityReviewService.Decision(0L,"AMEND",null,"complete legacy candidate","fixture-r2","completed proof","https://example.org/amended-fixture","fixture license"),actor);assertEquals("CANDIDATE",amended.status());assertEquals("completed proof",amended.evidenceText());assertEquals("https://example.org/amended-fixture",amended.sourceUrl());
                assertEquals("REJECTED",service.decide(upper.id(),decision(1,"REJECT",null),actor).status());
                var title=service.create(new IdentityReviewService.Candidate("PROJECT",prefix+"-title","CHARACTER","character:bangumi:87968","https://example.org/title-only","r1","fixture license","title metadata only","integration fixture","TITLE_ONLY"),actor);ids.add(title.id());assertEquals("TITLE_ONLY",title.matchMethod());
                assertThrows(ApiException.class,()->service.decide(title.id(),decision(0,"REVIEW",null),actor));
                assertThrows(ApiException.class,()->service.decide(title.id(),new IdentityReviewService.Decision(0L,"AMEND",null,"unchanged proof","r1","title metadata only",title.sourceUrl(),title.licenseLabel()),actor));
                assertEquals(1,mapper.history(title.id()).size());assertEquals(0,mapper.find(title.id()).version());
                var verified=service.decide(title.id(),new IdentityReviewService.Decision(0L,"AMEND",null,"fixture manual original-name and work check","r1","new original-name and work attribution proof",title.sourceUrl(),title.licenseLabel()),actor);assertEquals("MANUAL",verified.matchMethod());assertEquals("CANDIDATE",verified.status());
                assertEquals("REVIEWED",service.decide(title.id(),decision(1,"REVIEW",null),actor).status());assertEquals(3,mapper.history(title.id()).size());
                var nameService=context.getBean(IdentityNameService.class);var nameMapper=context.getBean(IdentityNameMapper.class);var projection=context.getBean(ReviewedNameProjection.class);var graph=context.getBean(GraphStore.class);var catalog=new com.animegraph.catalog.CatalogService(graph,projection);
                String alias="ＦｉｘｔｕｒｅAlias-"+prefix;String query=IdentityNameService.normalize(alias);var graphBefore=graph.entity("character:bangumi:87968",false);
                var nameInput=List.of(new IdentityNameService.Input(alias,"en","Synthetic original-name and work proof, not a real external fact"));
                var names=nameService.replace(title.id(),new IdentityNameService.Replacement(2L,"fixture alias review",nameInput),actor);assertTrue(names.activeForSearch());assertEquals(3,names.identity().version());assertEquals(3,names.names().getFirst().reviewVersion());assertEquals(List.of("character:bangumi:87968"),projection.search("CHARACTER",query));
                var cachedSource=Map.of("nodes",List.of(Map.of("id","character:bangumi:87968","label","来源显示名","originalName","source original","aliases",List.of("source alias"))));var enriched=projection.project(cachedSource,"nodes");assertEquals(2,enriched.at("/nodes/0/aliases").size());assertEquals("PROJECT",enriched.at("/nodes/0/nameAssertions/0/provider").asText());assertEquals(3,enriched.at("/nodes/0/nameAssertions/0/identityVersion").asInt());assertEquals("来源显示名",enriched.at("/nodes/0/label").asText());
                var found=catalog.list("CHARACTER",query,null,null,true,1,1,"bangumi");assertEquals(1,found.total());assertEquals("character:bangumi:87968",found.items().getFirst().get("uid"));assertEquals(graphBefore,graph.entity("character:bangumi:87968",false));
                FAIL_AUDIT.set(true);try{assertThrows(IllegalStateException.class,()->nameService.replace(title.id(),new IdentityNameService.Replacement(3L,"injected failure",List.of()),actor));}finally{FAIL_AUDIT.set(false);}assertEquals(3,mapper.find(title.id()).version());assertEquals(1,nameMapper.stored(title.id()).size());assertEquals(4,mapper.history(title.id()).size());
                assertThrows(ApiException.class,()->nameService.replace(title.id(),new IdentityNameService.Replacement(2L,"stale",nameInput),actor));
                var second=service.create(new IdentityReviewService.Candidate("PROJECT",prefix+"-same-name","CHARACTER","character:bangumi:87973","https://example.org/fixture-second","r1","fixture license","synthetic proof","fixture"),actor);ids.add(second.id());service.decide(second.id(),decision(0,"REVIEW",null),actor);nameService.replace(second.id(),new IdentityNameService.Replacement(1L,"same spelling, distinct stable entity",nameInput),actor);assertEquals(2,projection.search("CHARACTER",query).size());assertEquals(2,catalog.list("CHARACTER",query,null,null,true,1,1,"bangumi").total());
                service.decide(title.id(),decision(3,"REVOKE",null),actor);assertFalse(nameService.detail(title.id()).activeForSearch());assertEquals(1,projection.project(cachedSource,"nodes").at("/nodes/0/aliases").size());assertEquals(List.of("character:bangumi:87973"),projection.search("CHARACTER",query));
                service.decide(title.id(),new IdentityReviewService.Decision(4L,"REOPEN",null,"new revision","names-r2","new identity proof","https://example.org/names-r2","fixture license"),actor);service.decide(title.id(),decision(5,"REVIEW",null),actor);assertFalse(nameService.detail(title.id()).activeForSearch());assertEquals(List.of("character:bangumi:87973"),projection.search("CHARACTER",query));
                service.decide(title.id(),decision(6,"REMAP","character:bangumi:87973"),actor);service.decide(title.id(),decision(7,"REVIEW",null),actor);assertFalse(nameService.detail(title.id()).activeForSearch());
                var rebound=nameService.replace(title.id(),new IdentityNameService.Replacement(8L,"explicitly rechecked after remap",nameInput),actor);assertTrue(rebound.activeForSearch());assertEquals("character:bangumi:87973",rebound.names().getFirst().canonicalUid());assertEquals("names-r2",rebound.names().getFirst().sourceRevision());
                var nameStart=new CountDownLatch(1);var namePool=Executors.newFixedThreadPool(2);try{var attempts=new ArrayList<Future<Boolean>>();for(var replacement:List.of(nameInput,List.<IdentityNameService.Input>of()))attempts.add(namePool.submit(()->{nameStart.await();try{nameService.replace(title.id(),new IdentityNameService.Replacement(9L,"concurrent names",replacement),actor);return true;}catch(ApiException e){assertEquals(409,e.status().value());return false;}}));nameStart.countDown();int saved=0;for(var attempt:attempts)if(attempt.get(15,TimeUnit.SECONDS))saved++;assertEquals(1,saved);assertEquals(10,mapper.find(title.id()).version());}finally{namePool.shutdownNow();}
                nameService.replace(title.id(),new IdentityNameService.Replacement(10L,"explicit clear",List.of()),actor);assertEquals(0,nameMapper.stored(title.id()).size());assertTrue(mapper.history(title.id()).stream().anyMatch(e->"NAMES".equals(e.get("action"))&&e.get("beforeState").toString().contains("names-r2")));
                service.decide(second.id(),decision(2,"REVOKE",null),actor);assertTrue(projection.search("CHARACTER",query).isEmpty());
                var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
                try{var futures=new ArrayList<Future<Boolean>>();for(String action:List.of("REVIEW","REJECT"))futures.add(pool.submit(()->{start.await();try{service.decide(lower.id(),decision(0,action,null),actor);return true;}catch(ApiException e){assertEquals(409,e.status().value());return false;}}));start.countDown();int successes=0;for(var f:futures)if(f.get(15,TimeUnit.SECONDS))successes++;assertEquals(1,successes);assertEquals(1,mapper.find(lower.id()).version());assertEquals(2,mapper.history(lower.id()).size());}finally{pool.shutdownNow();}
            }finally{
                FAIL_AUDIT.set(false);
                for(long id:ids){jdbc.update("DELETE FROM external_identity_name WHERE identity_id=?",id);jdbc.update("DELETE FROM external_identity_event WHERE identity_id=?",id);jdbc.update("DELETE FROM operation_log WHERE target_type='EXTERNAL_IDENTITY' AND target_uid=?","identity:"+id);jdbc.update("DELETE FROM external_identity WHERE id=? AND provider='PROJECT' AND provider_id LIKE ?",id,prefix+"%");assertNull(mapper.find(id));}
            }
        }
    }
}
