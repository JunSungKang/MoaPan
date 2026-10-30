package com.moapan.subscription.infrastructure;
import com.moapan.subscription.application.EntitlementProvider;
import com.moapan.subscription.domain.EntitlementPolicy.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
@Configuration
public class PreviewEntitlements {
 @Bean public EntitlementProvider entitlementProvider(@Value("${moapan.subscription.mode:preview}")String mode){
  if(!"preview".equals(mode))throw new IllegalStateException("Payment integration is unavailable; subscription mode must remain preview");
  return new EntitlementProvider(){public Collection<Grant> grants(UUID account,Instant now){return List.of(new Grant(EnumSet.allOf(Benefit.class),Instant.EPOCH,null));}public String distribution(){return "FREE_PREVIEW";}};
 }
}
