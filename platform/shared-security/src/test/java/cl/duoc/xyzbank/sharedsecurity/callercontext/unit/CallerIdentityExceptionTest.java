package cl.duoc.xyzbank.sharedsecurity.callercontext.unit;

import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerIdentityException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("The CallerIdentityException")
class CallerIdentityExceptionTest {

    /*
     * Cases:
     * 1. invalid() sets Type.INVALID and preserves the message
     * 2. forbidden() sets Type.FORBIDDEN and preserves the message
     */

    @Test
    @DisplayName("invalid() sets Type.INVALID and preserves the message")
    void invalidSetsTypeInvalidAndPreservesTheMessage() {
        CallerIdentityException exception = CallerIdentityException.invalid("missing customer id");

        assertEquals(CallerIdentityException.Type.INVALID, exception.getType());
        assertEquals("missing customer id", exception.getMessage());
    }

    @Test
    @DisplayName("forbidden() sets Type.FORBIDDEN and preserves the message")
    void forbiddenSetsTypeForbiddenAndPreservesTheMessage() {
        CallerIdentityException exception = CallerIdentityException.forbidden("channel not allowed");

        assertEquals(CallerIdentityException.Type.FORBIDDEN, exception.getType());
        assertEquals("channel not allowed", exception.getMessage());
    }
}
