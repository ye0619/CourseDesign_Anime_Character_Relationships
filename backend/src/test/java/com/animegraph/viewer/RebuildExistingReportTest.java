package com.animegraph.viewer;

import static org.junit.jupiter.api.Assertions.*;
import com.animegraph.explore.*;
import com.animegraph.graph.GraphStore;
import com.animegraph.identity.NameResolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.neo4j.driver.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit one-off repair for the uniquely identified existing v4 three-company report. Never binds or synchronizes accounts. */
@EnabledIfSystemProperty(named="report.rebuild",matches="true")
class RebuildExistingReportTest {
    @Test void regenerateTheExistingThreeCompanyReportFromSavedPublicSources()throws Exception {
        String url=System.getenv("MYSQL_URL");assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:")||url.startsWith("jdbc:mysql://localhost:"));
        var json=new ObjectMapper().findAndRegisterModules();var ds=new DriverManagerDataSource(url,System.getenv("MYSQL_USER"),System.getenv("MYSQL_PASSWORD"));
        String id,old;try(var connection=ds.getConnection();var statement=connection.createStatement();var rows=statement.executeQuery("SELECT r.viewer_id,r.payload FROM viewer_report r JOIN viewer_binding b ON b.id=r.viewer_id WHERE JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.version'))='viewing-report/4' AND JSON_LENGTH(JSON_EXTRACT(r.payload,'$.companies'))=3 AND b.revision=r.binding_revision AND b.last_synced_at <=> r.collection_time")) {
            assertTrue(rows.next(),"No matching current v4 report; do not guess an account");id=rows.getString(1);old=rows.getString(2);assertFalse(rows.next(),"More than one matching report; refuse ambiguous repair");
        }
        Path output=Path.of(System.getProperty("report.rebuild.directory"));Files.createDirectories(output);Files.writeString(output.resolve("private-report-before.json"),old,StandardOpenOption.CREATE_NEW);
        var configuration=new Configuration(new Environment("local-report-rebuild",new JdbcTransactionFactory(),ds));configuration.setMapUnderscoreToCamelCase(true);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setDefaultStatementTimeout(3);
        for(var mapper:List.of(ViewerMapper.class,ViewerAnalysisMapper.class,MetadataMapper.class))configuration.addMapper(mapper);
        var sessions=SqlSessionManager.newInstance(new SqlSessionFactoryBuilder().build(configuration));
        try(var driver=GraphDatabase.driver(System.getenv("NEO4J_URI"),AuthTokens.basic(System.getenv("NEO4J_USERNAME"),System.getenv("NEO4J_PASSWORD")))) {
            var viewerMapper=sessions.getMapper(ViewerMapper.class);var analysis=sessions.getMapper(ViewerAnalysisMapper.class);
            var viewers=new ViewerService(viewerMapper,null,json,new TransactionTemplate(new DataSourceTransactionManager(ds)),new PreferenceService());
            var snapshots=new SubjectSnapshotService(null,sessions.getMapper(MetadataMapper.class),json);
            var reports=new ViewingReportService(viewers,analysis,snapshots,json,new LocalReportCredits(new GraphStore(driver,System.getenv("NEO4J_DATABASE"),json),json));
            var raw=new SavedRawCredits(json,new NameResolutionService(json),"../data/raw/bangumi");var field=ViewingReportService.class.getDeclaredField("raw");field.setAccessible(true);field.set(reports,raw);
            var before=viewers.snapshot(id);try {
                reports.start(id,false);ViewingReportService.Result result=null;long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                do {Thread.sleep(50);result=reports.read(id);}while(result.task()!=null&&Set.of("RUNNING","PENDING").contains(result.task().status())&&System.nanoTime()<deadline);
                assertNotNull(result.report());assertEquals("viewing-report/5",result.report().version());assertTrue(result.report().companies().size()>3,"Saved-source correction did not increase company coverage");
                assertEquals(before,viewers.snapshot(id),"Collection and binding must remain unchanged");
                var record=Map.of("checkedAt",Instant.now(),"beforeCompanies",3,"afterCompanies",result.report().companies().size(),"completed",result.report().completed(),"coveredCompanyWorks",result.report().companies().stream().flatMap(c->c.works().stream()).map(ViewerModels.Collection::subjectId).distinct().count(),"reportVersion",result.report().version(),"collectionAndBindingUnchanged",true,"networkRequests",0,"status",result.task().status());
                Files.writeString(output.resolve("company-repair-summary.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(record));
                System.out.println(json.writeValueAsString(record));
            }finally{reports.close();viewers.close();}
        }
    }
}
