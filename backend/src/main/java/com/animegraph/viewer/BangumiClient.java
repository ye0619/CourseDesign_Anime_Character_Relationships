package com.animegraph.viewer;

import static com.animegraph.viewer.ViewerModels.*;
import com.animegraph.common.ApiException;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class BangumiClient {
    private final HttpClient http;
    private final ObjectMapper json;
    private final String base,userAgent,networkBase;
    private final Duration publicTimeout;
    @org.springframework.beans.factory.annotation.Autowired
    public BangumiClient(ObjectMapper json,@Value("${app.bangumi.api-base:https://api.bgm.tv}")String base,
                         @Value("${app.bangumi.user-agent:AnimeGraphGraduation/local-viewer/0.6}")String userAgent,
                         @Value("${app.bangumi.proxy:}")String proxy,
                         @Value("${app.bangumi.network-base:https://next.bgm.tv}")String networkBase) {
        this(json,base,userAgent,proxy,networkBase,Duration.ofSeconds(6));
    }
    BangumiClient(ObjectMapper json,String base,String userAgent,String proxy,String networkBase,Duration publicTimeout) {
        this.publicTimeout=publicTimeout;
        this.json=json;this.base=base.replaceAll("/+$","");this.userAgent=userAgent;this.networkBase=networkBase.replaceAll("/+$","");
        var builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER);
        if(!proxy.isBlank()) {
            URI uri=URI.create(proxy);
            if(!"http".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException("BANGUMI_PROXY must be an HTTP proxy URL without credentials");
            builder.proxy(ProxySelector.of(new InetSocketAddress(uri.getHost(),uri.getPort()<0?80:uri.getPort())));
        }
        http=builder.build();
    }
    public BangumiClient(ObjectMapper json,String base,String userAgent) {this(json,base,userAgent,"",base);}
    public Profile profile(String username,String token) {return profile(read(token==null?"/v0/users/"+normalizeUsername(username):"/v0/me",token));}
    private Profile profile(JsonNode node) {
        if(!node.path("id").canConvertToLong() || node.path("id").asLong()<1) throw unavailable();
        String username=node.path("username").asText("");if(username.isBlank()) username=node.path("id").asText();
        return new Profile(node.path("id").asLong(),username,node.path("nickname").asText(username),safeImage(node.path("avatar").path("medium").asText(null)));
    }
    public RemotePage collections(String username,int offset,String token) {
        JsonNode node=read("/v0/users/"+normalizeUsername(username)+"/collections?subject_type=2&limit=100&offset="+offset,token);
        if(!node.path("data").isArray() || !node.path("total").canConvertToInt()) throw unavailable();
        var items=new ArrayList<ViewerModels.Collection>();
        for(var row:node.path("data")) {
            var subject=row.path("subject");
            if(row.path("subject_type").asInt()!=2 || subject.isMissingNode() || subject.isNull()) continue;
            long id=row.path("subject_id").asLong();int status=row.path("type").asInt();
            if(id<1 || status<1 || status>5) continue;
            String original=subject.path("name").asText("未命名动画"),title=subject.path("name_cn").asText("");
            items.add(new ViewerModels.Collection(id,status,Math.max(0,Math.min(10,row.path("rate").asInt())),title.isBlank()?original:title,original,
                safeImage(subject.path("images").path("common").asText(null)),subject.path("date").asText(""),Math.max(0,subject.path("eps").asInt()),
                Math.max(0,row.path("ep_status").asInt()),subject.path("score").asDouble(),row.path("updated_at").asText(""),tags(row.path("tags")),tags(subject.path("tags"))));
        }
        return new RemotePage(Math.max(0,node.path("total").asInt()),items);
    }
    static List<String> tags(JsonNode node) {
        var result=new LinkedHashSet<String>();
        if(node.isArray()) for(var item:node) {String value=(item.isTextual()?item.asText():item.path("name").asText()).strip();if(!value.isBlank() && value.length()<=100) result.add(value);if(result.size()==50) break;}
        return List.copyOf(result);
    }
    public static String normalizeUsername(String input) {
        if(input==null || input.length()>200) throw bad("请输入Bangumi用户名、UID或个人主页链接");
        String value=input.strip();
        if(value.startsWith("http://") || value.startsWith("https://")) {
            try {
                URI uri=URI.create(value);
                if(uri.getUserInfo()!=null || uri.getPort()!=-1 || !Set.of("bgm.tv","bangumi.tv","chii.in").contains(uri.getHost()) || uri.getRawQuery()!=null || uri.getFragment()!=null || !uri.getPath().matches("/user/[A-Za-z0-9_-]{1,64}/?")) throw new IllegalArgumentException();
                value=uri.getPath().split("/")[2];
            } catch(IllegalArgumentException e) {throw bad("请填写Bangumi用户名、UID或官方个人主页链接");}
        }
        if(!value.matches("[A-Za-z0-9_-]{1,64}")) throw bad("用户名或UID格式不正确，不能填写昵称");
        return value;
    }
    private String safeImage(String value) {return value!=null && value.matches("https://lain\\.bgm\\.tv/[^\\s]+")?value:null;}
    public JsonNode publicAnimeRequest(String path,Map<String,Object> body) {
        if(!path.matches("/v0/subjects/[1-9][0-9]{0,9}") && !path.matches("/v0/search/subjects\\?limit=[0-9]+&offset=[0-9]+")) throw bad("不支持的动画资料路径");
        JsonNode response=read(path,null,body,true);
        if(response==null || !response.isObject()) throw unavailable();
        return response;
    }
    public JsonNode publicNetworkRequest(String path) {
        if(!path.matches("/p1/subjects/[1-9][0-9]{0,9}/(characters|staffs/persons)\\?limit=60&offset=(?:0|[1-9][0-9]{0,3})")
            && !path.matches("/p1/characters/[1-9][0-9]{0,9}/relations\\?limit=100&offset=0")) throw bad("不支持的在线关系路径");
        JsonNode response=readFrom(networkBase,path,null,null,true);
        if(response==null || !response.path("data").isArray() || !response.path("total").canConvertToInt()) throw unavailable();
        return response;
    }
    private JsonNode read(String path,String token) {return read(path,token,null,false);}
    private JsonNode read(String path,String token,Map<String,Object> body,boolean anime) {
        return readFrom(base,path,token,body,anime);
    }
    private JsonNode readFrom(String origin,String path,String token,Map<String,Object> body,boolean anime) {
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            Duration timeout=anime?publicTimeout:Duration.ofSeconds(12);
            var builder=HttpRequest.newBuilder(URI.create(origin+path)).timeout(timeout).header("Accept","application/json").header("User-Agent",userAgent);
            if(body==null) builder.GET(); else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if(token!=null) builder.header("Authorization","Bearer "+token);
            pending=http.sendAsync(builder.build(),info -> new LimitedBody());
            // Bound the entire exchange, including a body that stalls after headers.
            var response=pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);
                int status=response.statusCode();
                if(status==401) throw new ApiException(HttpStatus.UNAUTHORIZED,"BANGUMI_TOKEN_INVALID","Bangumi令牌无效或已过期，请重新验证");
                if(status==404) throw new ApiException(HttpStatus.NOT_FOUND,anime?"BANGUMI_SUBJECT_NOT_FOUND":"BANGUMI_USER_NOT_FOUND",anime?"未找到该Bangumi动画作品":"未找到该Bangumi用户，请核对用户名或UID");
                if(status==403) throw new ApiException(HttpStatus.FORBIDDEN,"BANGUMI_FORBIDDEN","Bangumi拒绝访问该资料；公开模式无法读取私有收藏");
                if(status==429) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"BANGUMI_RATE_LIMITED","Bangumi请求频率受限，请稍后重试");
                if(status!=200) throw unavailable();
                return json.readTree(new String(response.body(),StandardCharsets.UTF_8));
        } catch(ApiException e) {throw e;} catch(InterruptedException e) {Thread.currentThread().interrupt();throw unavailable();}
        catch(Exception e) {throw unavailable();}
        finally {if(pending!=null && !pending.isDone()) pending.cancel(true);}
    }
    /** Cancel before buffering an oversized upstream response. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription value){subscription=value;value.request(1);}
        public void onNext(List<ByteBuffer> buffers){
            for(var buffer:buffers){
                if(buffer.remaining()>2_000_000-bytes.size()){subscription.cancel();result.completeExceptionally(unavailable());return;}
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable cause){result.completeExceptionally(cause);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
    private static ApiException unavailable() {return new ApiException(HttpStatus.BAD_GATEWAY,"BANGUMI_UNAVAILABLE","暂时无法读取Bangumi资料，请稍后重试；已同步的片单会保留");}
    private static ApiException bad(String message) {return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message);}
}
