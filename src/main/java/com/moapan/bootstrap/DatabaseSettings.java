package com.moapan.bootstrap;

import java.nio.file.*;
import java.util.Map;

/** Operator-only database configuration; secrets are never represented in toString/logs. */
public final class DatabaseSettings {
    private final String url,user,password,runtimeUser;
    private DatabaseSettings(String url,String user,String password,String runtimeUser){this.url=url;this.user=user;this.password=password;this.runtimeUser=runtimeUser;}
    public String url(){return url;} public String user(){return user;} public String password(){return password;} public String runtimeUser(){return runtimeUser;}
    public static DatabaseSettings load(Map<String,String> env,String role){
        String host=env.get("DAMGOX2_DATABASE_HOST"),url;
        if(host!=null&&!host.isBlank()){
            if(!host.matches("[A-Za-z0-9.-]{1,253}"))throw new IllegalStateException("invalid_database_host");
            String port=env.getOrDefault("DAMGOX2_DATABASE_PORT","5432"),name=env.getOrDefault("DAMGOX2_DATABASE_NAME","damgox2");
            if(!port.matches("[0-9]{1,5}")||Integer.parseInt(port)<1||Integer.parseInt(port)>65535||!name.matches("[a-z_][a-z_0-9]{0,62}"))throw new IllegalStateException("invalid_database_address");
            url="jdbc:postgresql://"+host+":"+port+"/"+name;
        }else{url=env.get("DATABASE_URL");if(url==null||!url.startsWith("jdbc:postgresql://"))throw new IllegalStateException("database_url_required");}
        String runtime=env.getOrDefault("DAMGOX2_DATABASE_USER",role.equals("migrate")?"damgox2":env.getOrDefault("DATABASE_USER","damgox2_sid"));
        boolean migration=role.equals("migrate");String user=migration?env.getOrDefault("DAMGOX2_DATABASE_MIGRATION_USER",env.getOrDefault("DATABASE_USER",runtime)):runtime;
        if(!runtime.matches("[a-z_][a-z_0-9]{0,62}")||!user.matches("[a-z_][a-z_0-9]{0,62}"))throw new IllegalStateException("invalid_database_user");
        String file=migration?env.getOrDefault("DAMGOX2_DATABASE_MIGRATION_PASSWORD_FILE",env.get("DAMGOX2_DATABASE_PASSWORD_FILE")):env.get("DAMGOX2_DATABASE_PASSWORD_FILE");
        String password;
        if(file!=null&&!file.isBlank()){
            try{var p=Path.of(file);if(!Files.isRegularFile(p)||Files.size(p)>4096)throw new IllegalStateException();password=Files.readString(p);if(password.endsWith("\n"))password=password.substring(0,password.length()-1);if(password.endsWith("\r"))password=password.substring(0,password.length()-1);}
            catch(Exception e){throw new IllegalStateException("database_secret_unreadable");}
        }else password=env.get("DATABASE_PASSWORD");
        if(password==null||password.isEmpty()||password.indexOf('\n')>=0||password.indexOf('\r')>=0||password.indexOf('\0')>=0)throw new IllegalStateException("database_secret_required");
        return new DatabaseSettings(url,user,password,runtime);
    }
}
