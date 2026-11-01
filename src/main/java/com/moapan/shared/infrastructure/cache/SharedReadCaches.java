package com.moapan.shared.infrastructure.cache;

import java.util.EnumSet;
import java.util.function.ToIntFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/** Each domain owns one immutable read-model cache; allocations sum to a fixed process budget. */
@Component
public final class SharedReadCaches {
    public enum Region {
        DICTIONARY(512), CLASSIFICATION(512), INTERESTS(256), ADVERTISEMENT(256),
        EDITION_IDS(2048), ARTICLE_CARDS(9984), SOURCE_CATALOG(256), PUBLICATION_CATALOG(512), PUBLICATIONS(2048);
        final int kib;
        Region(int kib) { this.kib = kib; }
    }
    private final PlatformTransactionManager transactions;
    private final int divisor;
    private final EnumSet<Region> allocated = EnumSet.noneOf(Region.class);

    public SharedReadCaches(PlatformTransactionManager transactions,
            @Value("${moapan.role:web}") String role) {
        this.transactions = transactions;
        divisor = role.equals("worker") ? 2 : 1;
        org.slf4j.LoggerFactory.getLogger(SharedReadCaches.class)
                .info("Shared read-cache logical budget: {} bytes",logicalBudgetBytes());
    }

    public synchronized <V> VersionedReadCache<V> create(Region region, ToIntFunction<V> weigh) {
        if (!allocated.add(region)) throw new IllegalStateException("Cache region already allocated: " + region);
        int budget = region.kib * 1024 / divisor;
        int entryLimit=region==Region.PUBLICATIONS?budget:Math.min(budget,512*1024/divisor);
        return new VersionedReadCache<>(transactions, budget, entryLimit, weigh);
    }

    public long logicalBudgetBytes() {
        return java.util.Arrays.stream(Region.values()).mapToLong(r -> r.kib * 1024L / divisor).sum();
    }
}
