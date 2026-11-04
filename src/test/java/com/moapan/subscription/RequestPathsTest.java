package com.moapan.subscription;

import com.moapan.shared.api.RequestPaths;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class RequestPathsTest {
    @Test void decodingMatchesMvcSegmentsAndRequestCacheCannotSurviveAForwardedPath(){
        var q=new MockHttpServletRequest();q.setContextPath("");
        for(String path:new String[]{"/newsletter.html","/%6eewsletter.html"}){q.setRequestURI(path);assertEquals("/newsletter.html",RequestPaths.withinApplication(q));assertEquals(path,q.getRequestURI());}
        q.setRequestURI("/%61pi/v1/%6eewsletters");assertEquals("/api/v1/newsletters",RequestPaths.withinApplication(q));
        assertSame(RequestPaths.withinApplication(q),RequestPaths.withinApplication(q));
        q.setRequestURI("/index.html");assertEquals("/index.html",RequestPaths.withinApplication(q));
        q.setContextPath("/app");q.setRequestURI("/app/%6eewsletter.html");assertEquals("/newsletter.html",RequestPaths.withinApplication(q));
    }
}
