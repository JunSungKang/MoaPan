package com.moapan.identity.infrastructure;

import com.moapan.identity.domain.TermsDocument;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TermsDocumentsTest {
    private static String text(List<TermsDocument.Part> nodes){return nodes.stream().map(n->n.text()+text(n.children())).collect(java.util.stream.Collectors.joining());}
    @Test void packagedTermsUseCanonicalEditionAndKeepEveryReadableParagraph()throws Exception{
        var document=new TermsDocuments().current();assertEquals("terms-1",document.version());
        String content=text(document.nodes());assertTrue(content.contains("약관 개정 제 1판"));assertTrue(content.contains("현재 서비스에서는 결제나 자동 청구를 하지 않습니다"));assertFalse(content.contains("회원가입으로 돌아가기"));
        assertThrows(UnsupportedOperationException.class,()->document.nodes().clear());
    }
    @Test void parserRemovesScriptsNavigationAndAllAttributesWithoutChangingReadableText(){
        var document=TermsDocuments.parse("<main data-terms-version='terms-7'><h1>Excluded heading</h1><p onclick='attack()'>Readable &amp; safe <a href='javascript:attack()'>link label</a></p><script>attack()</script><p data-policy-navigation>Excluded navigation</p></main>");
        assertEquals("Readable & safe link label",text(document.nodes()));assertEquals("span",document.nodes().getFirst().children().get(1).kind());
        assertThrows(IllegalStateException.class,()->TermsDocuments.parse("<main data-terms-version='invalid'><p>Text</p></main>"));
    }
}
