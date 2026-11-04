package com.moapan.identity;

import com.moapan.identity.application.MailTransport;
import com.moapan.identity.domain.EmailPurpose;
import com.moapan.identity.infrastructure.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real JavaMail SMTP over loopback TLS; no external recipient and no trust bypass. */
class SmtpTransportTest {
    static Path root,ca;static SSLContext tls;
    @BeforeAll static void certificates()throws Exception{
        root=Path.of("build/test-smtp").toAbsolutePath().normalize();Files.createDirectories(root);var pass=root.resolve("fixture-password");Files.writeString(pass,"synthetic-keystore-password");var store=root.resolve("fixture.p12");Files.deleteIfExists(store);
        var command=List.of(Path.of(System.getProperty("java.home"),"bin/keytool").toString(),"-genkeypair","-alias","smtp","-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN=localhost","-ext","SAN=dns:localhost","-storetype","PKCS12","-keystore",store.toString(),"-storepass:file",pass.toString(),"-keypass:file",pass.toString());
        assertEquals(0,new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(root.resolve("keytool.log").toFile()).start().waitFor());
        var keys=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(store)){keys.load(in,"synthetic-keystore-password".toCharArray());}ca=root.resolve("ca.crt");Files.writeString(ca,"-----BEGIN CERTIFICATE-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(keys.getCertificate("smtp").getEncoded())+"\n-----END CERTIFICATE-----\n");var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"synthetic-keystore-password".toCharArray());tls=SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),null,null);
    }
    @AfterAll static void removePrivateFixtures()throws Exception{if(root!=null)try(var files=Files.list(root)){for(var file:files.toList())Files.deleteIfExists(file);}Files.deleteIfExists(root);}
    static MailTransport.Envelope message(){return new MailTransport.Envelope(UUID.randomUUID(),EmailPurpose.SIGNUP_CODE,"synthetic@example.test","Verification","Synthetic test only");}
    static SmtpMailTransport transport(String host,int port,String mode,boolean trusted){return new SmtpMailTransport(new MailSettings(true,host,port,"no-reply@example.test","","",mode,trusted?ca.toString():"","", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));}
    static final class Server implements AutoCloseable {
        final ServerSocket listener;final ExecutorService executor=Executors.newSingleThreadExecutor();final Future<?> task;volatile boolean received,upgraded;
        Server(boolean secure)throws Exception{this(secure,false);}
        Server(boolean secure,boolean offerStarttls)throws Exception{
            listener=secure?tls.getServerSocketFactory().createServerSocket(0,1,InetAddress.getLoopbackAddress()):new ServerSocket(0,1,InetAddress.getLoopbackAddress());listener.setSoTimeout(5000);
            task=executor.submit(()->serve(offerStarttls));
        }
        private void serve(boolean offerStarttls){
            SSLSocket secured=null;
            try(var socket=listener.accept()){
                socket.setSoTimeout(5000);var read=reader(socket);var write=writer(socket);boolean encrypted=socket instanceof SSLSocket;
                write.println("220 localhost ESMTP");String line;
                while((line=read.readLine())!=null){
                    if(line.startsWith("EHLO")||line.startsWith("HELO")){
                        if(offerStarttls&&!encrypted){write.println("250-localhost");write.println("250 STARTTLS");}else write.println("250 localhost");
                    }else if(line.equals("STARTTLS")&&offerStarttls&&!encrypted){
                        write.println("220 Ready for TLS");secured=(SSLSocket)tls.getSocketFactory().createSocket(socket,"localhost",socket.getPort(),true);secured.setUseClientMode(false);secured.setSoTimeout(5000);secured.startHandshake();
                        upgraded=true;encrypted=true;read=reader(secured);write=writer(secured);
                    }else if(offerStarttls&&!encrypted)write.println("530 STARTTLS required");
                    else if(line.startsWith("MAIL FROM")||line.startsWith("RCPT TO"))write.println("250 OK");
                    else if(line.equals("DATA")){write.println("354 continue");while((line=read.readLine())!=null&&!line.equals(".")){}received=true;write.println("250 accepted");}
                    else if(line.equals("QUIT")){write.println("221 bye");break;}
                    else write.println("502 unsupported");
                }
            }catch(IOException expected){/* Rejected CA/hostname peers can terminate during the handshake. */}
            finally{if(secured!=null)try{secured.close();}catch(IOException ignored){}}
        }
        private static BufferedReader reader(Socket socket)throws IOException{return new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));}
        private static PrintWriter writer(Socket socket)throws IOException{return new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true);}
        int port(){return listener.getLocalPort();}public void close()throws Exception{listener.close();executor.shutdownNow();executor.awaitTermination(6,TimeUnit.SECONDS);}
    }
    @Test void matchingTrustedCertificateDeliversToSyntheticSmtp()throws Exception{try(var server=new Server(true)){transport("localhost",server.port(),"SMTPS",true).send(message());assertTrue(server.received);}}
    @Test void wrongHostnameIsRejectedEvenWhenCertificateIsTrusted()throws Exception{try(var server=new Server(true)){assertThrows(IllegalStateException.class,()->transport("127.0.0.1",server.port(),"SMTPS",true).send(message()));assertFalse(server.received);}}
    @Test void unknownCaIsRejected()throws Exception{try(var server=new Server(true)){assertThrows(IllegalStateException.class,()->transport("localhost",server.port(),"SMTPS",false).send(message()));assertFalse(server.received);}}
    @Test void serverWithoutStarttlsCannotReceiveMessage()throws Exception{try(var server=new Server(false)){assertThrows(IllegalStateException.class,()->transport("localhost",server.port(),"STARTTLS",true).send(message()));assertFalse(server.received);}}
    @Test void requiredStarttlsWithVerifiedCertificateDelivers()throws Exception{try(var server=new Server(false,true)){transport("localhost",server.port(),"STARTTLS",true).send(message());assertTrue(server.upgraded);assertTrue(server.received);}}
    @Test void starttlsWithWrongHostnameCannotReceiveMessage()throws Exception{try(var server=new Server(false,true)){assertThrows(IllegalStateException.class,()->transport("127.0.0.1",server.port(),"STARTTLS",true).send(message()));assertFalse(server.received);}}
    @Test void starttlsWithUnknownCaCannotReceiveMessage()throws Exception{try(var server=new Server(false,true)){assertThrows(IllegalStateException.class,()->transport("localhost",server.port(),"STARTTLS",false).send(message()));assertFalse(server.received);}}
}
