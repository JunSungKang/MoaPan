package com.moapan.subscription.domain;
import java.time.Instant;
import java.util.*;
/** Product benefits are independent of operational USER/ADMIN roles. */
public final class EntitlementPolicy {
 private EntitlementPolicy(){}
 public enum Benefit { READ_NEWSLETTER, REUSE_NEWSLETTER, RESEARCH_WORKSHEET }
 public record Grant(Set<Benefit> benefits,Instant startsAt,Instant endsAt){public Grant{benefits=Set.copyOf(benefits);}}
 public static boolean allows(Collection<Grant> grants,Benefit benefit,Instant now){return grants.stream().anyMatch(g->!now.isBefore(g.startsAt())&&(g.endsAt()==null||now.isBefore(g.endsAt()))&&g.benefits().contains(benefit));}
}
