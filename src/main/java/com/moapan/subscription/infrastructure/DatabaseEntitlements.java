package com.moapan.subscription.infrastructure;

import com.moapan.subscription.application.EntitlementProvider;
import com.moapan.subscription.domain.EntitlementPolicy.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Live grants, deliberately outside session and cross-request caches. */
@Component
public final class DatabaseEntitlements implements EntitlementProvider {
    private final JdbcClient db;
    public DatabaseEntitlements(JdbcClient db,@org.springframework.beans.factory.annotation.Value("${moapan.subscription.mode:managed}") String mode) {
        if(!"managed".equals(mode))throw new IllegalStateException("Only manually managed subscription access is available; payment integration is unavailable");
        this.db=db;
    }

    public Collection<Grant> grants(UUID account, Instant now) {
        return db.sql("""
                SELECT s.starts_at,s.expires_at FROM account_subscriptions s
                JOIN users u ON u.id=s.account_id
                WHERE s.account_id=:id AND s.status='ACTIVE' AND u.active
                """).param("id",account).query((rs,n)->new Grant(EnumSet.allOf(Benefit.class),
                        rs.getObject("starts_at",OffsetDateTime.class).toInstant(),
                        rs.getObject("expires_at",OffsetDateTime.class).toInstant())).list();
    }
}
