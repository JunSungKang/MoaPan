package com.moapan.collection.infrastructure;
import com.moapan.collection.infrastructure.ArxivGate;
import com.moapan.collection.application.CollectionFilter;
import com.moapan.shared.api.ApiError;
import java.net.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.apache.hc.client5.http.*;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.config.*;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.util.Timeout;
import org.jdom2.input.SAXBuilder;
import com.rometools.rome.io.SyndFeedInput;
import org.jsoup.Jsoup;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SafeFetch {
    @org.springframework.beans.factory.annotation.Autowired private ArxivGate arxivGate;
    public record Item(String guid,String title,String url,String description,Instant publishedAt,String author) implements com.moapan.article.domain.ArticleInput {public Item(String guid,String title,String url,String description,Instant publishedAt){this(guid,title,url,description,publishedAt,"");}}
    public record Result(List<Item>items,String etag,String modified,boolean unchanged){}
    public static String canonical(String input){
        try{
            URI uri=URI.create(input).normalize();String host=uri.getHost();
            if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||uri.getUserInfo()!=null||uri.getPort()!=-1&&uri.getPort()!=443||input.length()>2048||!host.matches("(?i)[a-z0-9][a-z0-9.-]*\\.[a-z]{2,63}"))throw new IllegalArgumentException();
            String query=uri.getRawQuery();if(query!=null){query=Arrays.stream(query.split("&")).filter(p->!p.matches("(?i)(utm_[^=]*|fbclid|gclid|mc_cid|mc_eid)=.*")).collect(java.util.stream.Collectors.joining("&"));if(query.isEmpty())query=null;}
            return "https://"+host.toLowerCase(Locale.ROOT)+(uri.getRawPath()==null||uri.getRawPath().isEmpty()?"/":uri.getRawPath())+(query==null?"":"?"+query);
        }catch(Exception e){throw new ApiError(400,"invalid_https_url");}
    }
    public static boolean publicAddress(InetAddress address){
        byte[] b=address.getAddress();int a=b[0]&255,c=b[1]&255,d=b[2]&255;
        if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())return false;
        if(b.length==4)return !(a==0||a==10||a==127||a>=224||a==100&&c>=64&&c<=127||a==169&&c==254||a==172&&c>=16&&c<=31||a==192&&(c==168||c==0&&(d==0||d==2)||c==88&&d==99)||a==198&&(c==18||c==19||c==51&&d==100)||a==203&&c==0&&d==113);
        // Only globally routed IPv6 unicast; reject ULA, mapped, documentation and transition ranges.
        return (a&224)==32&&!(a==32&&c==1&&(b[2]&255)==13&&(b[3]&255)==184)&&!(a==32&&c==2)&&!(a==32&&c==1&&b[2]==0);
    }
    public static DnsResolver pinnedResolver(String host,InetAddress[] addresses){
        InetAddress[] pinned=addresses.clone();
        if(pinned.length==0||Arrays.stream(pinned).anyMatch(a->!publicAddress(a)))throw new ApiError(400,"unsafe_destination");
        return new DnsResolver(){public InetAddress[] resolve(String requested)throws UnknownHostException{if(!host.equalsIgnoreCase(requested))throw new UnknownHostException();return pinned.clone();}public String resolveCanonicalHostname(String requested)throws UnknownHostException{if(!host.equalsIgnoreCase(requested))throw new UnknownHostException();return host;}};
    }
    public static URI redirect(URI current,String location,String allowedHost){
        URI next=URI.create(canonical(current.resolve(location).toString()));
        if(!next.getHost().equals(allowedHost))throw new ApiError(400,"redirect_host_rejected");return next;
    }
    public Result read(Map<String,Object> source){
        URI endpoint=URI.create(canonical((String)source.get("endpoint_url")));String host=endpoint.getHost();
        try(var gate=host.equals("export.arxiv.org")||host.equals("arxiv.org")?arxivGate.enter():null){
            DnsResolver resolver=pinnedResolver(host,InetAddress.getAllByName(host));
            try(var connection=PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(resolver).setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(5)).setSocketTimeout(Timeout.ofSeconds(10)).build()).build();
                var client=HttpClients.custom().setConnectionManager(connection).disableRedirectHandling().disableAutomaticRetries().disableContentCompression().setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).setConnectionRequestTimeout(Timeout.ofSeconds(3)).build()).build()){
                URI current=endpoint;
                for(int redirects=0;redirects<=3;redirects++){
                    HttpGet get=new HttpGet(current);get.setHeader("Accept-Encoding","identity");get.setHeader("User-Agent","MOAPAN/1.0 (+news-feed-reader)");get.setHeader("Accept","application/rss+xml,application/atom+xml,application/json,application/xml,text/xml");
                    if(source.get("etag")!=null)get.setHeader("If-None-Match",source.get("etag").toString());if(source.get("last_modified")!=null)get.setHeader("If-Modified-Since",source.get("last_modified").toString());
                    String ref=(String)source.get("secret_ref");if(ref!=null){String secret=System.getenv(ref);if(secret==null||secret.contains("\r")||secret.contains("\n"))throw new ApiError(503,"source_secret_missing");get.setHeader("Authorization","Bearer "+secret);}
                    var response=client.execute(get,r->{
                        int status=r.getCode();String location=r.getFirstHeader("Location")==null?null:r.getFirstHeader("Location").getValue();String retry=r.getFirstHeader("Retry-After")==null?null:r.getFirstHeader("Retry-After").getValue();
                        if(status==429||status>=500)throw new FetchFailure(status,retry);
                        if(status==304)return new Raw(status,null,null,null,null,location);
                        if(status>=300&&status<400)return new Raw(status,null,null,null,null,location);
                        if(status!=200)throw new ApiError(502,"source_http_rejected");
                        if(r.getFirstHeader("Content-Encoding")!=null&&!r.getFirstHeader("Content-Encoding").getValue().equalsIgnoreCase("identity"))throw new ApiError(502,"compressed_response_rejected");
                        if(r.getEntity()==null||r.getEntity().getContentLength()>2_097_152)throw new ApiError(502,"source_too_large");
                        byte[] bytes;try(InputStream in=r.getEntity().getContent()){bytes=in.readNBytes(2_097_153);}if(bytes.length>2_097_152)throw new ApiError(502,"source_too_large");
                        String content=r.getFirstHeader("Content-Type")==null?"":r.getFirstHeader("Content-Type").getValue();
                        return new Raw(status,bytes,content,header(r,"ETag",500),header(r,"Last-Modified",100),location);
                    });
                    if(response.status==304)return new Result(List.of(),null,null,true);
                    if(response.location!=null&&response.status>=300&&response.status<400){current=redirect(current,response.location,host);continue;}
                    List<Item> items=parse(response.bytes,source.get("type").toString(),source.get("json_mapping").toString());
                    return new Result(items.stream().filter(item->CollectionFilter.accepts(source,item)).toList(),response.etag,response.modified,false);
                }
                throw new ApiError(502,"redirect_limit");
            }
        }catch(ApiError|FetchFailure e){throw e;}catch(java.io.IOException e){throw new FetchFailure(0,null);}catch(Exception e){throw new ApiError(502,"source_fetch_or_parse_failed");}
    }
    private record Raw(int status,byte[]bytes,String contentType,String etag,String modified,String location){}
    private static String header(org.apache.hc.core5.http.ClassicHttpResponse r,String name,int max){var h=r.getFirstHeader(name);if(h==null)return null;String v=h.getValue();return v.length()<=max&&!v.contains("\r")&&!v.contains("\n")?v:null;}
    public static class FetchFailure extends RuntimeException {public final int status;public final String retry;public FetchFailure(int status,String retry){super("retryable_source_failure");this.status=status;this.retry=retry;}}
    public static List<Item> parse(byte[] bytes,String type,String mapping)throws Exception{
        if(bytes.length>2_097_152)throw new ApiError(502,"source_too_large");List<Item> result=new ArrayList<>();
        if(type.equals("JSON_API")){
            JsonMapper mapper=JsonMapper.builder().build();JsonNode config=mapper.readTree(mapping);JsonNode root=mapper.readTree(bytes);JsonNode items=path(root,config.path("items").asString("items"));if(!items.isArray())throw new ApiError(502,"invalid_json_items");
            for(JsonNode item:items){if(result.size()>=100)break;try{String url=canonical(path(item,config.path("url").asString("url")).asString());String title=clean(path(item,config.path("title").asString("title")).asString(),500);if(title.isBlank())continue;result.add(new Item(trim(path(item,config.path("id").asString("id")).asString(url),2048),title,url,clean(path(item,config.path("description").asString("description")).asString(""),8000),instant(path(item,config.path("publishedAt").asString("publishedAt")).asString("")),clean(path(item,config.path("author").asString("author")).asString(""),2000)));}catch(IllegalArgumentException|ApiError ignored){}}
        }else{
            SAXBuilder builder=new SAXBuilder();builder.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);builder.setFeature("http://xml.org/sax/features/external-general-entities",false);builder.setFeature("http://xml.org/sax/features/external-parameter-entities",false);builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);builder.setExpandEntities(false);
            var feed=new SyndFeedInput().build(builder.build(new ByteArrayInputStream(bytes)));
            for(var entry:feed.getEntries()){
                if(result.size()>=100)break;
                try{String link=entry.getLink();if(link!=null&&link.matches("http://arxiv\\.org/abs/[A-Za-z0-9./-]+"))link="https"+link.substring(4);String url=canonical(link);String title=clean(entry.getTitle(),500);if(title.isBlank())continue;String desc=entry.getDescription()==null?"":entry.getDescription().getValue();result.add(new Item(trim(entry.getUri()==null||entry.getUri().isBlank()?url:entry.getUri(),2048),title,url,clean(desc,8000),entry.getPublishedDate()==null?null:future(entry.getPublishedDate().toInstant()),clean(entry.getAuthors().isEmpty()?entry.getAuthor():entry.getAuthors().stream().map(person->person.getName()).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining(", ")),2000)));}catch(ApiError ignored){}
            }
        }return result;
    }
    private static JsonNode path(JsonNode node,String path){if(path.equals("$"))return node;for(String key:path.split("\\."))node=node.path(key);return node;}
    public static String clean(String s,int max){return trim(Jsoup.parse(s==null?"":s).text(),max);}
    static String trim(String s,int max){return s.length()>max?s.substring(0,max):s;}
    static Instant instant(String s){try{return future(Instant.parse(s));}catch(Exception e){return null;}}
    static Instant future(Instant t){return t.isAfter(Instant.now().plusSeconds(300))?null:t;}
}
