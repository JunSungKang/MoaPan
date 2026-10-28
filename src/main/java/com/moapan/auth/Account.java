package com.moapan.auth;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="users")
public class Account {
    @Id public UUID id;
    @Column(nullable=false,unique=true,length=32) public String username;
    @Column(name="password_hash",nullable=false,length=255) public String passwordHash;
    @Column(nullable=false) public boolean active=true;
    @Column(nullable=false,length=5) public String role="USER";
    protected Account() {}
    public Account(String username,String hash){id=UUID.randomUUID();this.username=username;passwordHash=hash;}
}
