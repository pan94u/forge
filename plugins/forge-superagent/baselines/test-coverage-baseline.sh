#!/bin/bash
#
# test-coverage-baseline.sh
# Verifies test coverage meets minimum thresholds:
# - Service layer >= 80% line coverage
# - Controller endpoints have integration tests
# Exits with 0 on success, 1 on any threshold violation.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || echo ".")}"

VIOLATIONS_FOUND=0
REPORT=""

# --- Configuration ---
SERVICE_LAYER_MIN_COVERAGE=80
OVERALL_MIN_COVERAGE=70

# --- Color output helpers ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_info() { echo "[INFO] $1"; }

# --- Run tests and generate coverage report ---
run_tests_with_coverage() {
    log_info "Running tests with coverage report generation..."

    if [ -f "$PROJECT_ROOT/gradlew" ]; then
        if "$PROJECT_ROOT/gradlew" -p "$PROJECT_ROOT" test jacocoTestReport --quiet 2>/dev/null; then
            log_pass "All tests passed"
            return 0
        else
            log_fail "Some tests failed — fix test failures before checking coverage"
            VIOLATIONS_FOUND=1
            REPORT="${REPORT}\n--- Test failures must be resolved first ---\n"
            REPORT="${REPORT}Run './gradlew test' for detailed failure information.\n"
            return 1
        fi
    elif [ -f "$PROJECT_ROOT/pom.xml" ]; then
        if (cd "$PROJECT_ROOT" && mvn test jacoco:report -q 2>/dev/null); then
            log_pass "All tests passed"
            return 0
        else
            log_fail "Some tests failed — fix test failures before checking coverage"
            VIOLATIONS_FOUND=1
            return 1
        fi
    else
        log_warn "No recognized build tool found (Gradle or Maven). Cannot run tests."
        return 1
    fi
}

# --- Parse JaCoCo XML report for coverage data ---
parse_jacoco_coverage() {
    local report_path="$1"
    local package_filter="$2"
    local metric_type="${3:-LINE}"

    if [ ! -f "$report_path" ]; then
        echo "-1"
        return
    fi

    # Extract coverage counters for the specified package filter and metric type
    # JaCoCo XML format: <counter type="LINE" missed="10" covered="90"/>
    local missed=0
    local covered=0

    if [ -n "$package_filter" ]; then
        # Filter by package name
        local in_target_package=false
        while IFS= read -r line; do
            if echo "$line" | grep -q "<package name=\"${package_filter}"; then
                in_target_package=true
            elif echo "$line" | grep -q "</package>"; then
                in_target_package=false
            elif [ "$in_target_package" = true ] && echo "$line" | grep -q "counter type=\"${metric_type}\""; then
                local line_missed
                local line_covered
                line_missed=$(echo "$line" | sed -n 's/.*missed="\([0-9]*\)".*/\1/p')
                line_covered=$(echo "$line" | sed -n 's/.*covered="\([0-9]*\)".*/\1/p')
                missed=$((missed + line_missed))
                covered=$((covered + line_covered))
            fi
        done < "$report_path"
    else
        # Overall coverage — use report-level counters
        local report_counters
        report_counters=$(grep "counter type=\"${metric_type}\"" "$report_path" | tail -1)
        if [ -n "$report_counters" ]; then
            missed=$(echo "$report_counters" | sed -n 's/.*missed="\([0-9]*\)".*/\1/p')
            covered=$(echo "$report_counters" | sed -n 's/.*covered="\([0-9]*\)".*/\1/p')
        fi
    fi

    local total=$((missed + covered))
    if [ "$total" -eq 0 ]; then
        echo "0"
    else
        echo $((covered * 100 / total))
    fi
}

# --- Find JaCoCo report files ---
find_jacoco_reports() {
    find "$PROJECT_ROOT" -path "*/build/reports/jacoco/test/jacocoTestReport.xml" -o \
         -path "*/target/site/jacoco/jacoco.xml" 2>/dev/null
}

