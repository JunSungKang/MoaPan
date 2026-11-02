package com.moapan.subscription.api;

import com.moapan.subscription.application.SubscriptionManagement;
import com.moapan.subscription.application.SubscriptionManagement.*;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class SubscriptionController {
    private final SubscriptionManagement service;
    public SubscriptionController(SubscriptionManagement service){this.service=service;}
    @PostMapping("/api/v1/admin/subscriptions/search") Page search(@RequestBody Search input){return service.search(input);}
    @PutMapping("/api/v1/admin/subscriptions/{id}") AccountView grant(@PathVariable UUID id,@RequestBody GrantInput input,Authentication actor){return service.grant(id,input,UUID.fromString(actor.getName()));}
    @PostMapping("/api/v1/admin/subscriptions/{id}/revoke") AccountView revoke(@PathVariable UUID id,@RequestBody Version input,Authentication actor){return service.revoke(id,input,UUID.fromString(actor.getName()));}
}
