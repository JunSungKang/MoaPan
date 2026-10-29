package com.moapan.access.infrastructure;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.session.web.http.*;
/** Opt-in persistent Spring Session; the browser never stores account credentials. */
@Configuration @ConditionalOnWebApplication
public class RememberedSession {
    public static final String UNTIL="MOAPAN_REMEMBER_UNTIL";
    public static final int DAYS_30=30*24*60*60;
    public static void configure(HttpSession session,boolean remember){
        session.setMaxInactiveInterval(remember?DAYS_30:1800);
        if(remember)session.setAttribute(UNTIL,System.currentTimeMillis()+DAYS_30*1000L);else session.removeAttribute(UNTIL);
    }
    public static boolean valid(HttpSession session){
        Object until=session.getAttribute(UNTIL);
        return until instanceof Long value?System.currentTimeMillis()<value:System.currentTimeMillis()-session.getCreationTime()<43_200_000L;
    }
    @Bean CookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.secure:true}")boolean secure){
        var serializer=new DefaultCookieSerializer(){
            @Override public void writeCookieValue(CookieValue value){
                if(value.getCookieMaxAge()<0){var session=value.getRequest().getSession(false);Object until=session==null?null:session.getAttribute(UNTIL);
                    if(until instanceof Long end)value.setCookieMaxAge((int)Math.max(0,Math.min(DAYS_30,(end-System.currentTimeMillis())/1000)));
                }
                super.writeCookieValue(value);
            }
        };
        serializer.setCookieName("SESSION");serializer.setCookiePath("/");serializer.setUseHttpOnlyCookie(true);serializer.setUseSecureCookie(secure);serializer.setSameSite("Strict");return serializer;
    }
}
