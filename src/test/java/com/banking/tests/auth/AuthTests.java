package com.banking.tests.auth;

import com.banking.base.BaseTest;
import com.banking.config.Config;
import com.banking.utils.ExtentManager;
import com.banking.utils.TestDataFactory;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.testng.Assert.*;

/**
 * Authentication endpoint tests.
 *
 * Covers:
 *   POST /api/auth/register  — happy path + duplicate + validation errors
 *   POST /api/auth/login     — happy path + wrong creds + missing fields
 *   GET  /api/auth/me        — token introspection
 *   POST /api/auth/logout    — token invalidation
 */
public class AuthTests extends BaseTest {

    // ── Registration ─────────────────────────────────────────────────────────

    @Test(priority = 1, description = "Register a new user — should return 201 with token")
    public void registerNewUser_shouldReturn201() {
        ExtentManager.getTest().info("Registering new user with valid payload");
        Map<String, Object> payload = TestDataFactory.newUserPayload();

        given()
            .spec(baseSpec)
            .body(payload)
        .when()
            .post("/auth/register")
        .then()
            .statusCode(201)
            .body("token",     notNullValue())
            .body("user.email", equalTo(payload.get("email")));

        ExtentManager.getTest().pass("Registration returned 201 with JWT token");
    }

    @Test(priority = 2, description = "Register with duplicate email — should return 409")
    public void registerDuplicateEmail_shouldReturn409() {
        ExtentManager.getTest().info("Testing duplicate email registration");
        Map<String, Object> payload = TestDataFactory.newUserPayload();

        // First registration — should succeed
        given().spec(baseSpec).body(payload).post("/auth/register")
               .then().statusCode(201);

        // Second registration — same email → conflict
        given()
            .spec(baseSpec)
            .body(payload)
        .when()
            .post("/auth/register")
        .then()
            .statusCode(409)
            .body("error", containsStringIgnoringCase("email"))
            .body("error", containsStringIgnoringCase("already"));
    }

    @Test(priority = 3, description = "Register with missing required fields — should return 400")
    public void registerMissingFields_shouldReturn400() {
        ExtentManager.getTest().info("Testing registration with empty body");

        given()
            .spec(baseSpec)
            .body("{}")
        .when()
            .post("/auth/register")
        .then()
            .statusCode(400)
            .body("errors", notNullValue());
    }

    @Test(priority = 4, description = "Register with invalid email format — should return 400")
    public void registerInvalidEmail_shouldReturn400() {
        Map<String, Object> payload = TestDataFactory.newUserPayload();
        payload.put("email", "not-an-email");

        given()
            .spec(baseSpec)
            .body(payload)
        .when()
            .post("/auth/register")
        .then()
            .statusCode(400);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test(priority = 5, description = "Login with valid credentials — should return 200 with JWT")
    public void loginValidCredentials_shouldReturn200WithToken() {
        ExtentManager.getTest().info("Logging in with admin credentials");

        Response response = given()
            .spec(baseSpec)
            .body(TestDataFactory.loginPayload(Config.ADMIN_EMAIL, Config.ADMIN_PASSWORD))
        .when()
            .post("/auth/login")
        .then()
            .statusCode(200)
            .body("token",          notNullValue())
            .body("token.length()", greaterThan(20))
            .extract().response();

        String token = response.jsonPath().getString("token");
        assertNotNull(token, "JWT token must not be null");
        assertTrue(token.split("\\.").length == 3, "Token should be a valid 3-part JWT");
    }

    @Test(priority = 6, description = "Login with wrong password — should return 401")
    public void loginWrongPassword_shouldReturn401() {
        given()
            .spec(baseSpec)
            .body(TestDataFactory.loginPayload(Config.ADMIN_EMAIL, "WrongPass999!"))
        .when()
            .post("/auth/login")
        .then()
            .statusCode(401)
            .body("error", notNullValue());
    }

    @Test(priority = 7, description = "Login with non-existent email — should return 401 or 404")
    public void loginUnknownEmail_shouldReturnError() {
        given()
            .spec(baseSpec)
            .body(TestDataFactory.loginPayload("ghost@unknown.com", "SomePass@1"))
        .when()
            .post("/auth/login")
        .then()
            .statusCode(anyOf(is(401), is(404)));
    }

    @Test(priority = 8, description = "Login with empty body — should return 400")
    public void loginEmptyBody_shouldReturn400() {
        given()
            .spec(baseSpec)
            .body("{}")
        .when()
            .post("/auth/login")
        .then()
            .statusCode(400);
    }

    // ── Token / Me ────────────────────────────────────────────────────────────

    @Test(priority = 9, description = "GET /auth/me with valid token — should return 200 with user profile")
    public void getMe_withValidToken_shouldReturn200() {
        ExtentManager.getTest().info("Verifying /auth/me endpoint with admin token");

        given()
            .spec(authSpec())
        .when()
            .get("/auth/me")
        .then()
            .statusCode(200)
            .body("email",     notNullValue())
            .body("firstName", notNullValue());
    }

    @Test(priority = 10, description = "GET /auth/me without token — should return 401")
    public void getMe_withoutToken_shouldReturn401() {
        given()
            .spec(baseSpec)
        .when()
            .get("/auth/me")
        .then()
            .statusCode(401);
    }

    @Test(priority = 11, description = "GET /auth/me with malformed token — should return 401")
    public void getMe_withMalformedToken_shouldReturn401() {
        given()
            .spec(baseSpec)
            .header("Authorization", "Bearer this.is.garbage")
        .when()
            .get("/auth/me")
        .then()
            .statusCode(401);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test(priority = 12, description = "Logout with valid token — should invalidate session")
    public void logout_withValidToken_shouldReturn200() {
        // Register fresh user so we can safely log them out
        registerAndLoginUser();

        given()
            .spec(authSpec())
        .when()
            .post("/auth/logout")
        .then()
            .statusCode(anyOf(is(200), is(204)));

        clearUserToken();
    }
}
