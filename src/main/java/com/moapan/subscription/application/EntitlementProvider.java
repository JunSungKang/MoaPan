package com.moapan.subscription.application;
import com.moapan.subscription.domain.EntitlementPolicy.Grant;
import java.time.Instant;
import java.util.*;
public interface EntitlementProvider {
 Collection<Grant> grants(UUID account,Instant now);
 String distribution();
}
