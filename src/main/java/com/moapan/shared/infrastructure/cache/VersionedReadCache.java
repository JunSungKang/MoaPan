package com.moapan.shared.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Infrastructure only: callers must supply immutable public values and enforce access/quota outside. */
public final class VersionedReadCache<V> {
    private record Key(String id, String fingerprint) {}
    private record Entry<V>(V value, int weight) {}
    private static final class SnapshotChanged extends RuntimeException {}
    private final Cache<Key, Entry<V>> values;
    private final TransactionTemplate snapshot;
    private final ToIntFunction<V> weigh;
    private final int maximumEntryWeight;

    public VersionedReadCache(PlatformTransactionManager transactions, long maximumWeight,
            int maximumEntryWeight, ToIntFunction<V> weigh) {
        this(transactions, maximumWeight, maximumEntryWeight, weigh, Ticker.systemTicker());
    }

    VersionedReadCache(PlatformTransactionManager transactions, long maximumWeight,
            int maximumEntryWeight, ToIntFunction<V> weigh, Ticker ticker) {
        if (maximumWeight < 512 || maximumEntryWeight < 512 || maximumEntryWeight > maximumWeight)
            throw new IllegalArgumentException("Invalid cache weight limits");
        this.maximumEntryWeight = maximumEntryWeight;
        this.weigh = Objects.requireNonNull(weigh);
        values = Caffeine.newBuilder().maximumWeight(maximumWeight)
                .weigher((Key key, Entry<V> entry) -> entry.weight())
                .expireAfterWrite(Duration.ofMinutes(60)).ticker(ticker).build();
        snapshot = new TransactionTemplate(transactions);
        snapshot.setReadOnly(true);
        snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /** Read only small persisted fingerprints here; never hash source payloads on the request path. */
    public V read(String id, Supplier<String> fingerprint, Supplier<V> load) {
        Objects.requireNonNull(id);
        // Never expose uncommitted values or replace a worker's current transaction/row locks.
        if (TransactionSynchronizationManager.isActualTransactionActive()) return load.get();
        for (int attempt = 0; attempt < 3; attempt++) {
            String expected = fingerprint.get();
            if (expected == null) return snapshot.execute(status -> load.get());
            Key key = new Key(id, expected);
            AtomicReference<V> uncached = new AtomicReference<>();
            try {
                Entry<V> found = values.get(key, ignored -> {
                    V value = snapshot.execute(status -> {
                        if (!Objects.equals(expected, fingerprint.get())) throw new SnapshotChanged();
                        return load.get();
                    });
                    // TransactionTemplate has committed before Caffeine can publish this value.
                    if (value == null) return null;
                    long weight = 512L + 2L * (id.length() + expected.length()) + weigh.applyAsInt(value);
                    if (weight < 512 || weight > maximumEntryWeight) {
                        uncached.set(value);
                        return null;
                    }
                    return new Entry<>(value, (int) weight);
                });
                return found == null ? uncached.get() : found.value();
            } catch (SnapshotChanged changed) {
                // A commit between the first stamp read and loader snapshot must not mislabel data.
            }
        }
        return snapshot.execute(status -> load.get());
    }

    void cleanUp() { values.cleanUp(); }
    long estimatedSize() { return values.estimatedSize(); }
    long weightedSize() { return values.policy().eviction().orElseThrow().weightedSize().orElseThrow(); }
}
