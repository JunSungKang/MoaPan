import javax.net.ssl.*;
import java.security.*;
import java.security.cert.*;
import java.net.*;
import java.time.*;
public class TrustAudit {
 public static void main(String[] args) throws Exception {
  System.out.println("runtime="+System.getProperty("java.runtime.version")+" vendor="+System.getProperty("java.vendor"));
  if(System.getProperty("javax.net.ssl.trustStore")!=null)throw new IllegalStateException("Unexpected custom truststore");
  var f=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); f.init((KeyStore)null); int roots=0;
  for(var m:f.getTrustManagers()) if(m instanceof X509TrustManager t) for(var c:t.getAcceptedIssuers()){roots++;String n=c.getSubjectX500Principal().getName();if(java.util.List.of("emSign ECC Root CA - G3","AAA Certificate Services","emSign Root CA - G1","emSign Root CA - G2").stream().anyMatch(root->n.contains("CN="+root)))throw new IllegalStateException("Official mitigation root present");}
  System.out.println("roots="+roots+" official-four-roots=absent");
  var good=(HttpsURLConnection)URI.create("https://www.nasa.gov/").toURL().openConnection();good.setConnectTimeout(10000);good.setReadTimeout(10000);good.connect();System.out.println("ordinary-https=PASS");good.disconnect();
  String[] hosts={"dv-g3b","dv-g3c","ov-g3b","ov-g3c","ev-g3b","ev-g3c","dv-g3b-aaa","dv-g3c-aaa","ov-g3b-aaa","ov-g3c-aaa","ev-g3b-aaa","ev-g3c-aaa","dv-g3b-noct","dv-g3b-aaa-noct"};
  for(String h:hosts){var c=(HttpsURLConnection)URI.create("https://"+h+"-emudhra.we-have-a-problem.com/").toURL().openConnection();c.setConnectTimeout(10000);c.setReadTimeout(10000);try {c.connect();throw new IllegalStateException("Compromised chain accepted: "+h);}catch(SSLHandshakeException e){System.out.println(h+"=TLS_REJECTED");}finally{c.disconnect();}}
 }
}
