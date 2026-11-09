import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.util.*;
import javax.net.ssl.*;

/** Connection-only probe: never submits an envelope, recipient or message. No protocol/credential logging. */
public final class SmtpConnectionCheck {
    private static BufferedReader reader; private static BufferedWriter writer;
    private static List<String> reply(int code)throws IOException {
        var lines=new ArrayList<String>();
        for(int i=0;i<100;i++) {
            String line=reader.readLine();
            if(line==null||line.length()>4096||!line.startsWith(Integer.toString(code)))throw new IOException("SMTP connection rejected");
            lines.add(line); if(line.length()>3&&line.charAt(3)==' ')return lines;
        }
        throw new IOException("SMTP reply limit");
    }
    private static void send(String line)throws IOException {writer.write(line+"\r\n");writer.flush();}
    private static void streams(Socket s)throws IOException {reader=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.US_ASCII));writer=new BufferedWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.US_ASCII));}
    public static void main(String[] ignored) {
        try {
            String host=System.getenv("DAMGOX2_MAIL_HOST"),user=System.getenv("DAMGOX2_MAIL_USERNAME");
            int port=Integer.parseInt(System.getenv("DAMGOX2_MAIL_PORT"));
            if(host==null||!host.matches("[A-Za-z0-9.-]+")||user==null||user.isBlank())throw new IOException();
            var store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null,null);
            try(var input=Files.newInputStream(Path.of(System.getenv("DAMGOX2_MAIL_CA_FILE")))) {
                var certificates=CertificateFactory.getInstance("X.509").generateCertificates(input);int index=0;
                for(var certificate:certificates)store.setCertificateEntry("operator-"+index++,certificate);
                if(index==0)throw new IOException();
            }
            var trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(store);
            var context=SSLContext.getInstance("TLS");context.init(null,trust.getTrustManagers(),null);
            try(var socket=new Socket()) {
                socket.connect(new InetSocketAddress(host,port),10000);socket.setSoTimeout(10000);streams(socket);reply(220);
                send("EHLO damgox2-check.invalid");var before=reply(250);
                if(before.stream().noneMatch(x->x.toUpperCase(Locale.ROOT).contains("STARTTLS")))throw new IOException();
                send("STARTTLS");reply(220);
                try(var tls=(SSLSocket)context.getSocketFactory().createSocket(socket,host,port,true)) {
                    var parameters=tls.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");tls.setSSLParameters(parameters);tls.startHandshake();streams(tls);
                    send("EHLO damgox2-check.invalid");var after=reply(250);
                    if(after.stream().noneMatch(x->x.toUpperCase(Locale.ROOT).matches(".*AUTH.*\\bPLAIN\\b.*")))throw new IOException();
                    byte[] secret=Files.readAllBytes(Path.of(System.getenv("DAMGOX2_MAIL_PASSWORD_FILE")));
                    if(secret.length==0||secret.length>4096)throw new IOException();
                    String password=new String(secret,StandardCharsets.UTF_8).replaceFirst("\\r?\\n$","");Arrays.fill(secret,(byte)0);
                    send("AUTH PLAIN "+Base64.getEncoder().encodeToString(("\0"+user+"\0"+password).getBytes(StandardCharsets.UTF_8)));reply(235);
                    send("QUIT");reply(221);
                    System.out.println("STARTTLS=PASS certificate-chain=PASS hostname=PASS AUTH=235 message-submitted=false TLS="+tls.getSession().getProtocol());
                }
            }
        } catch(Exception e) {System.err.println("SMTP verified connection failed; protocol and credentials withheld");System.exit(1);}
    }
}
