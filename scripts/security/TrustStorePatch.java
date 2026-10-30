import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.naming.ldap.LdapName;

/** Official ops.java/emudhra four-root mitigation; no other trust decisions change. */
public class TrustStorePatch {
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Supply the selected JDK cacerts path");
        var path=Path.of(args[0]);char[] password="changeit".toCharArray();
        var store=KeyStore.getInstance(path.toFile(),password);
        Set<String> names=Set.of("emSign ECC Root CA - G3","AAA Certificate Services","emSign Root CA - G1","emSign Root CA - G2");
        int removed=0;
        for(String alias:Collections.list(store.aliases())){
            if(!(store.getCertificate(alias) instanceof X509Certificate certificate))continue;
            var subject=new LdapName(certificate.getSubjectX500Principal().getName());
            if(subject.getRdns().stream().anyMatch(r->r.getType().equalsIgnoreCase("CN")&&names.contains(r.getValue().toString()))){
                if(!certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())||certificate.getBasicConstraints()<0)throw new IllegalStateException("Expected self-issued root certificate");
                certificate.verify(certificate.getPublicKey());
                System.out.println("Removed official root: "+alias+" SHA256="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())));
                store.deleteEntry(alias);removed++;
            }
        }
        if(removed>0)try(var output=Files.newOutputStream(path)){store.store(output,password);}
        System.out.println("Official four-root mitigation complete; removed="+removed);
    }
}
