package com.moapan.news;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
@RestController @ConditionalOnWebApplication @RequestMapping("/api/v1/admin")
public class EditorialController {
    record Alias(String alias){} record Merge(UUID canonicalId){}
    private final EditorialService service;public EditorialController(EditorialService service){this.service=service;}
    @GetMapping("/articles")Object list(){return service.list();}
    @PatchMapping("/articles/{id}")void edit(@PathVariable UUID id,@RequestBody EditorialService.Edit input,Authentication a){service.edit(id,input,UUID.fromString(a.getName()));}
    @PostMapping("/tags")Object tag(@RequestBody EditorialService.TagInput input,Authentication a){return service.tag(input,UUID.fromString(a.getName()));}
    @PatchMapping("/tags/{id}")void updateTag(@PathVariable UUID id,@RequestBody EditorialService.TagInput input,Authentication a){service.updateTag(id,input,UUID.fromString(a.getName()));}
    @PostMapping("/tags/{id}/aliases")void alias(@PathVariable UUID id,@RequestBody Alias input,Authentication a){service.alias(id,input.alias(),UUID.fromString(a.getName()));}
    @PostMapping("/tags/{id}/merge")void merge(@PathVariable UUID id,@RequestBody Merge input,Authentication a){service.merge(id,input.canonicalId(),UUID.fromString(a.getName()));}
}
