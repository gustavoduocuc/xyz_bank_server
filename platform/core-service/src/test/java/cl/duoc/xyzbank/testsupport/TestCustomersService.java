package cl.duoc.xyzbank.testsupport;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * One stub customers-service for every core-service test context. Registering it in the shared
 * base keeps all contexts' configuration identical, so they are cached and reused instead of
 * each new one starting its own fixed-port PIN connector.
 */
public final class TestCustomersService {

    private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        SERVER.start();
    }

    private TestCustomersService() {
    }

    public static WireMockServer server() {
        return SERVER;
    }

    public static String baseUrl() {
        return SERVER.baseUrl();
    }
}
