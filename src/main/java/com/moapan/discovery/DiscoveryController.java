package com.moapan.discovery;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.*;
@RestController @ConditionalOnWebApplication
public class DiscoveryController {
    static final List<String> PUBLIC_PAGES=List.of("/index.html","/about.html","/privacy.html","/terms.html");
    private final String origin;
    public DiscoveryController(@Value("${moapan.public-base-url}") String base){origin=origin(base);}
    static String origin(String value){
        URI u=URI.create(value);
        if(!List.of("https","http").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/")))throw new IllegalStateException("invalid_public_origin");
        return u.getScheme()+"://"+u.getRawAuthority();
    }
    @GetMapping(value="/robots.txt",produces="text/plain;charset=UTF-8") public String robots(){return "User-agent: *\nDisallow: /internal/\nDisallow: /api/v1/admin/\nSitemap: "+origin+"/sitemap.xml\n";}
    @GetMapping(value="/sitemap.xml",produces="application/xml;charset=UTF-8") public String sitemap(){return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"+String.join("",PUBLIC_PAGES.stream().map(p->"<url><loc>"+origin+p+"</loc></url>").toList())+"</urlset>";}
}
