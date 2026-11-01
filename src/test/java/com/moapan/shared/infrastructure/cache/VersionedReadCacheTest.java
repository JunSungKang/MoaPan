package com.moapan.shared.infrastructure.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;

class VersionedReadCacheTest {
    private static final class Transactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks;
        boolean failCommit;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            assertTrue(definition.isReadOnly() || definition.getIsolationLevel() == TransactionDefinition.ISOLATION_DEFAULT);
        }
        protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new IllegalStateException("synthetic commit failure");
            commits++;
        }
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
    @Test void unchangedFingerprintReusesPayloadButChecksSmallStampEveryTime() {
        var tm=new Transactions();var reads=new AtomicInteger();var loads=new AtomicInteger();
        var cache=new VersionedReadCache<String>(tm,4096,2048,String::length);
        assertEquals("data",cache.read("one",()->{reads.incrementAndGet();return "a";},()->{loads.incrementAndGet();return "data";}));
        assertEquals("data",cache.read("one",()->{reads.incrementAndGet();return "a";},()->{fail("Payload must not reload");return null;}));
        assertEquals(3,reads.get());assertEquals(1,loads.get());assertEquals(1,tm.commits);
    }
    @Test void newFingerprintRefreshesBeforeSixtyMinutesAndTtlForcesReloadWithoutChange() {
        var ticks=new AtomicLong();var tm=new Transactions();
        var cache=new VersionedReadCache<String>(tm,4096,2048,String::length,ticks::get);
        assertEquals("old",cache.read("one",()->"a",()->"old"));
        ticks.set(Duration.ofMinutes(1).toNanos());
        assertEquals("new",cache.read("one",()->"b",()->"new"));
        ticks.set(Duration.ofMinutes(61).toNanos()+1);
        assertEquals("reloaded",cache.read("one",()->"b",()->"reloaded"));
    }
    @Test void oversizedValuesAndFailuresAreNeverRetained() {
        var tm=new Transactions();var cache=new VersionedReadCache<String>(tm,4096,1024,String::length);
        String large="x".repeat(1024);assertEquals(large,cache.read("large",()->"a",()->large));
        cache.cleanUp();assertEquals(0,cache.estimatedSize());
        assertThrows(IllegalStateException.class,()->cache.read("failed",()->"a",()->{throw new IllegalStateException("synthetic");}));
        assertEquals(0,cache.estimatedSize());assertEquals(1,tm.rollbacks);
        tm.failCommit=true;
        assertThrows(IllegalStateException.class,()->cache.read("commit",()->"a",()->"uncommitted"));
        assertEquals(0,cache.estimatedSize());
    }
    @Test void missingStampBypassesCache() {
        var cache=new VersionedReadCache<String>(new Transactions(),4096,2048,String::length);
        assertEquals("one",cache.read("one",()->null,()->"one"));
        assertEquals("two",cache.read("one",()->null,()->"two"));assertEquals(0,cache.estimatedSize());
    }
    @Test void activeWriterTransactionBypassesWithoutPublishingOrReadingStamp() {
        var tm=new Transactions();var cache=new VersionedReadCache<String>(tm,4096,2048,String::length);
        assertEquals("committed",cache.read("one",()->"a",()->"committed"));
        new TransactionTemplate(tm).executeWithoutResult(status->{
            assertEquals("private",cache.read("one",()->{fail("Writer must bypass stamp cache");return "a";},()->"private"));
            status.setRollbackOnly();
        });
        assertEquals("committed",cache.read("one",()->"a",()->{fail("Uncommitted result must not publish");return null;}));
    }
    @Test void changedStampBetweenProbeAndSnapshotRetriesWithoutWrongKey() {
        var tm=new Transactions();var probes=new AtomicInteger();
        var cache=new VersionedReadCache<String>(tm,4096,2048,String::length);
        assertEquals("new",cache.read("one",()->probes.incrementAndGet()==1?"a":"b",()->"new"));
        assertEquals(1,tm.rollbacks);assertEquals(1,tm.commits);assertEquals(4,probes.get());
        assertEquals("new",cache.read("one",()->"b",()->{fail("Correct key should be retained");return null;}));
    }
    @Test void changingFingerprintCannotCauseUnboundedRetries() {
        var tm=new Transactions();var probes=new AtomicInteger();
        var cache=new VersionedReadCache<String>(tm,4096,2048,String::length);
        assertEquals("fresh",cache.read("one",()->String.valueOf(probes.incrementAndGet()),()->"fresh"));
        assertEquals(6,probes.get());assertEquals(3,tm.rollbacks);assertEquals(1,tm.commits);assertEquals(0,cache.estimatedSize());
    }
    @Test void logicalWeightAndRegionBudgetsAreBounded() {
        var tm=new Transactions();var cache=new VersionedReadCache<String>(tm,4096,2048,String::length);
        for(int i=0;i<100;i++)cache.read("entry"+i,()->"a",()->"x".repeat(200));
        cache.cleanUp();assertTrue(cache.weightedSize()<=4096);assertTrue(cache.estimatedSize()<=8);
        var web=new SharedReadCaches(tm,"web");var worker=new SharedReadCaches(tm,"worker");
        assertEquals(16*1024*1024,web.logicalBudgetBytes());assertEquals(8*1024*1024,worker.logicalBudgetBytes());
        web.create(SharedReadCaches.Region.DICTIONARY,(String s)->s.length());
        assertThrows(IllegalStateException.class,()->web.create(SharedReadCaches.Region.DICTIONARY,(String s)->s.length()));
    }
    @Test void concurrentMissesForSameVersionLoadOnce() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var loads=new AtomicInteger();
        var cache=new VersionedReadCache<String>(new Transactions(),4096,2048,String::length);
        try(var pool=Executors.newFixedThreadPool(8)) {
            var calls=new ArrayList<Future<String>>();
            for(int i=0;i<8;i++)calls.add(pool.submit(()->cache.read("one",()->"a",()->{
                loads.incrementAndGet();entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return "same";
            })));
            assertTrue(entered.await(5,TimeUnit.SECONDS));release.countDown();
            for(var call:calls)assertEquals("same",call.get(5,TimeUnit.SECONDS));
        } finally {release.countDown();}
        assertEquals(1,loads.get());
    }
}
