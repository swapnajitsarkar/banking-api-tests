package com.banking.utils;

import com.github.javafaker.Faker;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Generates randomised, realistic test data using JavaFaker.
 * Use a fixed seed in the constructor for reproducible runs.
 */
public class TestDataFactory {

    private static final Faker faker = new Faker(new Locale("en-IN"));

    private TestDataFactory() {}

    // ── User payloads ────────────────────────────────────────────────────────

    public static Map<String, Object> newUserPayload() {
        String first = faker.name().firstName();
        String last  = faker.name().lastName();
        Map<String, Object> body = new HashMap<>();
        body.put("firstName",   first);
        body.put("lastName",    last);
        body.put("email",       (first + "." + last + faker.number().digits(4)
                                 + "@testbank.com").toLowerCase());
        body.put("password",    "Test@" + faker.number().digits(4));
        body.put("phone",       "+91" + faker.number().digits(10));
        body.put("dateOfBirth", "1995-06-15");
        body.put("address",     faker.address().fullAddress());
        return body;
    }

    public static Map<String, Object> loginPayload(String email, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("email",    email);
        body.put("password", password);
        return body;
    }

    // ── Account payloads ────────────────────────────────────────────────────

    public static Map<String, Object> savingsAccountPayload() {
        Map<String, Object> body = new HashMap<>();
        body.put("accountType",     "SAVINGS");
        body.put("initialDeposit",  faker.number().numberBetween(1000, 50000));
        body.put("currency",        "INR");
        return body;
    }

    public static Map<String, Object> currentAccountPayload() {
        Map<String, Object> body = new HashMap<>();
        body.put("accountType",    "CURRENT");
        body.put("initialDeposit", faker.number().numberBetween(5000, 100000));
        body.put("currency",       "INR");
        return body;
    }

    // ── Transaction payloads ─────────────────────────────────────────────────

    public static Map<String, Object> depositPayload(double amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount",      amount);
        body.put("currency",    "INR");
        body.put("description", "Test deposit — " + faker.lorem().word());
        return body;
    }

    public static Map<String, Object> withdrawPayload(double amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount",      amount);
        body.put("currency",    "INR");
        body.put("description", "Test withdrawal");
        return body;
    }

    public static Map<String, Object> transferPayload(String toAccountNumber, double amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("toAccountNumber", toAccountNumber);
        body.put("amount",          amount);
        body.put("currency",        "INR");
        body.put("description",     "Test transfer");
        return body;
    }
}
