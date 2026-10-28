package com.moapan.auth;
import com.moapan.*;
import com.moapan.security.Limits;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@RestController @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
public class AuthController {
    record Credentials(@NotNull @Size(min=1,max=128) String username,@NotNull @Size(min=6,max=256)String password){}
    public static final String TERMS_VERSION="2026-10-28.2";
    record Signup(@NotNull @Size(min=1,max=128) String username,@NotNull @Size(min=6,max=256) String password,@NotNull @AssertTrue Boolean termsAccepted,@NotBlank @Size(max=32) String termsVersion){}
    record Change(@NotNull String currentPassword,@NotNull @Size(min=6,max=256)String newPassword){}
    record Confirm(@NotNull String currentPassword){}
    record View(UUID id,String username,String role){}
    private final Accounts users;private final PasswordEncoder encoder;private final AuthenticationManager manager;private final SecurityContextRepository contexts;private final Limits limits;private final JdbcClient db;private final TransactionTemplate tx;private final Semaphore hashes=new Semaphore(4);
    public AuthController(Accounts users,PasswordEncoder encoder,AuthenticationManager manager,SecurityContextRepository contexts,Limits limits,JdbcClient db,PlatformTransactionManager tm){this.users=users;this.encoder=encoder;this.manager=manager;this.contexts=contexts;this.limits=limits;this.db=db;this.tx=new TransactionTemplate(tm);}
    @GetMapping("/api/v1/auth/csrf") Object csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
    @PostMapping("/api/v1/auth/signup") View signup(@Valid @RequestBody Signup input){
        if(!TERMS_VERSION.equals(input.termsVersion()))throw new ApiError(400,"terms_version_outdated");
        passwordLength(input.password());limits.auth("signup",100,3600);String username=normalize(input.username());
        return hash(()->{if(users.findByUsername(username).isPresent())throw new ApiError(409,"username_unavailable");Account user=new Account(username,encoder.encode(input.password()));user.termsVersion=TERMS_VERSION;user.termsAcceptedAt=java.time.Instant.now();return view(users.save(user));});
    }
    @PostMapping("/api/v1/auth/login") View login(@Valid @RequestBody Credentials input,HttpServletRequest request,HttpServletResponse response){
        passwordLength(input.password());limits.auth("login_global",300,900);String username=normalize(input.username());String bucket="login:"+limits.fingerprint(username);limits.checkAuth(bucket,10);
        return hash(()->{
            try{manager.authenticate(new UsernamePasswordAuthenticationToken(username,input.password()));}catch(AuthenticationException e){int failures=limits.auth(bucket,10,900);limits.checkAuth(bucket,10);throw new ApiError(401,"invalid_credentials",null,failures>=5?10-failures:null);}
            Account user=users.findByUsername(username).filter(a->a.active).orElseThrow(()->new ApiError(401,"invalid_credentials"));
            if(request.getSession(false)!=null)request.changeSessionId();else request.getSession(true);
            var auth=UsernamePasswordAuthenticationToken.authenticated(user.id.toString(),null,List.of(new SimpleGrantedAuthority("ROLE_"+user.role)));
            var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(auth);SecurityContextHolder.setContext(context);contexts.saveContext(context,request,response);
            new HttpSessionCsrfTokenRepository().saveToken(null,request,response);
            return view(user);
        });
    }
    @PostMapping("/api/v1/auth/logout") void logout(HttpServletRequest request){if(request.getSession(false)!=null)request.getSession(false).invalidate();SecurityContextHolder.clearContext();}
    @GetMapping("/api/v1/account") View account(Authentication auth){return view(current(auth));}
    @PatchMapping("/api/v1/account/password") void password(Authentication auth,@Valid @RequestBody Change change,HttpServletRequest q){
        passwordLength(change.newPassword());Account user=current(auth);limits.auth("password:"+user.id,10,900);
        hash(()->{if(!encoder.matches(change.currentPassword(),user.passwordHash))throw new ApiError(403,"invalid_credentials");tx.executeWithoutResult(s->{user.passwordHash=encoder.encode(change.newPassword());users.saveAndFlush(user);db.sql("DELETE FROM spring_session WHERE principal_name=:p").param("p",user.id.toString()).update();});return null;});
        if(q.getSession(false)!=null)q.getSession(false).invalidate();SecurityContextHolder.clearContext();
    }
    @DeleteMapping("/api/v1/account") void delete(Authentication auth,@Valid @RequestBody Confirm confirm,HttpServletRequest q){
        Account user=current(auth);limits.auth("password:"+user.id,10,900);
        hash(()->{if(!encoder.matches(confirm.currentPassword(),user.passwordHash))throw new ApiError(403,"invalid_credentials");tx.executeWithoutResult(s->{db.sql("DELETE FROM spring_session WHERE principal_name=:p").param("p",user.id.toString()).update();db.sql("DELETE FROM auth_rate_limits WHERE bucket=:b").param("b","password:"+user.id).update();db.sql("DELETE FROM auth_rate_limits WHERE bucket=:b").param("b","login:"+limits.fingerprint(user.username)).update();users.delete(user);});return null;});
        if(q.getSession(false)!=null)q.getSession(false).invalidate();SecurityContextHolder.clearContext();
    }
    private static void passwordLength(String password){int n=password.codePointCount(0,password.length());if(n<6||n>128)throw new ApiError(400,"invalid_password_length");}
    private Account current(Authentication auth){return users.findById(UUID.fromString(auth.getName())).filter(a->a.active).orElseThrow(()->new ApiError(401,"authentication_required"));}
    private <T>T hash(java.util.function.Supplier<T> work){if(!hashes.tryAcquire())throw new ApiError(503,"hash_capacity");try{return work.get();}finally{hashes.release();}}
    private static View view(Account a){return new View(a.id,a.username,a.role);}
    private static String normalize(String value){if(!value.matches("[A-Za-z0-9_]{1,128}"))throw new ApiError(400,"invalid_username");String n=value.toLowerCase(Locale.ROOT);if(!n.matches("[a-z0-9_]{1,128}"))throw new ApiError(400,"invalid_username");return n;}
}
