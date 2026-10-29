package com.moapan;
import org.junit.jupiter.api.Test;
import javax.net.ssl.*;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import static org.junit.jupiter.api.Assertions.*;
class RuntimeSecurityTest {
    @Test void exactVendorRuntimeIsUsed(){assertEquals("27.0.1.0.2+1",System.getProperty("java.runtime.version"));assertTrue(System.getProperty("java.vendor").contains("Adoptium"));assertNull(System.getProperty("javax.net.ssl.trustStore"));}
    @Test void compromisedChainRootsAreAbsent()throws Exception{
        // Adoptium 2026-10-27 advisory explicitly distinguishes retained G1 from compromised G3/AAA roots.
        var factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init((KeyStore)null);
        for(var manager:factory.getTrustManagers())if(manager instanceof X509TrustManager trust)for(X509Certificate cert:trust.getAcceptedIssuers()){
            String name=cert.getSubjectX500Principal().getName();assertFalse(name.contains("CN=emSign ECC Root CA - G3"));assertFalse(name.contains("CN=AAA Certificate Services"));
        }
    }
}
