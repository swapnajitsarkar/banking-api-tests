# 🏦 Digital Banking System — REST Assured API Test Suite

[![API Tests – CI](https://github.com/swapnajitsarkar/banking-api-tests/actions/workflows/api-tests.yml/badge.svg)](https://github.com/swapnajitsarkar/banking-api-tests/actions/workflows/api-tests.yml)
![Java](https://img.shields.io/badge/Java-17-blue?logo=openjdk)
![RestAssured](https://img.shields.io/badge/Rest%20Assured-5.4.0-green)
![TestNG](https://img.shields.io/badge/TestNG-7.9.0-orange)
![Build](https://img.shields.io/badge/build-Maven-red?logo=apachemaven)

A production-grade **API test automation framework** built with **Rest Assured 5 + TestNG**, targeting the [Digital Banking System](https://github.com/swapnajitsarkar/Banking-System) Spring Boot backend.

---

## 📌 Project Overview

This suite validates every critical REST endpoint of the Digital Banking System — authentication, account management, and financial transactions — covering both happy-path flows and a comprehensive set of negative/edge cases.

| Metric | Count |
|--------|-------|
| Test classes | 3 |
| Total test cases | 34+ |
| Happy-path tests | ~18 |
| Negative / edge-case tests | ~16 |
| Endpoints covered | 15+ |

---

## 🛠️ Tech Stack

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 17 | Language |
| Rest Assured | 5.4.0 | API assertion DSL |
| TestNG | 7.9.0 | Test orchestration |
| Extent Reports | 5.1.1 | HTML test reporting |
| Jackson | 2.16.1 | JSON serialisation |
| JavaFaker | 1.0.2 | Randomised test data |
| Logback | 1.4.14 | Structured logging |
| GitHub Actions | — | CI/CD |
| Maven | 3.9+ | Build & dependency management |

---

## 📁 Project Structure

```
banking-api-tests/
├── .github/
│   └── workflows/
│       └── api-tests.yml          # GitHub Actions CI pipeline
├── src/test/
│   ├── java/com/banking/
│   │   ├── base/
│   │   │   └── BaseTest.java      # RequestSpec, token mgmt, reporting lifecycle
│   │   ├── config/
│   │   │   └── Config.java        # Env-aware configuration (URL, credentials)
│   │   ├── utils/
│   │   │   ├── ExtentManager.java # Thread-safe report manager
│   │   │   ├── TestDataFactory.java# JavaFaker-backed payload builder
│   │   │   └── TestListener.java  # TestNG → Extent Reports bridge
│   │   └── tests/
│   │       ├── auth/
│   │       │   └── AuthTests.java       # 12 auth tests
│   │       ├── account/
│   │       │   └── AccountTests.java    # 11 account tests
│   │       └── transaction/
│   │           └── TransactionTests.java # 13 transaction tests
│   └── resources/
│       ├── testng.xml             # Suite configuration
│       └── logback-test.xml       # Logging config
├── reports/                       # Generated HTML reports (gitignored)
└── pom.xml
```

---

## 🧪 Test Coverage

### Auth (`POST /api/auth/...`)
- ✅ Register new user — 201 + JWT
- ✅ Duplicate email registration — 409
- ✅ Missing fields — 400
- ✅ Invalid email format — 400
- ✅ Login with valid credentials — 200 + valid JWT structure
- ✅ Login with wrong password — 401
- ✅ Login with unknown email — 401/404
- ✅ Login with empty body — 400
- ✅ `GET /auth/me` with valid token — 200
- ✅ `GET /auth/me` without token — 401
- ✅ `GET /auth/me` with malformed token — 401
- ✅ Logout — session invalidation

### Accounts (`/api/accounts/...`)
- ✅ Create SAVINGS account — 201 + all fields
- ✅ Create CURRENT account — 201
- ✅ Invalid account type — 400
- ✅ Negative initial deposit — 400
- ✅ List accounts — non-empty array
- ✅ Get account by ID — correct data
- ✅ Get non-existent account — 404
- ✅ Get balance — balance + currency
- ✅ Cross-user access attempt — 403/404
- ✅ Close/delete account — 200/204
- ✅ Required fields assertion

### Transactions (`/api/accounts/{id}/...`)
- ✅ Deposit valid amount — balance increases
- ✅ Deposit zero — 400
- ✅ Deposit negative — 400
- ✅ Withdraw valid amount — balance decreases
- ✅ Withdraw exceeds balance (overdraft) — 400/422
- ✅ Withdraw zero — 400
- ✅ Transfer between own accounts — both balances update atomically
- ✅ Transfer with insufficient funds — 400/422
- ✅ Transfer to non-existent account — 404/400
- ✅ Transaction history — paginated list
- ✅ History with pagination params — respects page size
- ✅ Get transaction by ID — all fields present
- ✅ Get non-existent transaction — 404

---

## 🚀 Running the Tests

### Prerequisites
- Java 17+
- Maven 3.9+
- The [Digital Banking System](https://github.com/YOUR_USERNAME/digital-banking-system) running on `localhost:8080`

### 1. Start the Backend

```bash
# From the digital-banking-system repo
docker compose up -d
```

### 2. Run All Tests

```bash
mvn test
```

### 3. Run Against a Remote URL

```bash
mvn test -Dbase.url=https://your-deployed-api.com/api
```

### 4. Run a Specific Test Class

```bash
mvn test -Dtest=AuthTests
mvn test -Dtest=TransactionTests
```

### 5. View HTML Report

After the run, open:
```
reports/APITestReport_<timestamp>.html
```

---

## ⚙️ Configuration

| Method | Variable | Default |
|--------|----------|---------|
| System property | `-Dbase.url=...` | `http://localhost:8080/api` |
| Environment variable | `BASE_URL=...` | `http://localhost:8080/api` |
| System property | `-Dadmin.email=...` | `admin@bank.com` |
| Environment variable | `ADMIN_EMAIL=...` | `admin@bank.com` |

---

## 🔄 CI/CD Pipeline

GitHub Actions runs the full suite on every push to `main`/`develop` and every pull request.

**Pipeline steps:**
1. Checkout code
2. Set up JDK 17 (Temurin)
3. Run `mvn test` with the configured `BASE_URL`
4. Upload Extent HTML report as a build artifact (retained 30 days)
5. Fail the build if any tests failed

To point CI at a live server, add a `BASE_URL` secret in your repository settings.

---

## 🏗️ Design Decisions

**Why Rest Assured over Postman/Newman?**  
Rest Assured integrates directly with Java/Maven, allowing the framework to live in the same toolchain as the backend. Tests can share POJOs, re-use validation logic, and run inside the same CI pipeline.

**Why TestNG over JUnit 5?**  
TestNG's `@BeforeClass`, `dependsOnMethods`, and built-in parallel execution at the `classes` level are a natural fit for stateful API flows where accounts must exist before transactions can be tested.

**Thread-safe reporting**  
`ExtentManager` uses a `ThreadLocal<ExtentTest>` so parallel test classes write to separate report nodes without race conditions.

**Randomised test data**  
`TestDataFactory` uses JavaFaker to generate unique emails, names, and amounts on each run, preventing test pollution from leftover database state.

---

## 📊 Sample Report

Generated reports follow this structure:

```
Digital Banking System — REST Assured Suite
├── Auth Tests
│   ├── PASS  registerNewUser_shouldReturn201
│   ├── PASS  loginValidCredentials_shouldReturn200WithToken
│   └── ...
├── Account Tests
│   ├── PASS  createSavingsAccount_shouldReturn201
│   └── ...
└── Transaction Tests
    ├── PASS  deposit_validAmount_shouldIncreaseBalance
    └── ...
```

---

## 🔗 Related Repositories

- **Backend**: [digital-banking-system](https://github.com/swapnajitsarkar/digital-banking-system) — Spring Boot 3 + JWT + Docker
- **UI Tests**: [orangehrm-selenium-framework](https://github.com/swapnajitsarkar/Selenium-TestNG-Framework) — Selenium POM + TestNG
