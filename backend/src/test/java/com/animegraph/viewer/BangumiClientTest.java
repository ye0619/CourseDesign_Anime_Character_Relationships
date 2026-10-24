package com.animegraph.viewer;

import static org.assertj.core.api.Assertions.*;
import com.animegraph.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BangumiClientTest {
    @Test void cancelsStalledBodiesAndRejectsOversizedAndMalformedResponses() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var release=new java.util.concurrent.CountDownLatch(1);
        try(var handlers=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(handlers);
            server.createContext("/p1/characters/1/relations",exchange -> {
                exchange.sendResponseHeaders(200,0);exchange.getResponseBody().write('{');exchange.getResponseBody().flush();
                try{release.await(3,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                exchange.close();
            });
            server.createContext("/p1/characters/2/relations",exchange -> {
                byte[] bytes=new byte[2_000_001];exchange.sendResponseHeaders(200,bytes.length);
                try{exchange.getResponseBody().write(bytes);}catch(java.io.IOException ignored){}finally{exchange.close();}
            });
            server.createContext("/p1/characters/3/relations",exchange -> {
                byte[] bytes="not-json".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });server.start();
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            var client=new BangumiClient(new ObjectMapper(),base,"test/local/1","",base,java.time.Duration.ofMillis(350));
            long start=System.nanoTime();
            assertThatThrownBy(() -> client.publicNetworkRequest("/p1/characters/1/relations?limit=100&offset=0")).isInstanceOf(ApiException.class);
            assertThat(java.time.Duration.ofNanos(System.nanoTime()-start).toMillis()).isLessThan(2000);
            release.countDown();
            for(int id:new int[]{2,3})assertThatThrownBy(() -> client.publicNetworkRequest("/p1/characters/"+id+"/relations?limit=100&offset=0")).isInstanceOf(ApiException.class);
        } finally {release.countDown();server.stop(0);}
    }
    @Test void networkReadUsesOnlyWhitelistedPublicPathsWithoutCredentials() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/p1/characters/86246/relations",exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isNull();
            assertThat(exchange.getRequestHeaders().getFirst("Cookie")).isNull();
            byte[] bytes="{\"total\":0,\"data\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var client=new BangumiClient(new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort(),"test/project/1.0");
            assertThat(client.publicNetworkRequest("/p1/characters/86246/relations?limit=100&offset=0").path("total").asInt()).isZero();
            for(String path:new String[]{"https://evil.test/x","/p1/users/me","/p1/characters/1/relations?limit=100&offset=0&token=x","/p1/characters/../me/relations?limit=100&offset=0"})
                assertThatThrownBy(() -> client.publicNetworkRequest(path)).isInstanceOf(ApiException.class);
        } finally {server.stop(0);}
    }
    @Test void publicAnimeSearchPostsOnlySearchCriteriaWithoutViewerCredentials() throws Exception {
        var json=new ObjectMapper();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v0/search/subjects",exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isNull();
            var body=json.readTree(exchange.getRequestBody());assertThat(body.path("keyword").asText()).isEqualTo("中文");
            byte[] bytes="{\"total\":0,\"data\":[]}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var client=new BangumiClient(json,"http://127.0.0.1:"+server.getAddress().getPort(),"test/project/1.0");
            assertThat(client.publicAnimeRequest("/v0/search/subjects?limit=12&offset=0",java.util.Map.of("keyword","中文")).path("total").asInt()).isZero();
            assertThatThrownBy(() -> client.publicAnimeRequest("https://evil.test",null)).isInstanceOf(ApiException.class);
        } finally {server.stop(0);}
    }
    @Test void usernamesCannotTurnIntoArbitraryUrlsOrPaths() {
        assertThat(BangumiClient.normalizeUsername(" https://bgm.tv/user/sai ")).isEqualTo("sai");
        assertThat(BangumiClient.normalizeUsername("12345")).isEqualTo("12345");
        for(String value:new String[]{"https://evil.test/user/sai","https://bgm.tv@evil.test/user/sai","https://bgm.tv/user/sai?token=x","../me","https://bgm.tv/user/sai/../me","昵称"})
            assertThatThrownBy(() -> BangumiClient.normalizeUsername(value)).isInstanceOf(ApiException.class);
    }
    @Test void mapsCollectionsWithoutCopyingCommentsAndNeverFollowsRedirects() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v0/users/sai/collections",exchange -> {
            assertThat(exchange.getRequestURI().getQuery()).contains("subject_type=2","offset=100");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer local-test-token");
            byte[] bytes="""
                {"total":101,"data":[{"subject_id":12,"subject_type":2,"type":2,"rate":8,"comment":"never store this","tags":["my tag"],"subject":{"name":"original","name_cn":"中文名","images":{"common":"https://lain.bgm.tv/a.jpg"},"tags":[{"name":"科幻"},{"name":"科幻"}],"score":9.5}}]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.createContext("/v0/users/redirect",exchange -> {exchange.getResponseHeaders().set("Location","https://example.com");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.createContext("/v0/me",exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer local-test-token");
            byte[] bytes="{\"id\":77,\"username\":\"verified-owner\",\"nickname\":\"Owner\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            var client=new BangumiClient(new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort(),"test/project/1.0");
            var page=client.collections("sai",100,"local-test-token");
            assertThat(page.total()).isEqualTo(101);
            assertThat(page.items().getFirst().title()).isEqualTo("中文名");
            assertThat(page.items().getFirst().communityTags()).containsExactly("科幻");
            assertThat(client.profile("someone-else","local-test-token").username()).isEqualTo("verified-owner");
            assertThat(new ViewerController.BindRequest("sai","secret-do-not-log").toString()).doesNotContain("secret-do-not-log");
            assertThatThrownBy(() -> client.profile("redirect",null)).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("BANGUMI_UNAVAILABLE"));
        } finally {server.stop(0);}
    }
}
