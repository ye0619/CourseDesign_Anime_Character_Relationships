package com.animegraph.viewer;

import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class ViewerIdentity {
    private static final String COOKIE="animegraph_viewer";
    private final boolean secure;
    private final SecureRandom random=new SecureRandom();
    public ViewerIdentity(@Value("${server.servlet.session.cookie.secure:false}")boolean secure) {this.secure=secure;}
    public String read(HttpServletRequest request) {
        if(request.getCookies()==null) return null;
        return Arrays.stream(request.getCookies()).filter(c -> c.getName().equals(COOKIE) && c.getValue().matches("[A-Za-z0-9_-]{43}"))
                .findFirst().map(c -> hash(c.getValue())).orElse(null);
    }
    public String create(HttpServletResponse response) {
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String value=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        response.addHeader("Set-Cookie",cookie(value,60L*60*24*60));
        return hash(value);
    }
    public void clear(HttpServletResponse response) {response.addHeader("Set-Cookie",cookie("",0));}
    private String cookie(String value,long age) {return ResponseCookie.from(COOKIE,value).httpOnly(true).secure(secure).sameSite("Lax").path("/api/v1/viewer").maxAge(age).build().toString();}
    private String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
