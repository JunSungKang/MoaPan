package com.moapan;
import com.moapan.bootstrap.AdminCommand;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@SpringBootApplication
@EnableScheduling
public class MoapanApplication {
    public static void main(String[] args) {
        String role = System.getenv().getOrDefault("DAMGOX2_ROLE", "web");
        if (!java.util.Set.of("web","worker","migrate","admin-cli","policy-info","policy-transition").contains(role)) throw new IllegalArgumentException("Invalid role");
        if (role.equals("policy-info")) { com.moapan.bootstrap.AiPolicyTransition.printIdentity(); return; }
        var database=com.moapan.bootstrap.DatabaseSettings.load(System.getenv(),role);
        if (role.equals("policy-transition")) { com.moapan.bootstrap.AiPolicyTransition.run(database,System.getenv().get("DAMGOX2_POLICY_TRANSITION")); return; }
        if (role.equals("migrate")) { org.flywaydb.core.Flyway.configure().dataSource(database.url(),database.user(),database.password()).cleanDisabled(true).load().migrate(); restrictHistory(database); return; }
        SpringApplication app = new SpringApplication(MoapanApplication.class);
        app.addInitializers(context->context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("database-secrets",java.util.Map.of("spring.datasource.url",database.url(),"spring.datasource.username",database.user(),"spring.datasource.password",database.password()))));
        if (!role.equals("web")) app.setWebApplicationType(WebApplicationType.NONE);
        if (role.equals("admin-cli")) {
            try (var context = app.run(args)) { context.getBean(AdminCommand.class).run(); }
        } else app.run(args);
    }
    private static void restrictHistory(com.moapan.bootstrap.DatabaseSettings database) {
        try (var connection=java.sql.DriverManager.getConnection(database.url(),database.user(),database.password());var statement=connection.createStatement()) { statement.execute("REVOKE INSERT,UPDATE,DELETE ON flyway_schema_history FROM "+statement.enquoteIdentifier(database.runtimeUser(),true)); } catch(java.sql.SQLException e) { throw new IllegalStateException("Migration privilege finalization failed"); }
    }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
