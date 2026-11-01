package com.moapan.cache;

import java.lang.reflect.*;
import java.sql.Connection;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Isolated synthetic PostgreSQL and SQL-shape counters; never records parameters or member events. */
final class CacheDatabase implements AutoCloseable {
    private final PostgreSQLContainer<?> pg=new PostgreSQLContainer<>(DockerImageName.parse("postgres@sha256:edb0c08dc33befd0262ab3b5cf1c4f3f8cfd8b09e88c05b45adee29d6761ddb6").asCompatibleSubstituteFor("postgres"));
    private final ConcurrentHashMap<String,AtomicInteger> counts=new ConcurrentHashMap<>();
    final JdbcClient db;
    final DataSourceTransactionManager tm;
    CacheDatabase() {
        pg.start();var raw=new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());
        org.flywaydb.core.Flyway.configure().dataSource(raw).cleanDisabled(true).load().migrate();
        var counted=new AbstractDataSource(){
            public Connection getConnection() throws java.sql.SQLException {return track(raw.getConnection());}
            public Connection getConnection(String user,String password)throws java.sql.SQLException{return track(raw.getConnection(user,password));}
        };
        db=JdbcClient.create(counted);tm=new DataSourceTransactionManager(counted);
    }
    private Connection track(Connection connection) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,args)->{
            if(m.getName().startsWith("prepare")&&args!=null&&args.length>0&&args[0] instanceof String sql)
                counts.computeIfAbsent(sql.replaceAll("\\s+"," ").toLowerCase(Locale.ROOT),k->new AtomicInteger()).incrementAndGet();
            try{return m.invoke(connection,args);}catch(InvocationTargetException e){throw e.getCause();}
        });
    }
    int readsContaining(String sql){return counts.entrySet().stream().filter(e->e.getKey().startsWith("select ")&&e.getKey().contains(sql.toLowerCase(Locale.ROOT))).mapToInt(e->e.getValue().get()).sum();}
    void reset(){counts.clear();}
    String jdbcUrl(){return pg.getJdbcUrl();}
    String username(){return pg.getUsername();}
    String password(){return pg.getPassword();}
    public void close(){pg.stop();}
}
