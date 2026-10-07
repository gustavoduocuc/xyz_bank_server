package cl.duoc.xyzbank.coreservice.auth.e2e;

import cl.duoc.xyzbank.coredomain.cards.domain.entities.Card;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.CardRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.sharedsecurity.callercontext.PinHasher;
import cl.duoc.xyzbank.testsupport.AbstractCoreServiceIT;
import cl.duoc.xyzbank.testsupport.TestAccessTokens;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The PIN verification endpoint")
class PinVerificationControllerE2ETest extends AbstractCoreServiceIT {

    /*
     * Cases:
     * 1. Correct PIN returns 200
     * 2. Incorrect PIN returns 401
     * 3. Third consecutive incorrect PIN locks the card and returns 423
     * 4. Locked card rejects a correct PIN with 423
     * 5. No case's response body ever contains the submitted PIN
     * 6. No case's log output ever contains the submitted PIN
     */

    private static final String PIN = "1234";
    private static final String WRONG_PIN = "9999";

    private final PinHasher hasher = new PinHasher();
    private ListAppender<ILoggingEvent> logAppender;

    @Autowired
    private CardRepository cardRepository;

    @BeforeEach
    void configureRestAssuredAndLogCapture() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logAppender);
    }

    @AfterEach
    void detachLogCapture() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logAppender);
    }

    private record SeededCard(Id cardNumber, Id customerId) {
    }

    private SeededCard seedCard(int consecutiveFailures, boolean locked) {
        Id customerId = Id.generate();
        Id cardNumber = Id.generate();
        cardRepository.save(Card.create(cardNumber, customerId, hasher.hash(PIN), consecutiveFailures, locked, 0L));
        return new SeededCard(cardNumber, customerId);
    }

    private Response verify(int tlsPort, String cardNumber, String pin) {
        return given()
                .relaxedHTTPSValidation()
                .baseUri("https://localhost:" + tlsPort)
                .header("Authorization", "Bearer " + TestAccessTokens.atm())
                .contentType("application/json")
                .body("{\"cardNumber\":\"" + cardNumber + "\",\"pin\":\"" + pin + "\"}")
                .when()
                .post("/internal/auth/atm/pin-verifications");
    }

    @Test
    @DisplayName("correct pin returns 200 with the owning customer id")
    void correctPinReturns200() {
        SeededCard seededCard = seedCard(0, false);

        Response response = verify(8453, seededCard.cardNumber().getValue(), PIN);

        response.then().statusCode(200).body("customerId", org.hamcrest.Matchers.equalTo(seededCard.customerId().getValue()));
        assertPinNeverLeaked(response);
    }

    @Test
    @DisplayName("incorrect pin returns 401")
    void incorrectPinReturns401() {
        SeededCard seededCard = seedCard(0, false);

        Response response = verify(8453, seededCard.cardNumber().getValue(), WRONG_PIN);

        response.then().statusCode(401);
        assertPinNeverLeaked(response);
    }

    @Test
    @DisplayName("third consecutive incorrect pin locks the card and returns 423")
    void thirdConsecutiveIncorrectPinLocksCardAndReturns423() {
        SeededCard seededCard = seedCard(2, false);

        Response response = verify(8453, seededCard.cardNumber().getValue(), WRONG_PIN);

        response.then().statusCode(423);
        assertTrue(cardRepository.findByCardNumber(seededCard.cardNumber()).orElseThrow().isLocked());
        assertPinNeverLeaked(response);
    }

    @Test
    @DisplayName("locked card rejects a correct pin with 423")
    void lockedCardRejectsCorrectPinWith423() {
        SeededCard seededCard = seedCard(3, true);

        Response response = verify(8453, seededCard.cardNumber().getValue(), PIN);

        response.then().statusCode(423);
        assertPinNeverLeaked(response);
    }

    private void assertPinNeverLeaked(Response response) {
        String body = response.getBody().asString();
        assertFalse(body != null && (body.contains(PIN) || body.contains(WRONG_PIN)));
        boolean pinInLogs = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message != null && (message.contains(PIN) || message.contains(WRONG_PIN)));
        assertFalse(pinInLogs, "expected no log line to contain the submitted PIN");
    }
}
