package com.moapan.newsletter;

import com.moapan.newsletter.application.ManuscriptValidation;
import com.moapan.newsletter.domain.Manuscript;
import com.moapan.newsletter.domain.Manuscript.Block;
import com.moapan.shared.api.ApiError;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class ManuscriptValidationTest {
    private final ManuscriptValidation validation=new ManuscriptValidation();
    private final JsonMapper json=JsonMapper.builder().build();
    private Manuscript manuscript(Block block,String language){return new Manuscript("Synthetic","Introduction","Research",List.of(block),language);}
    @Test void legacyLanguageRemainsUnknownWhileExplicitLanguageIsPreserved(){
        var block=new Block("paragraph","Synthetic text","","");
        assertEquals("und",json.readTree(validation.encode(manuscript(block,null))).path("language").asString());
        assertEquals("ko",json.readTree(validation.encode(manuscript(block,"ko"))).path("language").asString());
        assertThrows(ApiError.class,()->validation.encode(manuscript(block,"invalid-language")));
    }
    @Test void closedStrategiesRejectUnknownBlocksAndUnsafeReferences(){
        for(var block:List.of(new Block("script","Synthetic","",""),new Block("reference","Link","javascript:alert(1)",""),new Block("reference","Link","https://user:pass@example.org/",""),new Block("paragraph","Text","https://example.org/","")))assertThrows(ApiError.class,()->validation.encode(manuscript(block,"en")));
        assertDoesNotThrow(()->validation.encode(manuscript(new Block("reference","Link","https://example.org/paper",""),"en")));
    }
    @Test void imageDimensionsComeFromImageBytesRatherThanClientClaims()throws Exception{
        var image=new java.awt.image.BufferedImage(40,30,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        String url="data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());
        var block=json.readTree(validation.encode(manuscript(new Block("image","Synthetic image",url,"Owned fixture",1,1),"en"))).path("blocks").get(0);
        assertEquals(40,block.path("width").asInt());assertEquals(30,block.path("height").asInt());
        assertThrows(ApiError.class,()->validation.encode(manuscript(new Block("image","","data:image/png;base64,AAAA",""),"en")));
    }
}
