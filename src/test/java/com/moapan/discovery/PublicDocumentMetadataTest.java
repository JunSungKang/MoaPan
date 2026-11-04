package com.moapan.discovery;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import static org.junit.jupiter.api.Assertions.*;

class PublicDocumentMetadataTest {
    private final org.springframework.web.servlet.resource.ResourceTransformerChain chain=new org.springframework.web.servlet.resource.ResourceTransformerChain(){
        @Override public org.springframework.web.servlet.resource.ResourceResolverChain getResolverChain(){throw new AssertionError("Resolver not used by metadata transformer");}
        @Override public Resource transform(jakarta.servlet.http.HttpServletRequest request,Resource resource){return resource;}
    };
    private Resource document(String filename,String html,long modified){return new ByteArrayResource(html.getBytes(StandardCharsets.UTF_8)){
        @Override public String getFilename(){return filename;}
        @Override public long lastModified(){return modified;}
    };}
    @Test void publicStaticMetadataIsAbsoluteAndDoesNotRewriteProtectedDocuments()throws Exception{
        var metadata=new PublicDocumentMetadata("https://example.test");String html="<link rel=\"canonical\" href=\"/index.html\"><main>Fixed public text</main>";
        var source=document("index.html",html,1);var result=metadata.transform(null,source,chain);
        assertEquals(html.replace("href=\"/index.html\"","href=\"https://example.test/index.html\""),new String(result.getContentAsByteArray(),StandardCharsets.UTF_8));
        assertSame(result,metadata.transform(null,source,chain));
        var protectedDocument=document("account.html","Private static shell",1);
        assertSame(protectedDocument,metadata.transform(null,protectedDocument,chain));
        var changed=document("index.html",html.replace("Fixed","Updated"),2);
        assertTrue(new String(metadata.transform(null,changed,chain).getContentAsByteArray(),StandardCharsets.UTF_8).contains("Updated public text"));
    }
    @Test void missingCanonicalFailsInsteadOfPublishingConflictingMetadata(){
        var metadata=new PublicDocumentMetadata("https://example.test");
        assertThrows(java.io.IOException.class,()->metadata.transform(null,document("about.html","Missing",1),chain));
        assertThrows(java.io.IOException.class,()->metadata.transform(null,document("about.html","<link rel=\"canonical\" href=\"/\"><link rel=\"canonical\" href=\"/\">",1),chain));
    }
}
