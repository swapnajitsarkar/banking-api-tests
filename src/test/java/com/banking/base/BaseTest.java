package com.banking.base;

import com.banking.config.Config;
import com.banking.utils.ExtentManager;
import com.banking.utils.TestDataFactory;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import java.lang.reflect.Method;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Base class for all API test classes.
 * Handles:
 *   - RequestSpecification with auth + content type
 *   - JWT token acquisition and thread-local caching
 *   - Extent Reports lifecycle (start / pass / fail / skip)
 *   - Admin token bootstrapped once per suite
 */
public abstract class BaseTest {

    // Shared admin token (acquired once per suite run)
    protected static String adminToken;

    // Per-test user credentials stored as thread-locals for parallel safety
    private static final ThreadLocal<String> userToken = new ThreadLocal<>();

    // Base RequestSpec (no auth) — used for login/register calls
    protected static RequestSpecification baseSpec;

    // Auth RequestSpec — injects Bearer token automatically
    protected RequestSpecification authSpec() {
        String token = userToken.get() != null ? userToken.get() : adminToken;
        return new RequestSpecBuilder()
                .addRequestSpecification(baseSpec)
                .addHeader("Authorization", "Bearer " + token)
                .build();
    }

    // ── Suite-level setup/teardown ─────────────────────────────────────────

    @BeforeSuite(alwaysRun = true)
    public void globalSetup() {
        RestAssured.baseURI = Config.BASE_URL;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        baseSpec = new RequestSpecBuilder()
                .setBaseUri(Config.BASE_URL)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addFilter(new RequestLoggingFilter())
                .addFilter(new ResponseLoggingFilter())
                .build();

        // Bootstrap admin token
        adminToken = acquireToken(Config.ADMIN_EMAIL, Config.ADMIN_PASSWORD);

        ExtentManager.getInstance(); // initialise reporter
    }

    @AfterSuite(alwaysRun = true)
    public void globalTeardown() {
        ExtentManager.flush();
    }

    // ── Method-level reporting ─────────────────────────────────────────────

    @BeforeMethod(alwaysRun = true)
    public void startTest(Method method) {
        var extentTest = ExtentManager.getInstance()
                .createTest(method.getName(),
                        method.getDeclaringClass().getSimpleName());
        ExtentManager.setTest(extentTest);
    }

    @AfterMethod(alwaysRun = true)
    public void endTest(ITestResult result) {
        var extentTest = ExtentManager.getTest();
        switch (result.getStatus()) {
            case ITestResult.FAILURE ->
                    extentTest.fail(result.getThrowable());
            case ITestResult.SKIP ->
                    extentTest.skip("Test skipped: " + result.getThrowable().getMessage());
            default ->
                    extentTest.pass("Test passed");
        }
        ExtentManager.removeTest();
    }

    // ── Token helpers ──────────────────────────────────────────────────────

    /**
     * Acquires a JWT for the given credentials.
     * Returns the raw token string, or empty string on failure.
     */
    protected String acquireToken(String email, String password) {
        try {
            Response response = given()
                    .spec(baseSpec)
                    .body(TestDataFactory.loginPayload(email, password))
                    .post("/auth/login");

            if (response.statusCode() == 200) {
                return response.jsonPath().getString("token");
            }
        } catch (Exception e) {
            System.err.println("[BaseTest] Token acquisition failed: " + e.getMessage());
        }
        return "";
    }

    /**
     * Registers a new user and returns their JWT.
     * Stores in thread-local so authSpec() picks it up automatically.
     */
    protected Map<String, Object> registerAndLoginUser() {
        Map<String, Object> userData = TestDataFactory.newUserPayload();

        // Register
        given().spec(baseSpec).body(userData).post("/auth/register").then()
                .statusCode(201);

        // Login
        String token = acquireToken(
                userData.get("email").toString(),
                userData.get("password").toString());
        userToken.set(token);

        return userData;
    }

    protected void setUserToken(String token) {
        userToken.set(token);
    }

    protected void clearUserToken() {
        userToken.remove();
    }
}
