package com.moapan.access.infrastructure;
import com.moapan.access.infrastructure.Limits;
import com.moapan.identity.infrastructure.Accounts;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration @EnableMethodSecurity @ConditionalOnWebApplication
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new Argon2PasswordEncoder(16,32,1,19456,2);}
    @Bean SecurityContextRepository contextRepository(){return new HttpSessionSecurityContextRepository();}
    @Bean AuthenticationManager authenticationManager(Accounts accounts,PasswordEncoder encoder){
        UserDetailsService users=name->accounts.findByUsername(name).filter(a->a.active).map(a->User.withUsername(a.username).password(a.passwordHash).roles(a.role).build()).orElseThrow(()->new UsernameNotFoundException("invalid_credentials"));
        var provider=new DaoAuthenticationProvider(users);provider.setPasswordEncoder(encoder);return new ProviderManager(provider);
    }
    @Bean SecurityFilterChain chain(HttpSecurity http,Accounts accounts,Limits limits,SecurityContextRepository repository,@Value("${moapan.public-base-url}")String origin)throws Exception{
        URI base=URI.create(origin); if(base.getHost()==null||!Set.of("http","https").contains(base.getScheme()))throw new IllegalStateException("Invalid PUBLIC_BASE_URL");
        var csrf=new HttpSessionCsrfTokenRepository(); csrf.setHeaderName("X-CSRF-TOKEN");
        var handler=new CsrfTokenRequestAttributeHandler();
        http.csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(handler))
            .securityContext(c->c.securityContextRepository(repository))
            .authorizeHttpRequests(a->a.requestMatchers("/api/v1/auth/csrf","/api/v1/auth/signup","/api/v1/auth/login","/internal/live").permitAll().requestMatchers(org.springframework.http.HttpMethod.GET,"/api/v1/public/interests","/api/v1/public/tags","/api/v1/public/news/edition","/","/index.html").permitAll().requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/public/news/search").permitAll().requestMatchers("/api/v1/admin/**","/admin.html","/newsletter-editor.html","/research-evaluation.html").hasRole("ADMIN").requestMatchers("/settings.html","/account.html","/newsletter.html").authenticated().requestMatchers("/api/**").authenticated().anyRequest().permitAll())
            .requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable())
            .logout(c->c.disable())
            .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->{if(Set.of("/","/index.html","/settings.html","/account.html","/admin.html","/newsletter.html","/newsletter-editor.html","/research-evaluation.html").contains(q.getRequestURI()))redirect(r,"/login.html");else error(r,401);}).accessDeniedHandler((q,r,x)->{if(Set.of("/settings.html","/account.html","/admin.html","/newsletter-editor.html","/research-evaluation.html").contains(q.getRequestURI()))redirect(r,"/index.html");else error(r,403);}))
            .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; font-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'"))
                .referrerPolicy(c->c.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .permissionsPolicyHeader(c->c.policy("camera=(), microphone=(), geolocation=()")))
            .addFilterBefore(new RequestBodyLimitFilter(),CsrfFilter.class)
            .addFilterBefore(new Boundary(accounts,limits,base),AnonymousAuthenticationFilter.class);
        return http.build();
    }
    // A relative Location keeps the browser on HTTPS without trusting forwarded headers.
    static void redirect(HttpServletResponse response,String path){response.setStatus(302);response.setHeader("Location",path);response.setHeader("Cache-Control","no-store");}
    static void error(HttpServletResponse response,int status)throws IOException{response.setStatus(status);if(status==503&&response.getHeader("Retry-After")==null)response.setHeader("Retry-After","60");response.setContentType("application/json");response.setHeader("Cache-Control","no-store");response.getWriter().write("{\"error\":\""+(status==401?"authentication_required":status==429?"rate_limited":status==503?"temporarily_unavailable":"forbidden")+"\"}");}
    private static boolean publicNewsRequest(HttpServletRequest q){
        return "GET".equals(q.getMethod())&&Set.of("/","/index.html","/api/v1/public/news/edition","/api/v1/public/interests","/api/v1/public/tags").contains(q.getRequestURI())||"POST".equals(q.getMethod())&&q.getRequestURI().equals("/api/v1/public/news/search");
    }
    static class Boundary extends OncePerRequestFilter {
        final Accounts accounts;final Limits limits;final URI origin;final Semaphore requests=new Semaphore(100);
        Boundary(Accounts a,Limits l,URI u){accounts=a;limits=l;origin=u;}
        protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain chain)throws ServletException,IOException{
            if(!requests.tryAcquire()){r.setHeader("Retry-After","1");error(r,503);return;}
            try{
                if(!Objects.equals(q.getServerName(),origin.getHost())){error(r,403);return;}
                String supplied=q.getHeader("Origin");
                if(supplied!=null&&!Set.of("GET","HEAD","OPTIONS").contains(q.getMethod())&&!supplied.equals(origin.toString())){error(r,403);return;}
                if("cross-site".equals(q.getHeader("Sec-Fetch-Site"))&&q.getRequestURI().startsWith("/api/")){error(r,403);return;}
                if(q.getRequestURI().startsWith("/api/")){r.setHeader("Cache-Control","no-store");limits.auth("api_global",3000,60);}
                HttpSession session=q.getSession(false);
                var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if(session!=null&&auth!=null&&auth.isAuthenticated()){
                    boolean valid=RememberedSession.valid(session);
                    try{valid&=accounts.findById(UUID.fromString(auth.getName())).filter(a->a.active&&auth.getAuthorities().stream().anyMatch(p->p.getAuthority().equals("ROLE_"+a.role))).isPresent();}catch(IllegalArgumentException e){valid=false;}
                    if(!valid){session.invalidate();org.springframework.security.core.context.SecurityContextHolder.clearContext();if(!publicNewsRequest(q)){if(q.getRequestURI().endsWith(".html"))redirect(r,"/login.html");else error(r,401);return;}}
                }
                if(q.getRequestURI().equals("/api/v1/auth/csrf")&&session==null)limits.auth("anonymous_sessions",600,60);
                chain.doFilter(q,r);
            }catch(com.moapan.shared.api.ApiError e){r.setHeader("Retry-After","60");error(r,e.statusCode());}
            catch(org.springframework.dao.DataAccessException e){error(r,503);}
            finally{requests.release();}
        }
    }
}
