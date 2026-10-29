package com.moapan.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface Accounts extends JpaRepository<Account,UUID> { Optional<Account> findByUsername(String username); }
