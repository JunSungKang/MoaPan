package com.moapan.access.infrastructure;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds bytes before CSRF/JSON parsing, including requests without Content-Length. */
public final class RequestBodyLimitFilter extends OncePerRequestFilter {
    private static final int MAX_BYTES=16384;
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        if(request.getContentLengthLong()>MAX_BYTES){reject(response);return;}
        byte[] body=request.getInputStream().readNBytes(MAX_BYTES+1);
        if(body.length>MAX_BYTES){reject(response);return;}
        chain.doFilter(new HttpServletRequestWrapper(request){
            @Override public ServletInputStream getInputStream(){
                var bytes=new ByteArrayInputStream(body);
                return new ServletInputStream(){
                    public int read(){return bytes.read();}
                    public int read(byte[] b,int off,int len){return bytes.read(b,off,len);}
                    public boolean isFinished(){return bytes.available()==0;}
                    public boolean isReady(){return true;}
                    public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous request body");}
                };
            }
            @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
            @Override public int getContentLength(){return body.length;}
            @Override public long getContentLengthLong(){return body.length;}
        },response);
    }
    private static void reject(HttpServletResponse response)throws IOException{
        response.setStatus(413);response.setHeader("Cache-Control","no-store");response.setContentType("application/json");response.getWriter().write("{\"error\":\"request_too_large\"}");
    }
}
