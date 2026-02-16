---
name: test-execution
description: "Skill for test execution — running test suites, coverage analysis, flaky test detection, and test report generation."
stage: testing
type: delivery-skill
---

# Test Execution Skill

## Purpose

This skill guides the SuperAgent in executing test suites systematically, analyzing coverage and results, detecting and handling flaky tests, and generating comprehensive test reports.

---

## Running Test Suites

### Execution Order

Run tests in this order to get the fastest feedback on failures:

1. **Unit tests** (fastest, most isolated):
   ```bash
   ./gradlew test --tests '*UnitTest' --tests '*Test' --exclude-task integrationTest
   ```

2. **Integration tests** (slower, requires infrastructure):
   ```bash
   ./gradlew integrationTest
   ```

3. **Contract tests** (validates external API compatibility):
   ```bash
   ./gradlew contractTest
   ```

4. **End-to-end tests** (slowest, full system):
   ```bash
   ./gradlew e2eTest
   ```

### Execution Commands

**Gradle (Kotlin/Java)**:
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests 'com.example.OrderServiceTest'

# Run specific test method
./gradlew test --tests 'com.example.OrderServiceTest.should create order successfully'

# Run tests in a specific module
./gradlew :order-service:test

# Run with verbose output
./gradlew test --info

# Run with parallel execution
./gradlew test --parallel

# Generate test report
./gradlew test jacocoTestReport
```

**Maven (Java)**:
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=OrderServiceTest

# Run with coverage
mvn test jacoco:report

# Skip tests (for compilation check only)
mvn compile -DskipTests
```

### Handling Test Failures

When tests fail, analyze systematically:

1. **Read the failure message** carefully — what was expected vs. actual?
2. **Check if it is a real bug or a test issue**:
   - Does the test expectation match the requirements?
   - Is the test setup correct?
   - Is the test data in the expected state?
3. **Categorize the failure**:
   - **Real bug**: Log as a defect with reproduction steps
   - **Test bug**: Fix the test, not the code
   - **Environment issue**: Fix the environment, re-run
   - **Flaky test**: Mark for investigation (see Flaky Test section)
4. **Never ignore a failing test** — every failure must be resolved or documented

---

## Coverage Analysis and Gap Identification

### Coverage Metrics

Track these coverage metrics:

| Metric           | Description                                    | Target  |
|------------------|------------------------------------------------|---------|
| Line coverage    | Percentage of executable lines covered         | >= 80%  |
| Branch coverage  | Percentage of conditional branches covered     | >= 70%  |
| Method coverage  | Percentage of methods with at least one test   | >= 90%  |
| Class coverage   | Percentage of classes with at least one test   | >= 95%  |

### Coverage Analysis Process

1. **Generate coverage report**:
   ```bash
   ./gradlew test jacocoTestReport
   # Report at: build/reports/jacoco/test/html/index.html
   ```

2. **Review coverage by layer**:
   - **Service layer**: Must be >= 80% line coverage (business logic is critical)
   - **Controller layer**: Must have integration tests for every endpoint
   - **Repository layer**: Custom query methods must be tested
   - **Domain model**: Must have tests for business rule methods
   - **Configuration**: Coverage not required (tested implicitly)

3. **Identify gaps**:
   ```
   For each class with < 80% coverage:
   ├── List uncovered methods
   ├── List uncovered branches (if/else, switch, try/catch)
   ├── Categorize each gap:
   │   ├── Critical: business logic — MUST add test
   │   ├── Important: error handling — SHOULD add test
   │   └── Low: logging, toString — MAY skip
   └── Prioritize test additions by criticality
   ```

4. **Write targeted tests** to close gaps:
   - Focus on uncovered branches in business logic
   - Add error-path tests for uncovered catch blocks
   - Add edge-case tests for uncovered conditional branches
   - Do NOT write trivial tests just to increase numbers (e.g., testing getters)

### Coverage Exclusions

Some code legitimately does not need test coverage:

- Configuration classes (e.g., `@Configuration` beans)
- Data classes with no logic (pure DTOs)
- Generated code (MapStruct mappers, Lombok-generated code)
- Main application class (`@SpringBootApplication`)
- Constants and enum declarations without logic

Document exclusions in the JaCoCo configuration:
```kotlin
// build.gradle.kts
jacocoTestReport {
    afterEvaluate {
        classDirectories.setFrom(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/config/**",
                    "**/dto/**",
                    "**/*Application*",
                    "**/*Config*"
                )
            }
        })
    }
}
```

