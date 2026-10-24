package com.animegraph.explore;

import com.animegraph.common.ApiException;
import com.animegraph.viewer.BangumiClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Shared public pages for graph and credits; no viewer data or credentials. */
@Service
public class NetworkSourceCache {
    public record Snapshot(JsonNode data, Instant fetchedAt, boolean cached, boolean stale) {}
    private record Entry(JsonNode data, Instant at) {}
    private final BangumiClient client;
    private final Clock clock;
    private final Map<String, Entry> cache = Collections.synchronizedMap(new LinkedHashMap<>(128, .75f, true));
    private final ConcurrentHashMap<String, CompletableFuture<Snapshot>> pending = new ConcurrentHashMap<>();
    private final Semaphore permits = new Semaphore(8);

    @org.springframework.beans.factory.annotation.Autowired
    public NetworkSourceCache(BangumiClient client) { this(client, Clock.systemUTC()); }
    NetworkSourceCache(BangumiClient client, Clock clock) { this.client = client; this.clock = clock; }

    public Snapshot fetch(String path) {
        Entry old = cache.get(path); Instant now = clock.instant();
        if (old != null && old.at.plusSeconds(300).isAfter(now)) return new Snapshot(old.data, old.at, true, false);
        var promise = new CompletableFuture<Snapshot>(); var active = pending.putIfAbsent(path, promise);
        if (active != null) {
            try { return active.get(8, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw unavailable(); }
            catch (Exception e) { if (e.getCause() instanceof ApiException api) throw api; throw unavailable(); }
        }
        boolean acquired = false;
        try {
            Entry latest = cache.get(path);
            if (latest != null && latest.at.plusSeconds(300).isAfter(clock.instant())) {
                var value = new Snapshot(latest.data, latest.at, true, false); promise.complete(value); return value;
            }
            acquired = permits.tryAcquire();
            if (!acquired) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "NETWORK_BUSY", "在线资料请求较多，请稍后重试");
            Entry entry = new Entry(client.publicNetworkRequest(path), clock.instant());
            synchronized (cache) { cache.put(path, entry); while (cache.size() > 256) cache.remove(cache.keySet().iterator().next()); }
            var value = new Snapshot(entry.data, entry.at, false, false); promise.complete(value); return value;
        } catch (RuntimeException e) {
            boolean unavailableForReuse = e instanceof ApiException api && Set.of(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN).contains(api.status());
            if (unavailableForReuse) cache.remove(path);
            if (!unavailableForReuse && old != null && old.at.plusSeconds(1800).isAfter(now)) {
                var value = new Snapshot(old.data, old.at, true, true); promise.complete(value); return value;
            }
            promise.completeExceptionally(e); throw e;
        } finally { if (acquired) permits.release(); pending.remove(path, promise); }
    }
    private static ApiException unavailable() { return new ApiException(HttpStatus.BAD_GATEWAY, "NETWORK_UNAVAILABLE", "暂时无法读取在线资料，请重试"); }
}
