package com.moapan.bootstrap;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;
class DatabaseSettingsTest {
 @TempDir Path dir;
 @Test void secretFilePreservesSpacesAndSeparatesMigrationIdentity()throws Exception{
  Path app=dir.resolve("app"),migration=dir.resolve("migration");Files.writeString(app," synthetic DB password \n");Files.writeString(migration,"migration-only-password\n");var e=new HashMap<String,String>();e.put("DAMGOX2_DATABASE_HOST","shared-pg");e.put("DAMGOX2_DATABASE_USER","damgox2_sid");e.put("DAMGOX2_DATABASE_PASSWORD_FILE",app.toString());e.put("DAMGOX2_DATABASE_MIGRATION_USER","damgox2_migrate");e.put("DAMGOX2_DATABASE_MIGRATION_PASSWORD_FILE",migration.toString());var runtime=DatabaseSettings.load(e,"worker");assertEquals("jdbc:postgresql://shared-pg:5432/damgox2",runtime.url());assertEquals(" synthetic DB password ",runtime.password());assertEquals("damgox2_sid",runtime.user());assertEquals("migration-only-password",DatabaseSettings.load(e,"migrate").password());assertFalse(runtime.toString().contains("password"));
 }
 @Test void malformedAddressesUnreadableOrMultilineSecretsFailWithoutValues()throws Exception{
  var e=new HashMap<String,String>(Map.of("DAMGOX2_DATABASE_HOST","postgres","DAMGOX2_DATABASE_USER","damgox2_sid","DAMGOX2_DATABASE_PASSWORD_FILE",dir.resolve("absent").toString()));assertThrows(IllegalStateException.class,()->DatabaseSettings.load(e,"web"));Path p=dir.resolve("secret");Files.writeString(p,"private-first\nprivate-second");e.put("DAMGOX2_DATABASE_PASSWORD_FILE",p.toString());var error=assertThrows(IllegalStateException.class,()->DatabaseSettings.load(e,"web"));assertFalse(error.toString().contains("private-"));e.put("DAMGOX2_DATABASE_HOST","localhost/?password=leak");assertThrows(IllegalStateException.class,()->DatabaseSettings.load(e,"web"));
 }
 @Test void legacyEnvironmentRemainsAvailableForCurrentDatabaseAndTests(){var c=DatabaseSettings.load(Map.of("DATABASE_URL","jdbc:postgresql://db:5432/damgox2","DATABASE_USER","damgox2","DATABASE_PASSWORD","synthetic-only"),"worker");assertEquals("damgox2",c.user());}
}
