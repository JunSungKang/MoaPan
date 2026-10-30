package com.moapan.discovery;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component @ConditionalOnWebApplication @Order(-1000)
public class DiscoveryHeaders extends OncePerRequestFilter {
    private final String origin;
    public DiscoveryHeaders(@Value("${moapan.public-base-url}") String value){origin=DiscoveryController.origin(value);}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String path=request.getRequestURI();String canonical=path.equals("/")?"/index.html":path;
        if(DiscoveryController.PUBLIC_PAGES.contains(canonical))response.setHeader("Link","<"+origin+canonical+">; rel=\"canonical\"");
        else if(!path.startsWith("/assets/"))response.setHeader("X-Robots-Tag","noindex, follow");
        chain.doFilter(request,response);
    }
}
