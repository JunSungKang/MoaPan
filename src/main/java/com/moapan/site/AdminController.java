package com.moapan.site;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
@RestController @ConditionalOnWebApplication @RequestMapping("/api/v1/admin")
public class AdminController {
    private final CatalogService service;
    public AdminController(CatalogService service){this.service=service;}
    static UUID actor(Authentication a){return UUID.fromString(a.getName());}
    @GetMapping("/sites")Object sites(@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String result,@RequestParam(defaultValue="name")String sort,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.sites(search,status,result,sort,page,size);}
    @PostMapping("/sites")Object create(@Valid @RequestBody CatalogService.SiteInput input,Authentication a){return service.createSite(input,actor(a));}
    @GetMapping("/sites/{id}")Object site(@PathVariable UUID id){return service.site(id);}
    @PatchMapping("/sites/{id}")Object update(@PathVariable UUID id,@Valid @RequestBody CatalogService.SiteInput input,Authentication a){return service.updateSite(id,input,actor(a));}
    @PostMapping("/sites/{id}/activate")Object activate(@PathVariable UUID id,Authentication a){return service.state(id,"ACTIVE",actor(a));}
    @PostMapping("/sites/{id}/pause")Object pause(@PathVariable UUID id,Authentication a){return service.state(id,"PAUSED",actor(a));}
    @DeleteMapping("/sites/{id}")Object archive(@PathVariable UUID id,Authentication a){return service.state(id,"ARCHIVED",actor(a));}
    @PostMapping("/sites/{id}/restore")Object restore(@PathVariable UUID id,Authentication a){return service.state(id,"PAUSED",actor(a));}
    @GetMapping("/sites/{id}/sources")Object sources(@PathVariable UUID id){return service.sources(id);}
    @PostMapping("/sites/{id}/sources")Object add(@PathVariable UUID id,@Valid @RequestBody CatalogService.SourceInput input,Authentication a){return service.createSource(id,input,actor(a));}
    @GetMapping("/sources/{id}")Object source(@PathVariable UUID id){return service.source(id);}
    @PatchMapping("/sources/{id}")Object updateSource(@PathVariable UUID id,@Valid @RequestBody CatalogService.SourceInput input,Authentication a){return service.updateSource(id,input,actor(a));}
    @DeleteMapping("/sources/{id}")Object archiveSource(@PathVariable UUID id,Authentication a){return service.archiveSource(id,true,actor(a));}
    @PostMapping("/sources/{id}/restore")Object restoreSource(@PathVariable UUID id,Authentication a){return service.archiveSource(id,false,actor(a));}
    @PostMapping("/sources/{id}/validate")Object validate(@PathVariable UUID id,Authentication a){return service.preview(id,true,actor(a));}
    @PostMapping("/sources/{id}/preview")Object preview(@PathVariable UUID id,Authentication a){return service.preview(id,false,actor(a));}
    @PostMapping("/sources/{id}/collect")ResponseEntity<?> collect(@PathVariable UUID id,Authentication a){return ResponseEntity.accepted().body(service.collect(id,actor(a)));}
    @GetMapping("/sources/{id}/runs")Object runs(@PathVariable UUID id){return service.runs(id);}
    @GetMapping("/jobs/{id}")Object job(@PathVariable UUID id){return service.job(id);}
    @GetMapping("/workers")Object workers(){return service.heartbeat();}
}
