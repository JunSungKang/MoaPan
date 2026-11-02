package com.moapan.identity;

import com.moapan.identity.domain.*;
import com.moapan.identity.infrastructure.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmailSecurityTest {
    static MailSettings settings(){return new MailSettings(true,"localhost",587,"no-reply@example.test","","","STARTTLS","","","MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");}
    @Test void emailCannotAddRecipientsHeadersOrAliasCollisions(){
        assertEquals("user+notice@example.test",CredentialPolicy.email(" User+Notice@Example.Test "));
        for(String value:List.of("x@example.test\r\nBcc: other@example.test","a@example.test,b@example.test","Name <a@example.test>","a..b@example.test",".a@example.test","a@localhost","a@-bad.test","é@example.test"))assertThrows(RuntimeException.class,()->CredentialPolicy.email(value));
        assertNotEquals(CredentialPolicy.email("a.b@example.test"),CredentialPolicy.email("ab@example.test"));
    }
    @Test void existingCredentialLimitsStayUnchanged(){for(String value:List.of("x","a".repeat(128)))assertEquals(value,CredentialPolicy.username(value));for(String value:List.of("abcdef","😀".repeat(128),"      "))assertDoesNotThrow(()->CredentialPolicy.password(value));for(String value:List.of("abcde","a".repeat(129)))assertThrows(RuntimeException.class,()->CredentialPolicy.password(value));}
    @Test void envelopesAreRandomizedAndBoundToTheirRowAndPurpose(){var secrets=new EmailSecrets(settings());byte[] one=secrets.encrypt("account:one","synthetic@example.test"),two=secrets.encrypt("account:one","synthetic@example.test");assertFalse(Arrays.equals(one,two));assertEquals("synthetic@example.test",secrets.decrypt("account:one",one));assertThrows(RuntimeException.class,()->secrets.decrypt("outbox:one",one));one[one.length-1]^=1;assertThrows(RuntimeException.class,()->secrets.decrypt("account:one",one));assertNotEquals(secrets.fingerprint("address","same"),secrets.fingerprint("session","same"));}
    @Test void plaintextSmtpAndUnkeyedStorageAreRejected(){assertThrows(IllegalStateException.class,()->new MailSettings(true,"localhost",25,"no-reply@example.test","","","NONE","","",""));assertThrows(IllegalStateException.class,()->new MailSettings(true,"localhost",25,"no-reply@example.test","","","STARTTLS","","",""));assertThrows(IllegalArgumentException.class,()->EmailPurpose.valueOf("MARKETING"));assertThrows(IllegalArgumentException.class,()->EmailPurpose.valueOf("ARBITRARY"));}
}
