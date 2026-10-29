package com.moapan;
import com.moapan.bootstrap.AdminCommand;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@SpringBootApplication
@EnableScheduling
public class MoapanApplication {
    public static void main(String[] args) {
        String role = System.getenv().getOrDefault("MOAPAN_ROLE", "web");
        if (!java.util.Set.of("web","worker","migrate","admin-cli").contains(role)) throw new IllegalArgumentException("Invalid role");
        if (role.equals("migrate")) { org.flywaydb.core.Flyway.configure().dataSource(System.getenv("DATABASE_URL"),System.getenv("DATABASE_USER"),System.getenv("DATABASE_PASSWORD")).cleanDisabled(true).load().migrate(); restrictHistory(); return; }
        SpringApplication app = new SpringApplication(MoapanApplication.class);
        if (!role.equals("web")) app.setWebApplicationType(WebApplicationType.NONE);
        try (var ignored = role.equals("migrate") || role.equals("admin-cli") ? launchCommand(app,args,role) : null) {
            if (ignored == null) app.run(args);
        }
    }
    private static void restrictHistory() {
        try (var connection=java.sql.DriverManager.getConnection(System.getenv("DATABASE_URL"),System.getenv("DATABASE_USER"),System.getenv("DATABASE_PASSWORD"));var statement=connection.createStatement()) { statement.execute("REVOKE INSERT,UPDATE,DELETE ON flyway_schema_history FROM moapan"); } catch(java.sql.SQLException e) { throw new IllegalStateException("Migration privilege finalization failed"); }
    }
    private static ConfigurableApplicationContext launchCommand(SpringApplication app,String[] args,String role) {
        var context = app.run(args);
        if (role.equals("migrate")) context.getBean(org.flywaydb.core.Flyway.class).migrate();
        else context.getBean(AdminCommand.class).run();
        return context;
    }
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean org.flywaydb.core.Flyway flyway(javax.sql.DataSource source) {
        return org.flywaydb.core.Flyway.configure().dataSource(source).cleanDisabled(true).load();
    }
}
