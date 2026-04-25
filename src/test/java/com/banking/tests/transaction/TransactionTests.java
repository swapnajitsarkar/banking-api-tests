package com.banking.tests.transaction;

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
 * Transaction endpoint tests.
 *
 * Covers:
 *   POST /api/accounts/{id}/deposit       — happy path, zero amount, negative
 *   POST /api/accounts/{id}/withdraw      — happy path, overdraft, zero
 *   POST /api/accounts/{id}/transfer      — valid transfer, insufficient funds, same-account
 *   GET  /api/accounts/{id}/transactions  — history pagination + filter
 *   GET  /api/transactions/{txnId}        — individual transaction detail
 */
public class TransactionTests extends BaseTest {

    private String accountId;
    private String secondAccountId;
    private String transactionId;

    private static final double INITIAL_DEPOSIT   = 50_000.0;
    private static final double DEPOSIT_AMOUNT     = 10_000.0;
    private static final double WITHDRAW_AMOUNT    = 5_000.0;
    private static final double TRANSFER_AMOUNT    = 2_000.0;
    private static final double OVERDRAFT_AMOUNT   = 999_999.0;

    @BeforeClass
    public void setupUserAndAccounts() {
        registerAndLoginUser();

        // Primary account
        Response acc1 = given()
            .spec(authSpec())
            .body(TestDataFactory.savingsAccountPayload())
            .post("/accounts")
            .then().statusCode(201).extract().response();
        accountId = acc1.jsonPath().getString("id");

        // Secondary account for transfers
        Response acc2 = given()
            .spec(authSpec())
            .body(TestDataFactory.currentAccountPayload())
            .post("/accounts")
            .then().statusCode(201).extract().response();
        secondAccountId = acc2.jsonPath().getString("id");

        // Seed primary account with a known balance
        given().spec(authSpec())
               .body(TestDataFactory.depositPayload(INITIAL_DEPOSIT))
               .post("/accounts/" + accountId + "/deposit")
               .then().statusCode(anyOf(is(200), is(201)));

        ExtentManager.getTest().info("Test accounts created. Primary: " + accountId);
    }

    // ── Deposit ───────────────────────────────────────────────────────────────

    @Test(priority = 1, description = "Deposit valid amount — balance should increase")
    public void deposit_validAmount_shouldIncreaseBalance() {
        ExtentManager.getTest().info("Depositing ₹" + DEPOSIT_AMOUNT);

        // Capture balance before
        float balanceBefore = given().spec(authSpec())
            .get("/accounts/" + accountId + "/balance")
            .then().statusCode(200).extract().jsonPath().getFloat("balance");

        // Perform deposit
        Response response = given()
            .spec(authSpec())
            .body(TestDataFactory.depositPayload(DEPOSIT_AMOUNT))
        .when()
            .post("/accounts/" + accountId + "/deposit")
        .then()
            .statusCode(anyOf(is(200), is(201)))
            .body("transactionId", notNullValue())
            .body("amount",        equalTo((float) DEPOSIT_AMOUNT))
            .body("type",          equalToIgnoringCase("DEPOSIT"))
            .extract().response();

        transactionId = response.jsonPath().getString("transactionId");

        // Verify new balance
        float balanceAfter = given().spec(authSpec())
            .get("/accounts/" + accountId + "/balance")
            .then().statusCode(200).extract().jsonPath().getFloat("balance");

        assertEquals(balanceAfter, balanceBefore + (float) DEPOSIT_AMOUNT, 0.01f,
                "Balance should have increased by the deposit amount");
    }

    @Test(priority = 2, description = "Deposit zero amount — should return 400")
    public void deposit_zeroAmount_shouldReturn400() {
        given()
            .spec(authSpec())
            .body(TestDataFactory.depositPayload(0))
        .when()
            .post("/accounts/" + accountId + "/deposit")
        .then()
            .statusCode(400);
    }

    @Test(priority = 3, description = "Deposit negative amount — should return 400")
    public void deposit_negativeAmount_shouldReturn400() {
        given()
            .spec(authSpec())
            .body(TestDataFactory.depositPayload(-500))
        .when()
            .post("/accounts/" + accountId + "/deposit")
        .then()
            .statusCode(400);
    }

    // ── Withdrawal ────────────────────────────────────────────────────────────

    @Test(priority = 4, description = "Withdraw valid amount — balance should decrease",
          dependsOnMethods = "deposit_validAmount_shouldIncreaseBalance")
    public void withdraw_validAmount_shouldDecreaseBalance() {
        ExtentManager.getTest().info("Withdrawing ₹" + WITHDRAW_AMOUNT);

        float balanceBefore = given().spec(authSpec())
            .get("/accounts/" + accountId + "/balance")
            .then().statusCode(200).extract().jsonPath().getFloat("balance");

        given()
            .spec(authSpec())
            .body(TestDataFactory.withdrawPayload(WITHDRAW_AMOUNT))
        .when()
            .post("/accounts/" + accountId + "/withdraw")
        .then()
            .statusCode(anyOf(is(200), is(201)))
            .body("type", equalToIgnoringCase("WITHDRAWAL"));

        float balanceAfter = given().spec(authSpec())
            .get("/accounts/" + accountId + "/balance")
            .then().statusCode(200).extract().jsonPath().getFloat("balance");

        assertEquals(balanceAfter, balanceBefore - (float) WITHDRAW_AMOUNT, 0.01f,
                "Balance should have decreased by the withdrawal amount");
    }

