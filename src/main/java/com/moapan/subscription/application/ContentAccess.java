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
 public void require(Benefit benefit){var a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!a.isAuthenticated()||a instanceof AnonymousAuthenticationToken)throw new ApiError(401,"authentication_required");UUID id;try{id=UUID.fromString(a.getName());}catch(IllegalArgumentException e){throw new ApiError(401,"authentication_required");}if(!EntitlementPolicy.allows(provider.grants(id,clock.instant()),benefit,clock.instant()))throw new ApiError(403,"benefit_required");}
 public String distribution(){return provider.distribution();}
}
