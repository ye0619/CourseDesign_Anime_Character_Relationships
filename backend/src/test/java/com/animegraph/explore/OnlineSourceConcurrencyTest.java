package com.animegraph.explore;

import static org.assertj.core.api.Assertions.*;
import com.animegraph.common.ApiException;
import com.animegraph.viewer.BangumiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Real HTTP concurrency is deliberately confined to a loopback source. */
class OnlineSourceConcurrencyTest {
    @Test void coalescesThirtyTwoDetailReadersAndReleasesFailedRequestsForRetry() throws Exception {
        var calls=new AtomicInteger();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var status=new AtomicInteger(200);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        try(var handlers=Executors.newVirtualThreadPerTaskExecutor();var readers=Executors.newFixedThreadPool(32)) {
            server.setExecutor(handlers);
            server.createContext("/v0/subjects/",exchange -> {
                calls.incrementAndGet();entered.countDown();
                try{release.await(4,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                byte[] body="{\"id\":1,\"type\":2,\"name_cn\":\"本地模拟作品\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status.get(),body.length);exchange.getResponseBody().write(body);exchange.close();
            });server.start();
            var client=new BangumiClient(new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort(),"test/local/1");
            var service=new BangumiAnimeService(client);
            var gate=new CyclicBarrier(32);var results=new ArrayList<Future<?>>();
            for(int i=0;i<32;i++)results.add(readers.submit(() -> {gate.await();return service.detail(1);}));
            assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();release.countDown();
            for(var result:results)assertThat(result.get(5,TimeUnit.SECONDS)).isNotNull();
            assertThat(calls.get()).isEqualTo(1);
            status.set(429);
            assertThatThrownBy(() -> service.detail(2)).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("BANGUMI_RATE_LIMITED"));
            status.set(200);
            // An error must not stay in the in-flight map or consume a permit.
            assertThat(service.detail(1).cached()).isTrue();
            assertThatThrownBy(() -> service.detail(2)).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("NOT_ANIME"));
            assertThat(calls.get()).isEqualTo(3);
        } finally {release.countDown();server.stop(0);}
    }

    @Test void boundsDistinctNetworkRequestsAtEightAndCoalescesDuplicateReaders() throws Exception {
        var active=new AtomicInteger();var peak=new AtomicInteger();var calls=new AtomicInteger();
        var entered=new CountDownLatch(8);var release=new CountDownLatch(1);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        try(var handlers=Executors.newVirtualThreadPerTaskExecutor();var readers=Executors.newFixedThreadPool(24)) {
            server.setExecutor(handlers);
            server.createContext("/p1/characters/",exchange -> {
                calls.incrementAndGet();peak.accumulateAndGet(active.incrementAndGet(),Math::max);entered.countDown();
                try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                byte[] body="{\"total\":0,\"data\":[]}".getBytes(StandardCharsets.UTF_8);
                active.decrementAndGet();exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
            });server.start();
            var client=new BangumiClient(new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort(),"test/local/1");
            var service=new OnlineNetworkService(client,new BangumiAnimeService(client));
            var results=new ArrayList<Future<?>>();
            for(int i=1;i<=8;i++){long id=i;results.add(readers.submit(() -> service.relations(id)));}
            assertThat(entered.await(4,TimeUnit.SECONDS)).isTrue();
            for(int i=0;i<8;i++)results.add(readers.submit(() -> service.relations(1)));
            assertThatThrownBy(() -> service.relations(9)).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("NETWORK_BUSY"));
            release.countDown();for(var result:results)assertThat(result.get(5,TimeUnit.SECONDS)).isNotNull();
            assertThat(peak.get()).isEqualTo(8);assertThat(calls.get()).isEqualTo(8);
            assertThat(service.relations(9).edges()).isEmpty();assertThat(calls.get()).isEqualTo(9);
        } finally {release.countDown();server.stop(0);}
    }
}