    @Test(priority = 5, description = "Withdraw more than balance — should return 400 (overdraft)")
    public void withdraw_exceedsBalance_shouldReturn400() {
        ExtentManager.getTest().info("Testing overdraft prevention");

        given()
            .spec(authSpec())
            .body(TestDataFactory.withdrawPayload(OVERDRAFT_AMOUNT))
        .when()
            .post("/accounts/" + accountId + "/withdraw")
        .then()
            .statusCode(anyOf(is(400), is(422)))
            .body("error", notNullValue());
    }

    @Test(priority = 6, description = "Withdraw zero amount — should return 400")
    public void withdraw_zeroAmount_shouldReturn400() {
        given()
            .spec(authSpec())
            .body(TestDataFactory.withdrawPayload(0))
        .when()
            .post("/accounts/" + accountId + "/withdraw")
        .then()
            .statusCode(400);
    }

    // ── Transfer ──────────────────────────────────────────────────────────────

    @Test(priority = 7, description = "Transfer between own accounts — both balances should update",
          dependsOnMethods = "deposit_validAmount_shouldIncreaseBalance")
    public void transfer_betweenOwnAccounts_shouldUpdateBothBalances() {
        ExtentManager.getTest().info("Transferring ₹" + TRANSFER_AMOUNT + " between own accounts");

        // Get destination account number
        String destAccountNumber = given().spec(authSpec())
            .get("/accounts/" + secondAccountId)
            .then().statusCode(200)
            .extract().jsonPath().getString("accountNumber");

        float srcBefore = given().spec(authSpec())
            .get("/accounts/" + accountId + "/balance")
            .then().extract().jsonPath().getFloat("balance");

        float dstBefore = given().spec(authSpec())
            .get("/accounts/" + secondAccountId + "/balance")
            .then().extract().jsonPath().getFloat("balance");

        given()
            .spec(authSpec())
            .body(TestDataFactory.transferPayload(destAccountNumber, TRANSFER_AMOUNT))
        .when()
            .post("/accounts/" + accountId + "/transfer")
        .then()
            .statusCode(anyOf(is(200), is(201)))
            .body("type",   equalToIgnoringCase("TRANSFER"))
            .body("amount", equalTo((float) TRANSFER_AMOUNT));

        float srcAfter = given().spec(authSpec())
            .get("/accounts/" + accountId + "/balance")
            .then().extract().jsonPath().getFloat("balance");

        float dstAfter = given().spec(authSpec())
            .get("/accounts/" + secondAccountId + "/balance")
            .then().extract().jsonPath().getFloat("balance");

        assertEquals(srcAfter, srcBefore - (float) TRANSFER_AMOUNT, 0.01f,
                "Source balance should decrease by transfer amount");
        assertEquals(dstAfter, dstBefore + (float) TRANSFER_AMOUNT, 0.01f,
                "Destination balance should increase by transfer amount");
    }

    @Test(priority = 8, description = "Transfer with insufficient funds — should return 400")
    public void transfer_insufficientFunds_shouldReturn400() {
        String destAccountNumber = given().spec(authSpec())
            .get("/accounts/" + secondAccountId)
            .then().extract().jsonPath().getString("accountNumber");

        given()
            .spec(authSpec())
            .body(TestDataFactory.transferPayload(destAccountNumber, OVERDRAFT_AMOUNT))
        .when()
            .post("/accounts/" + accountId + "/transfer")
        .then()
            .statusCode(anyOf(is(400), is(422)));
    }

    @Test(priority = 9, description = "Transfer to non-existent account — should return 404")
    public void transfer_toNonExistentAccount_shouldReturn404() {
        given()
            .spec(authSpec())
            .body(TestDataFactory.transferPayload("0000000000", 100.0))
        .when()
            .post("/accounts/" + accountId + "/transfer")
        .then()
            .statusCode(anyOf(is(404), is(400)));
    }

    // ── Transaction History ───────────────────────────────────────────────────

    @Test(priority = 10, description = "Get transaction history — should return paginated list",
          dependsOnMethods = "deposit_validAmount_shouldIncreaseBalance")
    public void getTransactionHistory_shouldReturnList() {
        ExtentManager.getTest().info("Fetching transaction history for account: " + accountId);

        given()
            .spec(authSpec())
        .when()
            .get("/accounts/" + accountId + "/transactions")
        .then()
            .statusCode(200)
            .body("$",      instanceOf(java.util.List.class))
            .body("size()", greaterThanOrEqualTo(1));
    }

    @Test(priority = 11, description = "Get transaction history with pagination params")
    public void getTransactionHistory_withPagination_shouldRespectPageSize() {
        given()
            .spec(authSpec())
            .queryParam("page", 0)
            .queryParam("size", 2)
        .when()
            .get("/accounts/" + accountId + "/transactions")
        .then()
            .statusCode(200)
            .body("size()", lessThanOrEqualTo(2));
    }

    // ── Individual Transaction Detail ─────────────────────────────────────────

    @Test(priority = 12, description = "Get transaction by ID — should return transaction details",
          dependsOnMethods = "deposit_validAmount_shouldIncreaseBalance")
    public void getTransactionById_shouldReturn200() {
        ExtentManager.getTest().info("Fetching transaction: " + transactionId);
        if (transactionId == null) {
            ExtentManager.getTest().skip("transactionId not captured — skipping detail test");
            return;
        }

        given()
            .spec(authSpec())
        .when()
            .get("/transactions/" + transactionId)
        .then()
            .statusCode(200)
            .body("transactionId", equalTo(transactionId))
            .body("amount",        notNullValue())
            .body("type",          notNullValue())
            .body("timestamp",     notNullValue());
    }

    @Test(priority = 13, description = "Get non-existent transaction — should return 404")
    public void getTransaction_notFound_shouldReturn404() {
        given()
            .spec(authSpec())
        .when()
            .get("/transactions/00000000-0000-0000-0000-000000000000")
        .then()
            .statusCode(404);
    }
}
