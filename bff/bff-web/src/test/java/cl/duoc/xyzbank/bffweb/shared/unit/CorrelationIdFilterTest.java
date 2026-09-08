package cl.duoc.xyzbank.bffweb.shared.unit;

import cl.duoc.xyzbank.bffweb.shared.infrastructure.rest.CorrelationIdFilter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("The correlation id filter")
class CorrelationIdFilterTest {

    /*
     * Cases:
     * 1. Generates a non-blank id when the header is absent
     * 2. Generates a non-blank id when the header is blank
     * 3. Reuses a present non-blank header
     * 4. Stores the value for logging while the request is handled
     * 5. Clears the logging value after the request
     */

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("generates a non-blank id when the header is absent")
    void generatesANonBlankIdWhenTheHeaderIsAbsent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorrelationIdFilter().doFilter(request, response, new MockFilterChain());

        String correlationId = response.getHeader("X-Correlation-Id");
        assertNotNull(correlationId);
        assertFalse(correlationId.isBlank());
    }

    @Test
    @DisplayName("generates a non-blank id when the header is blank")
    void generatesANonBlankIdWhenTheHeaderIsBlank() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorrelationIdFilter().doFilter(request, response, new MockFilterChain());

        String correlationId = response.getHeader("X-Correlation-Id");
        assertNotNull(correlationId);
        assertFalse(correlationId.isBlank());
    }

    @Test
    @DisplayName("reuses a present non-blank header")
    void reusesAPresentNonBlankHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "corr-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorrelationIdFilter().doFilter(request, response, new MockFilterChain());

        assertEquals("corr-1", response.getHeader("X-Correlation-Id"));
    }

    @Test
    @DisplayName("stores the value for logging while the request is handled")
    void storesTheValueForLoggingWhileTheRequestIsHandled() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "corr-2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] captured = new String[1];

        new CorrelationIdFilter().doFilter(request, response, (req, res) -> captured[0] = MDC.get("correlationId"));

        assertEquals("corr-2", captured[0]);
    }

    @Test
    @DisplayName("clears the logging value after the request")
    void clearsTheLoggingValueAfterTheRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "corr-3");

        new CorrelationIdFilter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(MDC.get("correlationId"));
    }
}
