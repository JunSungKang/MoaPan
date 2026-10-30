package com.moapan.newsletter;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
@RestController @ConditionalOnWebApplication
public class NewsletterController{
 private final NewsletterService service;public NewsletterController(NewsletterService s){service=s;}
 @GetMapping("/api/v1/newsletters") Object list(){return service.list();}
 @GetMapping("/api/v1/newsletters/{id}") Object detail(@PathVariable UUID id){return service.detail(id);}
 @GetMapping("/api/v1/admin/newsletters") Object drafts(){return service.drafts();}
 @GetMapping("/api/v1/admin/newsletters/{id}") Object edit(@PathVariable UUID id){return service.edit(id);}
 @PutMapping("/api/v1/admin/newsletters/{id}") Object save(@PathVariable UUID id,@RequestBody NewsletterService.Input input,Authentication a){return service.save(id,input,UUID.fromString(a.getName()));}
 public record Deletion(long version){}
 @DeleteMapping("/api/v1/admin/newsletters/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 void delete(@PathVariable UUID id,@RequestBody Deletion input,Authentication a){service.delete(id,input.version(),UUID.fromString(a.getName()));}
 public record Publication(long version,boolean visible){}
 @PostMapping("/api/v1/admin/newsletters/{id}/publication") void publish(@PathVariable UUID id,@RequestBody Publication input,Authentication a){service.publish(id,input.version(),input.visible(),UUID.fromString(a.getName()));}
}
