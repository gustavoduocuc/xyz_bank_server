package cl.duoc.xyzbank.coreservice.auth.e2e;

import cl.duoc.xyzbank.testsupport.AbstractCoreServiceIT;
import io.restassured.RestAssured;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("core-service's dual HTTP/TLS connectors")
class PinVerificationTlsConnectorTest extends AbstractCoreServiceIT {

    /*
     * Cases:
     * 1. Every other endpoint remains reachable over plain HTTP on the primary connector
     * 2. The additional connector accepts a TLS connection on its own port
     */

    private static final TrustManager TRUST_ALL = new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    @LocalServerPort
    private int primaryPort;

    @Test
    @DisplayName("every other endpoint remains reachable over plain http on the primary connector")
    void primaryConnectorStaysPlainHttp() {
        RestAssured.port = primaryPort;

        given().when().get("/actuator/health").then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("the additional connector accepts a tls connection on its own port")
    void additionalConnectorAcceptsTlsConnection() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] {TRUST_ALL}, new SecureRandom());

        try (SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket("localhost", 8453)) {
            socket.startHandshake();
        }
    }
}
