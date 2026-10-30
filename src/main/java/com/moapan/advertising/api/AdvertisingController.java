package com.moapan.advertising.api;

import com.moapan.advertising.application.AdvertisingSettings;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @ConditionalOnWebApplication
public class AdvertisingController {
    private final AdvertisingSettings settings;
    public AdvertisingController(AdvertisingSettings settings){this.settings=settings;}
    @GetMapping("/api/v1/advertisements") public AdvertisingSettings.Display display(){return settings.display();}
    @GetMapping("/api/v1/admin/advertisements") public AdvertisingSettings.Settings read(){return settings.read();}
    @PutMapping("/api/v1/admin/advertisements") public AdvertisingSettings.Settings update(@Valid @RequestBody AdvertisingSettings.Settings input,Authentication a){return settings.update(input,UUID.fromString(a.getName()));}
}