---

## Flaky Test Detection and Handling

### What is a Flaky Test?

A test that sometimes passes and sometimes fails without any code change. Flaky tests erode confidence in the test suite and waste investigation time.

### Common Causes

| Cause                       | Symptom                              | Fix                                |
|-----------------------------|--------------------------------------|------------------------------------|
| Shared mutable state        | Fails when run with other tests      | Isolate test data                  |
| Time-dependent logic        | Fails at certain times of day        | Mock the clock                     |
| Race conditions             | Fails intermittently                 | Add synchronization or use Awaitility |
| External service dependency | Fails when service is slow/down      | Mock external services             |
| Port conflicts              | Fails when port is already in use    | Use random ports                   |
| Order-dependent tests       | Fails when test order changes        | Make tests independent             |
| Database state leakage      | Fails when run after specific test   | Use transactions or clean up       |
| Insufficient wait times     | Fails on slower CI machines          | Use polling assertions, not sleep  |

### Detection Process

1. **Run tests multiple times** (at least 3 runs):
   ```bash
   for i in 1 2 3; do ./gradlew test; done
   ```

2. **Compare results**: Any test that fails in some runs but passes in others is flaky

3. **Check CI history**: Look for tests that flip between pass and fail across builds

4. **Quarantine flaky tests**:
   ```kotlin
   @Tag("flaky")
   @Test
   fun `flaky test that needs investigation`() { /* ... */ }
   ```

5. **Track in a flaky test register**:

   | Test Name | First Seen | Frequency | Root Cause | Status |
   |-----------|-----------|-----------|------------|--------|
   | ...       | ...       | ...       | ...        | ...    |

### Handling Protocol

1. **Immediate**: Tag the test as `@Tag("flaky")` and exclude from CI blocking
2. **Within 1 sprint**: Investigate root cause and fix
3. **If unfixable**: Delete the test and replace with a reliable alternative
4. **Never**: Leave a flaky test in the main test suite permanently

---

## Test Report Generation

### Report Structure

```markdown
# Test Execution Report

## Summary
- **Date**: [execution date]
- **Branch**: [git branch]
- **Commit**: [git commit hash]
- **Duration**: [total execution time]
- **Result**: PASS / FAIL

## Test Results

| Category          | Total | Passed | Failed | Skipped | Duration |
|-------------------|-------|--------|--------|---------|----------|
| Unit Tests        | 150   | 148    | 1      | 1       | 12s      |
| Integration Tests | 45    | 45     | 0      | 0       | 38s      |
| Contract Tests    | 12    | 12     | 0      | 0       | 8s       |
| **Total**         | **207**| **205**| **1** | **1**   | **58s**  |

## Coverage Summary

| Module          | Line Coverage | Branch Coverage | Status   |
|-----------------|--------------|-----------------|----------|
| order-service   | 87%          | 75%             | PASS     |
| user-service    | 82%          | 71%             | PASS     |
| payment-client  | 78%          | 65%             | **FAIL** |
| **Overall**     | **84%**      | **72%**         | -        |

## Failures

### Failure 1: OrderServiceTest.should reject expired order
- **Type**: Unit Test
- **Class**: com.example.order.OrderServiceTest
- **Message**: Expected OrderExpiredException but got OrderConfirmedException
- **Analysis**: Real bug — expiration check not working for orders created at midnight
- **Severity**: High
- **Ticket**: [link to created issue]

## Flaky Tests

| Test | Occurrences (last 10 runs) | Status |
|------|---------------------------|--------|
| ...  | 8/10 pass                 | Investigating |

## Coverage Gaps (Actionable)

| Class | Current | Target | Gap Area | Priority |
|-------|---------|--------|----------|----------|
| PaymentClient | 78% | 80% | Error handling methods | High |

## Recommendations

1. [Fix the expiration bug in OrderService]
2. [Add error handling tests for PaymentClient]
3. [Investigate flaky test in UserServiceTest]

## Baseline Results

| Baseline               | Status | Details |
|------------------------|--------|---------|
| test-coverage-baseline | FAIL   | payment-client module below 80% |
```

### Report Guidelines

1. **Always include the summary table** — reviewers scan this first
2. **Categorize every failure** — real bug, test issue, or environment issue
3. **Provide actionable recommendations** — what should be done next
4. **Include baseline results** — are we passing the automated quality gates?
5. **Track trends** — is coverage going up or down compared to previous run?
6. **Link to details** — reference JaCoCo HTML reports and test output for deep dives