# --- Check service layer coverage ---
check_service_layer_coverage() {
    log_info "Checking service layer coverage (minimum: ${SERVICE_LAYER_MIN_COVERAGE}%)..."

    local reports
    reports=$(find_jacoco_reports)

    if [ -z "$reports" ]; then
        log_warn "No JaCoCo report found. Ensure tests ran with coverage enabled."
        log_info "Looking for coverage data in alternative locations..."

        # Check for Gradle JaCoCo output directory
        local jacoco_exec
        jacoco_exec=$(find "$PROJECT_ROOT" -name "*.exec" -path "*/jacoco/*" 2>/dev/null | head -1)
        if [ -n "$jacoco_exec" ]; then
            log_info "Found JaCoCo execution data at: $jacoco_exec"
            log_info "Report may not have been generated. Run: ./gradlew jacocoTestReport"
        fi

        VIOLATIONS_FOUND=1
        REPORT="${REPORT}\n--- Coverage report not found ---\n"
        REPORT="${REPORT}Ensure JaCoCo is configured and run './gradlew test jacocoTestReport'\n"
        return 1
    fi

    local all_passed=true

    while IFS= read -r report; do
        log_info "Analyzing report: $report"

        # Find service packages (common naming conventions)
        local service_packages=()
        for pkg_pattern in "*/service" "*/service/*" "*/services" "*/services/*"; do
            local found_pkgs
            found_pkgs=$(grep -o "package name=\"[^\"]*service[^\"]*\"" "$report" 2>/dev/null | \
                         sed 's/package name="//;s/"//' | sort -u || true)
            while IFS= read -r pkg; do
                [ -n "$pkg" ] && service_packages+=("$pkg")
            done <<< "$found_pkgs"
        done

        if [ ${#service_packages[@]} -eq 0 ]; then
            log_info "No service packages detected in report — checking overall coverage"

            local overall_coverage
            overall_coverage=$(parse_jacoco_coverage "$report" "" "LINE")

            if [ "$overall_coverage" -eq -1 ]; then
                log_warn "Could not parse coverage from report"
                continue
            fi

            log_info "Overall line coverage: ${overall_coverage}%"

            if [ "$overall_coverage" -lt "$OVERALL_MIN_COVERAGE" ]; then
                log_fail "Overall coverage ${overall_coverage}% is below minimum ${OVERALL_MIN_COVERAGE}%"
                all_passed=false
                REPORT="${REPORT}\n  Overall coverage: ${overall_coverage}% (minimum: ${OVERALL_MIN_COVERAGE}%)\n"
            else
                log_pass "Overall coverage ${overall_coverage}% meets minimum ${OVERALL_MIN_COVERAGE}%"
            fi
        else
            for pkg in "${service_packages[@]}"; do
                local coverage
                coverage=$(parse_jacoco_coverage "$report" "$pkg" "LINE")

                if [ "$coverage" -lt "$SERVICE_LAYER_MIN_COVERAGE" ]; then
                    log_fail "Service package '${pkg}' coverage: ${coverage}% (minimum: ${SERVICE_LAYER_MIN_COVERAGE}%)"
                    all_passed=false
                    REPORT="${REPORT}\n  Package '${pkg}': ${coverage}% line coverage (minimum: ${SERVICE_LAYER_MIN_COVERAGE}%)\n"
                else
                    log_pass "Service package '${pkg}' coverage: ${coverage}% (minimum: ${SERVICE_LAYER_MIN_COVERAGE}%)"
                fi
            done
        fi
    done <<< "$reports"

    if [ "$all_passed" = false ]; then
        VIOLATIONS_FOUND=1
        return 1
    fi
    return 0
}

# --- Check controller integration tests exist ---
check_controller_integration_tests() {
    log_info "Checking that controller endpoints have integration tests..."

    local controllers
    controllers=$(find "$PROJECT_ROOT/src/main" \( -name "*Controller.kt" -o -name "*Controller.java" \) \
                  -not -path "*/build/*" 2>/dev/null || true)

    if [ -z "$controllers" ]; then
        log_info "No controller files found — skipping controller integration test check"
        return 0
    fi

    local missing_tests=""
    local all_have_tests=true

    while IFS= read -r controller; do
        [ -z "$controller" ] && continue

        local controller_name
        controller_name=$(basename "$controller" | sed 's/\.\(kt\|java\)$//')

        # Look for corresponding test files
        local test_patterns=(
            "${controller_name}Test"
            "${controller_name}IntegrationTest"
            "${controller_name}IT"
            "${controller_name}WebTest"
        )

        local test_found=false
        for pattern in "${test_patterns[@]}"; do
            local test_file
            test_file=$(find "$PROJECT_ROOT/src/test" \( -name "${pattern}.kt" -o -name "${pattern}.java" \) 2>/dev/null | head -1)
            if [ -n "$test_file" ]; then
                test_found=true

                # Verify the test file actually has test methods
                local test_count
                test_count=$(grep -c '@Test\|@ParameterizedTest' "$test_file" 2>/dev/null || echo "0")
                if [ "$test_count" -eq 0 ]; then
                    log_warn "${controller_name}: Test file exists but contains no @Test methods"
                else
                    log_pass "${controller_name}: ${test_count} integration test(s) found"
                fi
                break
            fi
        done

        if [ "$test_found" = false ]; then
            all_have_tests=false
            missing_tests="${missing_tests}\n  ${controller_name} (${controller})"
        fi
    done <<< "$controllers"

    if [ "$all_have_tests" = false ]; then
        log_fail "Controllers missing integration tests:${missing_tests}"
        REPORT="${REPORT}\n--- Missing Controller Integration Tests ---${missing_tests}\n"
        REPORT="${REPORT}\nCreate integration test classes using @WebMvcTest or @SpringBootTest.\n"
        VIOLATIONS_FOUND=1
        return 1
    else
        log_pass "All controllers have integration tests"
        return 0
    fi
}

# --- Main ---
main() {
    echo "========================================"
    echo "  Test Coverage Baseline Check"
    echo "========================================"
    echo "Project root: $PROJECT_ROOT"
    echo "Service layer minimum: ${SERVICE_LAYER_MIN_COVERAGE}%"
    echo ""

    run_tests_with_coverage || true
    check_service_layer_coverage || true
    check_controller_integration_tests || true

    echo ""
    echo "========================================"

    if [ "$VIOLATIONS_FOUND" -eq 1 ]; then
        echo -e "${RED}BASELINE FAILED: Test coverage thresholds not met${NC}"
        echo ""
        echo "Coverage gap details:"
        echo -e "$REPORT"
        echo ""
        echo "Add tests to close coverage gaps and re-run this baseline."
        exit 1
    else
        echo -e "${GREEN}BASELINE PASSED: All test coverage thresholds met${NC}"
        exit 0
    fi
}

main "$@"
