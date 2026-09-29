package cl.duoc.xyzbank.authserver.shared.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The auth-server transport")
class AuthServerTlsE2ETest extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. Serves HTTPS with a certificate that verifies against the dev CA and names both
     *    the browser host (localhost) and the Docker-network host (auth-server)
     * 2. Refuses a plain HTTP connection on the same port
     */

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("serves HTTPS with a dev-CA certificate valid for localhost and auth-server")
    void servesHttpsWithADevCaCertificateValidForLocalhostAndAuthServer() throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection)
                URI.create("https://localhost:" + port + "/actuator/health").toURL().openConnection();
        connection.setSSLSocketFactory(devCaTrustingContext().getSocketFactory());

        assertEquals(200, connection.getResponseCode());
        X509Certificate serverCertificate = (X509Certificate) connection.getServerCertificates()[0];
        List<String> dnsNames = serverCertificate.getSubjectAlternativeNames().stream()
                .map(name -> Objects.toString(name.get(1)))
                .toList();
        assertTrue(dnsNames.containsAll(List.of("localhost", "auth-server")), dnsNames.toString());
    }

    @Test
    @DisplayName("refuses a plain HTTP connection on the same port")
    void refusesAPlainHttpConnectionOnTheSamePort() throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(2000);
            socket.getOutputStream().write("GET /actuator/health HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes());
            socket.getOutputStream().flush();
            String statusLine;
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                statusLine = reader.readLine();
            } catch (IOException connectionRejected) {
                statusLine = null;
            }
            assertTrue(
                    statusLine == null || !statusLine.contains("200"),
                    "expected the plain HTTP request to be rejected, but got: " + statusLine);
        }
    }

    private static SSLContext devCaTrustingContext() throws Exception {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (InputStream inputStream = AuthServerTlsE2ETest.class.getResourceAsStream("/tls/truststore.p12")) {
            trustStore.load(inputStream, "xyzbank-dev".toCharArray());
        }
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers.getTrustManagers(), null);
        return context;
    }
}
