package com.moapan.identity.infrastructure;

import com.moapan.identity.domain.TermsDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Parse the canonical packaged terms once; this adapter never fetches external pages. */
@Component @ConditionalOnWebApplication
public class TermsDocuments {
    private static final Set<String> READABLE=Set.of("p","h2","h3","strong","em","ul","ol","li","br","a");
    private final TermsDocument current;
    public TermsDocuments()throws IOException{
        try(var in=new ClassPathResource("static/terms.html").getInputStream()){
            current=parse(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    public TermsDocument current(){return current;}
    static TermsDocument parse(String html){
        Element main=Jsoup.parse(html).selectFirst("main[data-terms-version]");
        if(main==null||!main.attr("data-terms-version").matches("terms-[1-9][0-9]*"))throw new IllegalStateException("Invalid canonical terms");
        var nodes=main.childNodes().stream().map(TermsDocuments::copy).filter(Objects::nonNull).toList();
        if(nodes.isEmpty())throw new IllegalStateException("Missing canonical terms");
        return new TermsDocument(main.attr("data-terms-version"),nodes);
    }
    private static TermsDocument.Part copy(Node node){
        if(node instanceof TextNode text)return new TermsDocument.Part("text",text.getWholeText(),List.of());
        if(!(node instanceof Element element)||element.hasAttr("data-policy-navigation")||!READABLE.contains(element.normalName()))return null;
        String kind=element.normalName().equals("a")?"span":element.normalName();
        return new TermsDocument.Part(kind,"",element.childNodes().stream().map(TermsDocuments::copy).filter(Objects::nonNull).toList());
    }
}
