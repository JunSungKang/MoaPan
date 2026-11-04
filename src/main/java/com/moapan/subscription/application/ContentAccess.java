package com.moapan.subscription.application;
import com.moapan.subscription.domain.EntitlementPolicy;
import com.moapan.subscription.domain.EntitlementPolicy.Benefit;
import com.moapan.shared.api.ApiError;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
@Service
public class ContentAccess {
 private final EntitlementProvider provider;private final Clock clock;
 public ContentAccess(EntitlementProvider p,Clock c){provider=p;clock=c;}
 public boolean allows(UUID account,Benefit benefit){var now=clock.instant();return EntitlementPolicy.allows(provider.grants(account,now),benefit,now);}
 public boolean allows(Benefit benefit){var a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!a.isAuthenticated()||a instanceof AnonymousAuthenticationToken)return false;UUID id;try{id=UUID.fromString(a.getName());}catch(IllegalArgumentException e){return false;}return allows(id,benefit);}
 public void require(Benefit benefit){if(!allows(benefit))throw new ApiError(404,"not_found");}
}
