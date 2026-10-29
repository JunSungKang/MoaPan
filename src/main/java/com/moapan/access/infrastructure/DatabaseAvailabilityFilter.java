package com.moapan.access.infrastructure;
import com.moapan.access.infrastructure.SecurityConfig;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration @ConditionalOnWebApplication
public class DatabaseAvailabilityFilter {
    @Bean FilterRegistrationBean<OncePerRequestFilter> databaseFailureBoundary(){
        var filter=new OncePerRequestFilter(){
            @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
                try{chain.doFilter(request,response);}catch(Exception failure){
                    boolean database=false;
                    for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof DataAccessException||cause instanceof java.sql.SQLException){database=true;break;}
                    if(database&&!response.isCommitted()){response.reset();response.setHeader("Retry-After","60");SecurityConfig.error(response,503);return;}
                    if(failure instanceof ServletException e)throw e;if(failure instanceof IOException e)throw e;if(failure instanceof RuntimeException e)throw e;throw new ServletException(failure);
                }
            }
        };
        var registration=new FilterRegistrationBean<OncePerRequestFilter>(filter);registration.setOrder(Ordered.HIGHEST_PRECEDENCE);return registration;
    }
}
