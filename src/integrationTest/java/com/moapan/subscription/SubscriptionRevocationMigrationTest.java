package com.moapan.subscription;

import java.time.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

class SubscriptionRevocationMigrationTest {
    @Test void v24CorrectsOnlyOldRevocationEndsAndInvalidatesTheirPreviousVersions() {
        try(var postgres=new PostgreSQLContainer<>(DockerImageName.parse("postgres@sha256:edb0c08dc33befd0262ab3b5cf1c4f3f8cfd8b09e88c05b45adee29d6761ddb6").asCompatibleSubstituteFor("postgres"))){
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).target("23").cleanDisabled(true).load().migrate();
            var db=JdbcClient.create(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
            UUID active=UUID.randomUUID(),cancelled=UUID.randomUUID(),alreadyCorrect=UUID.randomUUID();
            for(var id:java.util.List.of(active,cancelled,alreadyCorrect))db.sql("INSERT INTO users(id,username,password_hash) VALUES(:id,:name,'synthetic-unused-hash')").param("id",id).param("name","migration_"+id.toString().replace("-","")).update();
            db.sql("INSERT INTO account_subscriptions VALUES(:id,'ACTIVE','2026-10-01T00:00:00Z','2026-12-01T00:00:00Z',1,'2026-11-01T00:00:00Z')").param("id",active).update();
            db.sql("INSERT INTO account_subscriptions VALUES(:id,'REVOKED','2026-12-01T00:00:00Z','2027-01-01T00:00:00Z',2,'2026-11-01T00:00:00Z')").param("id",cancelled).update();
            db.sql("INSERT INTO account_subscriptions VALUES(:id,'REVOKED','2026-10-01T00:00:00Z','2026-11-01T00:00:00Z',5,'2026-11-01T00:00:00Z')").param("id",alreadyCorrect).update();
            var latest=Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).cleanDisabled(true).load();latest.migrate();latest.validate();
            assertEquals(Instant.parse("2026-12-01T00:00:00Z"),db.sql("SELECT expires_at FROM account_subscriptions WHERE account_id=:id").param("id",active).query(OffsetDateTime.class).single().toInstant());
            assertEquals(1,db.sql("SELECT version FROM account_subscriptions WHERE account_id=:id").param("id",active).query(Integer.class).single());
            var corrected=db.sql("SELECT starts_at,expires_at,updated_at,status,version FROM account_subscriptions WHERE account_id=:id").param("id",cancelled).query().singleRow();
            assertEquals(Instant.parse("2026-12-01T00:00:00Z"),db.sql("SELECT starts_at FROM account_subscriptions WHERE account_id=:id").param("id",cancelled).query(OffsetDateTime.class).single().toInstant());assertEquals(corrected.get("updated_at"),corrected.get("expires_at"));assertEquals("REVOKED",corrected.get("status"));assertEquals(3L,corrected.get("version"));
            assertEquals(5,db.sql("SELECT version FROM account_subscriptions WHERE account_id=:id").param("id",alreadyCorrect).query(Integer.class).single());
            latest.migrate();assertEquals(3,db.sql("SELECT count(*) FROM account_subscriptions").query(Integer.class).single());
        }
    }
}
