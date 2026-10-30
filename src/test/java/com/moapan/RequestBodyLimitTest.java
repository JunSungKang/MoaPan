package com.moapan;

import com.moapan.access.infrastructure.RequestBodyLimitFilter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class RequestBodyLimitTest {
    private MockHttpServletRequest unknownLength(byte[] body){
        var q=new MockHttpServletRequest(){@Override public long getContentLengthLong(){return -1;}@Override public int getContentLength(){return -1;}};
        q.setMethod("POST");q.setContentType("application/json");q.setContent(body);return q;
    }
    @Test void chunkedBodyCannotBypassSizeLimit()throws Exception{
        var q=unknownLength(new byte[16385]);var r=new MockHttpServletResponse();var called=new AtomicBoolean();
        new RequestBodyLimitFilter().doFilter(q,r,(request,response)->called.set(true));
        assertEquals(413,r.getStatus());assertFalse(called.get());assertEquals("no-store",r.getHeader("Cache-Control"));
    }
    @Test void boundaryBodyIsDeliveredIntactToJsonReader()throws Exception{
        byte[] data=("{\"value\":\""+"x".repeat(16372)+"\"}").getBytes(StandardCharsets.UTF_8);assertEquals(16384,data.length);
        var r=new MockHttpServletResponse();var called=new AtomicBoolean();
        new RequestBodyLimitFilter().doFilter(unknownLength(data),r,(request,response)->{assertEquals(new String(data,StandardCharsets.UTF_8),request.getReader().readLine());called.set(true);});
        assertTrue(called.get());assertEquals(200,r.getStatus());
    }
}
