---
name: testing-profile
description: "Skill Profile for the Testing delivery stage. Focused on test case design, test execution, coverage analysis, and quality reporting."
skills:
  - test-case-writing
  - test-execution
  - testing-standards
baselines:
  - test-coverage-baseline
hitl-checkpoint: "Test report confirmation — present comprehensive test results and coverage report for QA review and sign-off."
---

# Testing Profile — OODA Guidance

## Overview

The Testing Profile activates when the SuperAgent enters the Testing delivery stage. The primary objective is to ensure comprehensive test coverage, identify defects, and produce a quality report that gives confidence for release.

This profile enforces one baseline:
- **test-coverage-baseline**: Validates service layer >= 80% coverage and controller integration tests exist

The Testing Profile may be activated independently (dedicated QA phase) or as part of the Development Profile's test-writing activities.

---

## Observe

Understand what needs to be tested:

1. **Read the requirements**:
   - PRD requirements and acceptance criteria
   - User stories and their acceptance conditions
   - Non-functional requirements (performance, security, accessibility)
   - Edge cases mentioned in requirements

2. **Read the code under test**:
   - Service layer methods — identify all code paths
   - Controller endpoints — identify all input combinations
   - Domain logic — identify business rule boundaries
   - Error handling — identify all exception paths
   - Configuration — identify environment-dependent behavior

3. **Analyze existing tests**:
   - What is already tested?
   - What is the current coverage? (`forge-metrics-server.getCoverage(module)`)
   - Are there flaky tests? (check CI history)
   - What testing patterns are already established?

4. **Query context**:
   - `forge-context-server.getRecentChanges(7)` — what code changed recently?
   - `forge-knowledge-server.searchKnowledge("test failures")` — known problem areas
   - `forge-knowledge-server.getLessonsLearned("testing")` — past testing mistakes

5. **Identify test boundaries**:
   - External service dependencies (need mocking/stubbing)
   - Database interactions (need test data setup)
   - Time-dependent logic (need clock mocking)
   - File system interactions (need temp directories)
   - Network calls (need WireMock or similar)

### Observe Checklist
- [ ] Requirements and acceptance criteria cataloged
- [ ] Code under test fully read and paths identified
- [ ] Existing test coverage understood
- [ ] External dependencies identified
- [ ] Test infrastructure capabilities known

---

## Orient

Design the test strategy:

1. **Test type mapping**:
   - **Unit tests**: individual methods, business logic, utility functions
   - **Integration tests**: controller endpoints, repository queries, service interactions
   - **Contract tests**: external API calls, message schemas
   - **End-to-end tests**: critical user journeys (if applicable)

2. **Identify test boundaries using techniques**:
   - **Equivalence partitioning**: group inputs into classes that should produce the same behavior
   - **Boundary value analysis**: test at the edges of equivalence classes
   - **Decision table testing**: for complex business rules with multiple conditions
   - **State transition testing**: for stateful entities with defined lifecycle

3. **Coverage gap analysis**:
   - Compare existing coverage with the 80% threshold
   - Identify specific methods/branches that lack coverage
   - Prioritize: critical business logic > error handling > edge cases > trivial code

4. **Risk-based test prioritization**:
   - High risk: security-sensitive code, financial calculations, data integrity
   - Medium risk: complex business logic, integration points, error handling
   - Low risk: simple CRUD operations, configuration, logging

5. **Test data strategy**:
   - Define test fixtures and factories
   - Identify shared test data vs. test-specific data
   - Plan database state setup and teardown
   - Consider test data builders or ObjectMother pattern

### Orient Output
A test plan with categorized test cases, prioritized by risk and coverage impact.

---

## Decide

Finalize test case design:

1. **Test case catalog**:
   - For each requirement: at least one happy-path test
   - For each business rule: boundary and equivalence tests
   - For each error scenario: specific error handling test
   - For each API endpoint: valid input, invalid input, auth, and edge cases

2. **Test organization**:
   - Mirror source package structure in test packages
   - Group related tests in nested classes or describe blocks
   - Use meaningful test names that describe behavior:
     - `should return order when valid ID provided`
     - `should throw NotFoundException when order does not exist`
     - `should reject request when amount exceeds daily limit`

3. **Mocking strategy**:
   - Mock external services (HTTP clients, message queues)
   - Use real implementations for internal dependencies when possible
   - Use testcontainers for database integration tests
   - Mock time/clock for time-dependent logic

4. **Expected results definition**:
   - Define exact expected outputs for each test case
   - Define expected exceptions with specific types and messages
   - Define expected side effects (database state, messages published)

### Decide Output
Complete test case specifications ready for implementation.

---

## Act

Write and execute tests:

### Step 1: Write Unit Tests
1. Create test classes for each service/component under test
2. Implement test cases from the test catalog:
   - **Happy path tests**: verify correct behavior with valid input
   - **Error path tests**: verify correct exceptions with invalid input
   - **Edge case tests**: verify behavior at boundaries
3. Follow testing standards from Foundation Skills:
   - Arrange-Act-Assert pattern
   - One assertion per test (or one logical assertion group)
   - No test interdependencies
   - Clear test names describing behavior

### Step 2: Write Integration Tests
1. Create integration test classes for controllers
2. Implement endpoint tests:
   - Valid requests → correct response status and body
   - Invalid requests → correct error response
   - Authentication scenarios → correct access control
3. Create repository integration tests:
   - Custom query methods with various inputs
   - Pagination and sorting
   - Edge cases (empty results, large datasets)

### Step 3: Write Contract Tests
1. For each external API dependency:
   - Define expected request format
   - Define expected response format
   - Create contract test using Pact or Spring Cloud Contract
2. For each message schema:
   - Define expected message format
   - Create producer and consumer contract tests

### Step 4: Execute All Tests
1. Run the full test suite: `./gradlew test`
2. Collect results:
   - Total tests: passed / failed / skipped
   - Execution time
   - Failure details with stack traces
3. Analyze failures:
   - Real bugs → log as defects
   - Test issues → fix the test
   - Flaky tests → mark as flaky, investigate root cause

### Step 5: Coverage Analysis
1. Generate coverage report: `./gradlew jacocoTestReport`
2. Analyze coverage by module:
   - Service layer: must be >= 80%
   - Controller layer: must have integration tests for every endpoint
   - Repository layer: custom methods must be tested
3. Identify gaps and write additional tests to close them

### Step 6: Run Baselines
1. Execute `test-coverage-baseline.sh`
2. On failure:
   - Read the specific coverage gaps
   - Write targeted tests to close the gaps
   - Re-run the baseline
   - Maximum 3 fix loops

### Step 7: Generate Test Report
1. Compile results into a structured report:
   - Test execution summary (pass/fail/skip counts)
   - Coverage summary (by module, by layer)
   - Defects found (with severity and description)
   - Flaky test report
   - Risk areas with low coverage
2. Present at HITL checkpoint

### Act Output
Comprehensive test suite with passing baselines and a detailed test report.

---

## HITL Checkpoint: Test Report Confirmation

**When**: After all tests pass (or known failures are documented) and coverage baselines pass.

**Present to the user**:
1. Test execution summary
2. Coverage report with module-level breakdown
3. List of defects found (if any)
4. Flaky tests identified (if any)
5. Risk areas with justification for lower coverage
6. Recommendation: ready for release or needs fixes

**Wait for**: QA sign-off or feedback on additional test requirements.

**On rejection**: Add requested tests, fix identified issues, return to **Observe** with new requirements.
