package com.moapan.news;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import jakarta.servlet.http.HttpServletRequest;
import com.moapan.ApiError;
@RestController @ConditionalOnWebApplication
public class NewsController {
    private final RepresentativeInterests interests;private final NewsService service;private final org.springframework.jdbc.core.simple.JdbcClient db;
    public NewsController(NewsService service,org.springframework.jdbc.core.simple.JdbcClient db,RepresentativeInterests interests){this.service=service;this.db=db;this.interests=interests;}
    @GetMapping("/api/v1/public/news/edition")Object publicEdition(HttpServletRequest request){checkParams(request,Set.of());return service.publicEdition();}
    @GetMapping("/api/v1/public/interests")Object defaults(){return interests.read();}
    @GetMapping("/api/v1/public/tags")Object publicTags(){return service.tags();}
    @GetMapping("/api/v1/admin/interests")Object adminInterests(){return interests.read();}
    @PutMapping("/api/v1/admin/interests")Object updateInterests(@RequestBody RepresentativeInterests.Settings input,Authentication a){return interests.update(input,UUID.fromString(a.getName()));}
    @GetMapping("/api/v1/news/edition")Object edition(Authentication a,@RequestParam(defaultValue="30")int size,HttpServletRequest request){checkParams(request,Set.of("size"));return service.first(UUID.fromString(a.getName()),size);}
    @GetMapping("/api/v1/news/page")Object page(Authentication a,@RequestParam String cursor,HttpServletRequest request){checkParams(request,Set.of("cursor"));return service.next(UUID.fromString(a.getName()),cursor);}
    @GetMapping("/api/v1/tags")Object tags(){return service.tags();}
    @GetMapping("/api/v1/sources")Object sources(){return service.sources();}
    @GetMapping("/internal/live")Object live(){return Map.of("status","UP");}
    @GetMapping("/internal/ready")Object ready(){db.sql("SELECT 1").query(Integer.class).single();return Map.of("status","UP");}
    private static void checkParams(HttpServletRequest r,Set<String> allowed){if(!allowed.containsAll(r.getParameterMap().keySet())||r.getParameterMap().values().stream().anyMatch(v->v.length!=1))throw new ApiError(400,"invalid_parameters");}
}
