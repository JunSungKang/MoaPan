package com.moapan.identity;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

/** Upgrade a populated V24 database; never use the live database or real member information. */
class EmailMigrationIntegrationTest {
    @Test void v25PreservesExistingCredentialsSessionsAndSubscriptionsWithoutInventingEmail() {
        try(var postgres=new PostgreSQLContainer<>(DockerImageName.parse("postgres@sha256:edb0c08dc33befd0262ab3b5cf1c4f3f8cfd8b09e88c05b45adee29d6761ddb6").asCompatibleSubstituteFor("postgres"))){
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).target("24").cleanDisabled(true).load().migrate();
            var db=JdbcClient.create(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
            var encoder=new Argon2PasswordEncoder(16,32,1,19456,2);
            String password="synthetic migration credential",hash=encoder.encode(password);
            UUID admin=UUID.randomUUID(),inactive=UUID.randomUUID();
            db.sql("INSERT INTO users(id,username,password_hash,active,role,auth_version,terms_version,terms_accepted_at) VALUES(:id,'legacy_admin',:hash,true,'ADMIN',7,'terms-1','2026-10-01T00:00:00Z')").param("id",admin).param("hash",hash).update();
            db.sql("INSERT INTO users(id,username,password_hash,active,role,auth_version) VALUES(:id,'legacy_inactive',:hash,false,'USER',9)").param("id",inactive).param("hash",hash).update();
            for(UUID user:List.of(admin,inactive)){
                String session=UUID.randomUUID().toString();
                db.sql("INSERT INTO spring_session VALUES(:primary,:session,1790000000000,1790000000000,1800,1890000000000,:principal)").param("primary",session).param("session",UUID.randomUUID().toString()).param("principal",user.toString()).update();
                db.sql("INSERT INTO spring_session_attributes VALUES(:session,'synthetic-operational-attribute',:value)").param("session",session).param("value","synthetic session bytes".getBytes(StandardCharsets.UTF_8)).update();
            }
            db.sql("INSERT INTO account_subscriptions VALUES(:id,'ACTIVE','2026-10-01T00:00:00Z','2027-01-01T00:00:00Z',3,'2026-10-01T00:00:00Z')").param("id",admin).update();
            db.sql("INSERT INTO account_subscriptions VALUES(:id,'REVOKED','2026-10-01T00:00:00Z','2026-11-01T00:00:00Z',5,'2026-11-01T00:00:00Z')").param("id",inactive).update();
            String users="SELECT row_to_json(t)::text FROM (SELECT id,username,password_hash,active,role,auth_version,terms_version,terms_accepted_at FROM users ORDER BY id) t";
            var beforeUsers=db.sql(users).query(String.class).list();
            var snapshots=new LinkedHashMap<String,List<String>>();
            for(String table:List.of("spring_session","spring_session_attributes","account_subscriptions"))snapshots.put(table,snapshot(db,table));
            var latest=Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).target("25").cleanDisabled(true).load();
            assertEquals(1,latest.migrate().migrationsExecuted);latest.validate();
            assertTrue(beforeUsers.equals(db.sql(users).query(String.class).list()),"Existing credential/role/consent rows must remain identical");
            for(var entry:snapshots.entrySet())assertTrue(entry.getValue().equals(snapshot(db,entry.getKey())),"Existing operational rows changed: "+entry.getKey());
            assertEquals(2,db.sql("SELECT count(*) FROM users WHERE email_cipher IS NULL AND email_key IS NULL AND email_verified_at IS NULL AND email_policy_version IS NULL AND email_policy_accepted_at IS NULL").query(Integer.class).single());
            assertTrue(encoder.matches(password,db.sql("SELECT password_hash FROM users WHERE id=:id").param("id",admin).query(String.class).single()));
            assertThrows(DataIntegrityViolationException.class,()->db.sql("UPDATE users SET email_key=repeat('a',64) WHERE id=:id").param("id",admin).update());
            assertEquals(0,latest.migrate().migrationsExecuted);
            assertTrue(beforeUsers.equals(db.sql(users).query(String.class).list()),"Repeated migration must not alter legacy accounts");
            var current=Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).cleanDisabled(true).load();
            int pending=current.info().pending().length;assertEquals(pending,current.migrate().migrationsExecuted);current.validate();
            assertEquals(0,current.info().pending().length);assertEquals(beforeUsers,db.sql(users).query(String.class).list());
            for(var entry:snapshots.entrySet())assertEquals(entry.getValue(),snapshot(db,entry.getKey()),"Later migrations must also preserve "+entry.getKey());
        }
    }
    private static List<String> snapshot(JdbcClient db,String table){
        if(!Set.of("spring_session","spring_session_attributes","account_subscriptions").contains(table))throw new IllegalArgumentException("Fixture table only");
        return db.sql("SELECT row_to_json(t)::text FROM "+table+" t ORDER BY row_to_json(t)::text").query(String.class).list();
    }
}
