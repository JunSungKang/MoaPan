package com.moapan.identity.infrastructure;
import com.moapan.identity.infrastructure.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface Accounts extends JpaRepository<Account,UUID> { Optional<Account> findByUsername(String username); }
