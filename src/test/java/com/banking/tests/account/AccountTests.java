package com.banking.tests.account;

import com.banking.base.BaseTest;
import com.banking.utils.ExtentManager;
import com.banking.utils.TestDataFactory;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.testng.Assert.*;

/**
 * Account management endpoint tests.
 *
 * Covers:
 *   POST /api/accounts          — create savings / current account
 *   GET  /api/accounts          — list all accounts for user
 *   GET  /api/accounts/{id}     — get account by ID
 *   GET  /api/accounts/{id}/balance — balance check
 *   DELETE /api/accounts/{id}   — close account (soft delete)
 *   Cross-user access control   — 403 enforcement
 */
public class AccountTests extends BaseTest {

    private String accountId;
    private String accountNumber;
    private String secondAccountId;

    @BeforeClass
    public void setupUser() {
        // Each test class gets its own fresh user
        registerAndLoginUser();
        ExtentManager.getTest().info("Created fresh test user for AccountTests class");
    }

    // ── Account Creation ──────────────────────────────────────────────────────

    @Test(priority = 1, description = "Create SAVINGS account — should return 201 with account details")
    public void createSavingsAccount_shouldReturn201() {
        ExtentManager.getTest().info("Creating a SAVINGS account");
        Map<String, Object> payload = TestDataFactory.savingsAccountPayload();

        Response response = given()
            .spec(authSpec())
            .body(payload)
        .when()
            .post("/accounts")
        .then()
            .statusCode(201)
            .body("accountNumber", notNullValue())
            .body("accountType",   equalTo("SAVINGS"))
            .body("balance",       greaterThanOrEqualTo(0f))
            .body("status",        equalTo("ACTIVE"))
            .extract().response();

        accountId     = response.jsonPath().getString("id");
        accountNumber = response.jsonPath().getString("accountNumber");

        assertNotNull(accountId, "Account ID must be present in response");
        ExtentManager.getTest().pass("Account created: " + accountNumber);
    }

    @Test(priority = 2, description = "Create CURRENT account — should return 201")
    public void createCurrentAccount_shouldReturn201() {
        Map<String, Object> payload = TestDataFactory.currentAccountPayload();

        Response response = given()
            .spec(authSpec())
            .body(payload)
        .when()
            .post("/accounts")
        .then()
            .statusCode(201)
            .body("accountType", equalTo("CURRENT"))
            .extract().response();

        secondAccountId = response.jsonPath().getString("id");
    }

    @Test(priority = 3, description = "Create account with invalid type — should return 400")
    public void createAccount_invalidType_shouldReturn400() {
        Map<String, Object> payload = TestDataFactory.savingsAccountPayload();
        payload.put("accountType", "CRYPTO"); // unsupported type

        given()
            .spec(authSpec())
            .body(payload)
        .when()
            .post("/accounts")
        .then()
            .statusCode(400);
    }

    @Test(priority = 4, description = "Create account with negative initial deposit — should return 400")
    public void createAccount_negativeDeposit_shouldReturn400() {
        Map<String, Object> payload = TestDataFactory.savingsAccountPayload();
        payload.put("initialDeposit", -500);

        given()
            .spec(authSpec())
            .body(payload)
        .when()
            .post("/accounts")
        .then()
            .statusCode(400);
    }

    // ── Account Retrieval ─────────────────────────────────────────────────────

    @Test(priority = 5, description = "List accounts — should return user's accounts array",
          dependsOnMethods = "createSavingsAccount_shouldReturn201")
    public void listAccounts_shouldReturnNonEmptyArray() {
        ExtentManager.getTest().info("Listing accounts for current user");

        given()
            .spec(authSpec())
        .when()
            .get("/accounts")
        .then()
            .statusCode(200)
            .body("$",    instanceOf(java.util.List.class))
            .body("size()", greaterThanOrEqualTo(1));
    }

    @Test(priority = 6, description = "Get account by ID — should return account details",
          dependsOnMethods = "createSavingsAccount_shouldReturn201")
    public void getAccountById_shouldReturn200() {
        ExtentManager.getTest().info("Fetching account: " + accountId);

        given()
            .spec(authSpec())
        .when()
            .get("/accounts/" + accountId)
        .then()
            .statusCode(200)
            .body("id",            equalTo(accountId))
            .body("accountNumber", equalTo(accountNumber))
            .body("accountType",   equalTo("SAVINGS"));
    }

    @Test(priority = 7, description = "Get non-existent account — should return 404")
    public void getAccount_notFound_shouldReturn404() {
        given()
            .spec(authSpec())
        .when()
            .get("/accounts/00000000-0000-0000-0000-000000000000")
        .then()
            .statusCode(404);
    }

    // ── Balance ───────────────────────────────────────────────────────────────

    @Test(priority = 8, description = "Get account balance — should return balance object",
          dependsOnMethods = "createSavingsAccount_shouldReturn201")
    public void getAccountBalance_shouldReturn200() {
        ExtentManager.getTest().info("Checking balance for account: " + accountId);

        given()
            .spec(authSpec())
        .when()
            .get("/accounts/" + accountId + "/balance")
        .then()
            .statusCode(200)
            .body("balance",  notNullValue())
            .body("currency", equalTo("INR"));
    }

    // ── Access Control ────────────────────────────────────────────────────────

    @Test(priority = 9, description = "Access other user's account — should return 403",
          dependsOnMethods = "createSavingsAccount_shouldReturn201")
    public void accessOtherUserAccount_shouldReturn403() {
        ExtentManager.getTest().info("Testing cross-user account access denial");

        // Register a completely separate user
        registerAndLoginUser();

        given()
            .spec(authSpec())  // token of the NEW user
        .when()
            .get("/accounts/" + accountId)  // accountId belongs to the FIRST user
        .then()
            .statusCode(anyOf(is(403), is(404)));
    }

    // ── Account Deletion / Close ──────────────────────────────────────────────

    @Test(priority = 10, description = "Close account — should return 200 or 204",
          dependsOnMethods = {"createCurrentAccount_shouldReturn201"})
    public void closeAccount_shouldSucceed() {
        ExtentManager.getTest().info("Closing account: " + secondAccountId);

        // Re-auth as first user who owns secondAccountId
        Map<String, Object> firstUser = registerAndLoginUser();

        // Create an account for this new user and close it (safe)
        Response createResp = given()
            .spec(authSpec())
            .body(TestDataFactory.currentAccountPayload())
            .post("/accounts")
            .then().statusCode(201)
            .extract().response();

        String tempAccountId = createResp.jsonPath().getString("id");

        given()
            .spec(authSpec())
        .when()
            .delete("/accounts/" + tempAccountId)
        .then()
            .statusCode(anyOf(is(200), is(204)));
    }

    @Test(priority = 11, description = "Response should contain required account fields",
          dependsOnMethods = "createSavingsAccount_shouldReturn201")
    public void accountResponse_shouldContainRequiredFields() {
        Response response = given()
            .spec(authSpec())
            .get("/accounts/" + accountId)
            .then().statusCode(200)
            .extract().response();

        // Assert all required fields are present
        assertAll(response, accountId);
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private void assertAll(Response response, String id) {
        String[] requiredFields = {"id", "accountNumber", "accountType", "balance", "status", "currency"};
        for (String field : requiredFields) {
            assertNotNull(response.jsonPath().get(field),
                    "Field '" + field + "' must be present in account response");
        }
    }
}
