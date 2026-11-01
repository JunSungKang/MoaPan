package com.moapan.cache;

import com.moapan.shared.infrastructure.cache.VersionedReadCache;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

class ReadCacheIntegrationTest {
    static final PostgreSQLContainer<?> pg=new PostgreSQLContainer<>(DockerImageName.parse("postgres@sha256:edb0c08dc33befd0262ab3b5cf1c4f3f8cfd8b09e88c05b45adee29d6761ddb6").asCompatibleSubstituteFor("postgres"));
    static JdbcClient db;static DataSourceTransactionManager tm;static DriverManagerDataSource ds;
    private final UUID subject=UUID.randomUUID();
    @BeforeAll static void database() {
        pg.start();ds=new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(ds).cleanDisabled(true).load();flyway.migrate();flyway.validate();flyway.migrate();
        db=JdbcClient.create(ds);tm=new DataSourceTransactionManager(ds);
        db.sql("CREATE TABLE synthetic_cache_data(id uuid PRIMARY KEY,value text NOT NULL)").update();
    }
    @AfterAll static void stop() {pg.stop();}
    @BeforeEach void fixture() {write("old");}
    private void write(String text) {new TransactionTemplate(tm).executeWithoutResult(s->{
        db.sql("INSERT INTO synthetic_cache_data VALUES(:id,:text) ON CONFLICT(id) DO UPDATE SET value=EXCLUDED.value").param("id",subject).param("text",text).update();
        db.sql("SELECT set_read_cache_fingerprint('synthetic',:id,jsonb_build_object('value',CAST(:text AS text)))").param("id",subject).param("text",text).query((r,n)->1).list();
    });}
    private String stamp() {return db.sql("SELECT fingerprint FROM read_cache_fingerprints WHERE domain='synthetic' AND subject=:id").param("id",subject).query(String.class).single();}
    private String value() {return db.sql("SELECT value FROM synthetic_cache_data WHERE id=:id").param("id",subject).query(String.class).single();}
    private VersionedReadCache<String> cache() {return new VersionedReadCache<>(tm,4096,2048,String::length);}
    @Test void hashesChangeAtomicallyAndIdenticalWritesDoNotChurnFingerprintRows() {
        String before=stamp();assertTrue(before.matches("[0-9a-f]{64}"));
        String rowVersion=db.sql("SELECT xmin::text FROM read_cache_fingerprints WHERE domain='synthetic' AND subject=:id").param("id",subject).query(String.class).single();
        write("old");assertEquals(before,stamp());
        assertEquals(rowVersion,db.sql("SELECT xmin::text FROM read_cache_fingerprints WHERE domain='synthetic' AND subject=:id").param("id",subject).query(String.class).single());
        new TransactionTemplate(tm).executeWithoutResult(s->{write("rollback");assertNotEquals(before,stamp());s.setRollbackOnly();});
        assertEquals(before,stamp());assertEquals("old",value());write("new");assertNotEquals(before,stamp());
    }
    @Test void separateWebCachesNoticeCommittedChangeAndNeverPublishRolledBackWriterData() {
        var one=cache();var two=cache();assertEquals("old",one.read("one",this::stamp,this::value));assertEquals("old",two.read("one",this::stamp,this::value));
        new TransactionTemplate(tm).executeWithoutResult(s->{write("private");assertEquals("private",one.read("one",this::stamp,this::value));s.setRollbackOnly();});
        assertEquals("old",one.read("one",this::stamp,this::value));
        write("new");assertEquals("new",one.read("one",this::stamp,this::value));assertEquals("new",two.read("one",this::stamp,this::value));
    }
    @Test void commitBetweenInitialProbeAndLoaderSnapshotCannotCacheUnderOldHash() {
        var cache=cache();var reads=new AtomicInteger();
        String actual=cache.read("one",()->{String hash=stamp();if(reads.incrementAndGet()==1)write("new");return hash;},this::value);
        assertEquals("new",actual);assertEquals(4,reads.get());
        assertEquals("new",cache.read("one",this::stamp,()->{fail("New snapshot must be cached under its own fingerprint");return null;}));
    }
    @Test void committedFingerprintsSurviveActualDatabaseContainerRestart() {
        String before=stamp();write("persistent");String after=stamp();assertNotEquals(before,after);
        String initialPort=pg.getDockerClient().inspectContainerCmd(pg.getContainerId()).exec().getNetworkSettings().getPorts()
                .getBindings().get(com.github.dockerjava.api.model.ExposedPort.tcp(5432))[0].getHostPortSpec();
        pg.getDockerClient().restartContainerCmd(pg.getContainerId()).exec();
        var inspected=pg.getDockerClient().inspectContainerCmd(pg.getContainerId()).exec();
        assertTrue(Boolean.TRUE.equals(inspected.getState().getRunning()));
        String restartedPort=inspected.getNetworkSettings().getPorts().getBindings()
                .get(com.github.dockerjava.api.model.ExposedPort.tcp(5432))[0].getHostPortSpec();
        // Docker may allocate a new ephemeral host port on restart; never keep a stale JDBC URL.
        System.out.println("Synthetic DB restart: mapped port changed="+!initialPort.equals(restartedPort));
        ds.setUrl("jdbc:postgresql://"+pg.getHost()+":"+restartedPort+"/"+pg.getDatabaseName());
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(30)).ignoreExceptions()
                .until(()->"persistent".equals(value()));
        assertEquals(after,stamp());
    }
}
