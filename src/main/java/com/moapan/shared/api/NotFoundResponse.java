package com.moapan.shared.api;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;

/** Same public representation for nonexistent and intentionally concealed resources. */
public final class NotFoundResponse {
    private NotFoundResponse() {}
    private static final byte[] PAGE=page();
    private static final byte[] JSON="{\"error\":\"not_found\"}".getBytes(StandardCharsets.UTF_8);
    private static byte[] page(){try(var in=new ClassPathResource("static/404.html").getInputStream()){return in.readAllBytes();}catch(IOException e){throw new IllegalStateException("Missing static error page",e);}}
    private static boolean html(HttpServletRequest request){return !request.getRequestURI().startsWith("/api/")&&String.valueOf(request.getHeader("Accept")).toLowerCase(Locale.ROOT).contains("text/html");}
    public static ResponseEntity<byte[]> response(HttpServletRequest request){return ResponseEntity.status(404).cacheControl(CacheControl.noStore()).header("X-Robots-Tag","noindex, follow").contentType(html(request)?new MediaType("text","html",StandardCharsets.UTF_8):MediaType.APPLICATION_JSON).body(html(request)?PAGE:JSON);}
    public static void send(HttpServletRequest request,HttpServletResponse response)throws IOException {
        var body=response(request);response.setStatus(404);body.getHeaders().forEach((key,values)->response.setHeader(key,String.join(",",values)));
        if(!"HEAD".equals(request.getMethod()))response.getOutputStream().write(body.getBody());
    }
}
