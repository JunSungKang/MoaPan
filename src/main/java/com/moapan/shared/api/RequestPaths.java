package com.moapan.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;

/** Use MVC PathPattern segment decoding for access checks and error negotiation alike. */
public final class RequestPaths {
    private RequestPaths() {}
    private static final String KEY=RequestPaths.class.getName()+".path";
    private record Decoded(String uri,String context,String path) {}
    public static String withinApplication(HttpServletRequest request){
        String uri=request.getRequestURI(),context=request.getContextPath();
        if(request.getAttribute(KEY) instanceof Decoded cached&&cached.uri().equals(uri)&&cached.context().equals(context))return cached.path();
        var path=RequestPath.parse(uri,context).pathWithinApplication();
        var out=new StringBuilder();
        for(var element:path.elements())out.append(element instanceof PathContainer.PathSegment segment?segment.valueToMatch():element.value());
        String decoded=out.toString();request.setAttribute(KEY,new Decoded(uri,context,decoded));return decoded;
    }
}
