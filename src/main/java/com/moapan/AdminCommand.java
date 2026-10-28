package com.moapan;
import com.moapan.auth.Accounts;
import org.springframework.stereotype.Component;
@Component
public class AdminCommand {
    private final Accounts accounts;
    public AdminCommand(Accounts accounts){this.accounts=accounts;}
    public void run(){var console=System.console();String username;try{username=console==null?new java.io.BufferedReader(new java.io.InputStreamReader(System.in,java.nio.charset.StandardCharsets.UTF_8)).readLine():console.readLine("Existing username: ");}catch(java.io.IOException e){throw new IllegalStateException("Unable to read username");}if(username==null||!username.matches("[a-z0-9_]{4,32}"))throw new IllegalArgumentException("Invalid username");var account=accounts.findByUsername(username).orElseThrow(()->new IllegalArgumentException("Account not found"));account.role="ADMIN";accounts.save(account);System.out.println("Administrator role granted.");}
}
