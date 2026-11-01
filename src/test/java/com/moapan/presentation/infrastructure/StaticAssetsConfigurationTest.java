package com.moapan.presentation.infrastructure;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class StaticAssetsConfigurationTest {
    @Test void changedContentsWithIdenticalUrlAndLengthInvalidateTheValidator() {
        var config=new StaticAssetsConfiguration();
        assertEquals(config.etag(resource("first")),config.etag(resource("first")));
        assertNotEquals(config.etag(resource("first")),config.etag(resource("other")));
        assertTrue(config.etag(resource("first")).matches("W/\"[0-9a-f]{64}\""));
    }
    private static ByteArrayResource resource(String text){return new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)){
        @Override public URL getURL()throws java.io.IOException{return new URL("file:/synthetic/static.js");}
    };}
}
