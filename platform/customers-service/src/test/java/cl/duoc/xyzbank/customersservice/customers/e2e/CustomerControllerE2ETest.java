package cl.duoc.xyzbank.customersservice.customers.e2e;

import cl.duoc.xyzbank.customersservice.testsupport.AbstractPostgresIT;
import cl.duoc.xyzbank.customersservice.testsupport.TestTokens;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;

@DisplayName("The customers endpoints over HTTP")
class CustomerControllerE2ETest extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. GET returns the demo customer to its own web token
     * 2. GET returns 404 for an unknown customer
     * 3. GET returns 400 for a malformed id
     * 4. GET returns 404 when a web token asks for another customer
     * 5. POST creates a customer with 201
     * 6. POST with a repeated Idempotency-Key returns the first customer
     * 7. POST returns 400 without an Idempotency-Key or with an invalid body
     * 8. PATCH updates the contact details and returns version 1
     * 9. PATCH returns 409 for a stale version
     * 10. PATCH returns 404 for an unknown customer
     * 11. Requests without a token get 401
     * 12. A web token gets 403 on POST and PATCH
     */

    private static final String DEMO_CUSTOMER = "11111111-1111-1111-1111-111111111111";
    private static final Map<String, String> NEW_CUSTOMER = Map.of(
            "fullName", "Jane Doe", "email", "jane@xyzbank.cl", "phone", "+56911111111", "address", "Street 1");

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("GET returns the demo customer to its own web token")
    void getReturnsTheDemoCustomerToItsOwnWebToken() {
        as(TestTokens.web(DEMO_CUSTOMER)).get("/internal/customers/{id}", DEMO_CUSTOMER)
                .then().statusCode(200)
                .body("id", equalTo(DEMO_CUSTOMER))
                .body("fullName", equalTo("Ana Perez"))
                .body("email", equalTo("ana.perez@xyzbank.cl"));
    }

    @Test
    @DisplayName("GET returns 404 for an unknown customer")
    void getReturns404ForAnUnknownCustomer() {
        as(TestTokens.customersAdmin()).get("/internal/customers/{id}", UUID.randomUUID())
                .then().statusCode(404).contentType("application/problem+json");
    }

    @Test
    @DisplayName("GET returns 400 for a malformed id")
    void getReturns400ForAMalformedId() {
        as(TestTokens.customersAdmin()).get("/internal/customers/not-a-uuid").then().statusCode(400);
    }

    @Test
    @DisplayName("GET returns 404 when a web token asks for another customer")
    void getReturns404WhenAWebTokenAsksForAnotherCustomer() {
        as(TestTokens.web(UUID.randomUUID().toString())).get("/internal/customers/{id}", DEMO_CUSTOMER)
                .then().statusCode(404);
    }

    @Test
    @DisplayName("POST creates a customer with 201")
    void postCreatesACustomerWith201() {
        String id = create("key-" + UUID.randomUUID()).then().statusCode(201)
                .body("fullName", equalTo("Jane Doe"))
                .body("version", equalTo(0))
                .extract().path("id");

        as(TestTokens.customersAdmin()).get("/internal/customers/{id}", id)
                .then().statusCode(200).body("email", equalTo("jane@xyzbank.cl"));
    }

    @Test
    @DisplayName("POST with a repeated Idempotency-Key returns the first customer")
    void postWithARepeatedIdempotencyKeyReturnsTheFirstCustomer() {
        String key = "key-" + UUID.randomUUID();
        String firstId = create(key).then().statusCode(201).extract().path("id");

        create(key).then().statusCode(201).body("id", equalTo(firstId));
    }

    @Test
    @DisplayName("POST returns 400 without an Idempotency-Key or with an invalid body")
    void postReturns400WithoutAnIdempotencyKeyOrWithAnInvalidBody() {
        as(TestTokens.customersAdmin()).contentType(ContentType.JSON).body(NEW_CUSTOMER)
                .post("/internal/customers").then().statusCode(400);
        as(TestTokens.customersAdmin()).contentType(ContentType.JSON).header("Idempotency-Key", "key-" + UUID.randomUUID())
                .body(Map.of("fullName", "Jane Doe", "email", "not-an-email"))
                .post("/internal/customers").then().statusCode(400);
    }

    @Test
    @DisplayName("PATCH updates the contact details and returns version 1")
    void patchUpdatesTheContactDetailsAndReturnsVersion1() {
        String id = create("key-" + UUID.randomUUID()).then().extract().path("id");

        patch(id, Map.of("email", "new@xyzbank.cl", "phone", "+56922222222", "version", 0))
                .then().statusCode(200)
                .body("email", equalTo("new@xyzbank.cl"))
                .body("phone", equalTo("+56922222222"))
                .body("fullName", equalTo("Jane Doe"))
                .body("version", equalTo(1));
    }

    @Test
    @DisplayName("PATCH returns 409 for a stale version")
    void patchReturns409ForAStaleVersion() {
        String id = create("key-" + UUID.randomUUID()).then().extract().path("id");
        patch(id, Map.of("email", "first@xyzbank.cl", "version", 0)).then().statusCode(200);

        patch(id, Map.of("email", "second@xyzbank.cl", "version", 0))
                .then().statusCode(409).contentType("application/problem+json");
    }

    @Test
    @DisplayName("PATCH returns 404 for an unknown customer")
    void patchReturns404ForAnUnknownCustomer() {
        patch(UUID.randomUUID().toString(), Map.of("email", "a@xyzbank.cl", "version", 0)).then().statusCode(404);
    }

    @Test
    @DisplayName("requests without a token get 401")
    void requestsWithoutATokenGet401() {
        RestAssured.given().get("/internal/customers/{id}", DEMO_CUSTOMER).then().statusCode(401);
        RestAssured.given().contentType(ContentType.JSON).body(NEW_CUSTOMER)
                .post("/internal/customers").then().statusCode(401);
    }

    @Test
    @DisplayName("a web token gets 403 on POST and PATCH")
    void aWebTokenGets403OnPostAndPatch() {
        String webToken = TestTokens.web(DEMO_CUSTOMER);

        as(webToken).contentType(ContentType.JSON).header("Idempotency-Key", "key-" + UUID.randomUUID())
                .body(NEW_CUSTOMER).post("/internal/customers").then().statusCode(403);
        as(webToken).contentType(ContentType.JSON).body(Map.of("email", "x@xyzbank.cl", "version", 0))
                .patch("/internal/customers/{id}", DEMO_CUSTOMER).then().statusCode(403);
    }

    private static RequestSpecification as(String token) {
        return RestAssured.given().auth().oauth2(token);
    }

    private static Response create(String idempotencyKey) {
        return as(TestTokens.customersAdmin()).contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey).body(NEW_CUSTOMER)
                .post("/internal/customers");
    }

    private static Response patch(String id, Map<String, Object> body) {
        return as(TestTokens.customersAdmin()).contentType(ContentType.JSON).body(body)
                .patch("/internal/customers/{id}", id);
    }
}
