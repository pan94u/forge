#!/bin/bash
#
# code-style-baseline.sh
# Runs code style checks using ktlint (Kotlin) and checkstyle (Java).
# Exits with 0 on success, 1 on any violation.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || echo ".")}"

VIOLATIONS_FOUND=0
REPORT=""

# --- Color output helpers ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_info() { echo "[INFO] $1"; }

# --- Check for Kotlin files and run ktlint ---
check_ktlint() {
    local kotlin_files
    kotlin_files=$(find "$PROJECT_ROOT" -name "*.kt" -not -path "*/build/*" -not -path "*/.gradle/*" 2>/dev/null | head -1)

    if [ -z "$kotlin_files" ]; then
        log_info "No Kotlin files found, skipping ktlint check"
        return 0
    fi

    log_info "Running ktlint code style check on Kotlin files..."

    # Try gradle ktlint task first
    if [ -f "$PROJECT_ROOT/gradlew" ]; then
        if "$PROJECT_ROOT/gradlew" -p "$PROJECT_ROOT" ktlintCheck --quiet 2>/dev/null; then
            log_pass "ktlint: All Kotlin files pass code style checks"
            return 0
        else
            log_fail "ktlint: Code style violations found"
            VIOLATIONS_FOUND=1

            # Collect detailed violations
            local ktlint_output
            ktlint_output=$("$PROJECT_ROOT/gradlew" -p "$PROJECT_ROOT" ktlintCheck 2>&1 || true)
            REPORT="${REPORT}\n--- ktlint violations ---\n${ktlint_output}\n"
            return 1
        fi
    fi

    # Fall back to ktlint binary if available
    if command -v ktlint &>/dev/null; then
        local ktlint_output
        ktlint_output=$(ktlint "$PROJECT_ROOT/src/**/*.kt" 2>&1 || true)

        if [ -z "$ktlint_output" ]; then
            log_pass "ktlint: All Kotlin files pass code style checks"
            return 0
        else
            log_fail "ktlint: Code style violations found"
            VIOLATIONS_FOUND=1
            REPORT="${REPORT}\n--- ktlint violations ---\n${ktlint_output}\n"
            return 1
        fi
    fi

    log_warn "ktlint not found — skipping Kotlin style checks. Install ktlint or add the Gradle plugin."
    return 0
}

# --- Check for Java files and run checkstyle ---
check_checkstyle() {
    local java_files
    java_files=$(find "$PROJECT_ROOT" -name "*.java" -not -path "*/build/*" -not -path "*/target/*" 2>/dev/null | head -1)

    if [ -z "$java_files" ]; then
        log_info "No Java files found, skipping checkstyle check"
        return 0
    fi

    log_info "Running checkstyle on Java files..."

    # Try gradle checkstyle task
    if [ -f "$PROJECT_ROOT/gradlew" ]; then
        if "$PROJECT_ROOT/gradlew" -p "$PROJECT_ROOT" checkstyleMain --quiet 2>/dev/null; then
            log_pass "checkstyle: All Java files pass code style checks"
            return 0
        else
            log_fail "checkstyle: Code style violations found"
            VIOLATIONS_FOUND=1

            local cs_output
            cs_output=$("$PROJECT_ROOT/gradlew" -p "$PROJECT_ROOT" checkstyleMain 2>&1 || true)
            REPORT="${REPORT}\n--- checkstyle violations ---\n${cs_output}\n"
            return 1
        fi
    fi

    # Try maven checkstyle
    if [ -f "$PROJECT_ROOT/pom.xml" ]; then
        if (cd "$PROJECT_ROOT" && mvn checkstyle:check -q 2>/dev/null); then
            log_pass "checkstyle: All Java files pass code style checks"
            return 0
        else
            log_fail "checkstyle: Code style violations found"
            VIOLATIONS_FOUND=1

            local cs_output
            cs_output=$(cd "$PROJECT_ROOT" && mvn checkstyle:check 2>&1 || true)
            REPORT="${REPORT}\n--- checkstyle violations ---\n${cs_output}\n"
            return 1
        fi
    fi

    log_warn "checkstyle not configured — skipping Java style checks."
    return 0
}

# --- Check import ordering ---
check_import_ordering() {
    log_info "Checking import ordering in Kotlin files..."

    local bad_imports=0
    local bad_files=""

    while IFS= read -r -d '' file; do
        # Check for wildcard imports (generally discouraged)
        if grep -n 'import .*\.\*$' "$file" | grep -v '// ktlint-disable' >/dev/null 2>&1; then
            bad_imports=1
            local violations
            violations=$(grep -n 'import .*\.\*$' "$file" | grep -v '// ktlint-disable')
            bad_files="${bad_files}\n  ${file}:\n${violations}"
        fi
    done < <(find "$PROJECT_ROOT" -name "*.kt" -not -path "*/build/*" -not -path "*/.gradle/*" -print0 2>/dev/null)

    if [ "$bad_imports" -eq 1 ]; then
        log_fail "Wildcard imports found (use explicit imports instead):"
        REPORT="${REPORT}\n--- wildcard import violations ---${bad_files}\n"
        VIOLATIONS_FOUND=1
        return 1
    else
        log_pass "No wildcard imports found"
        return 0
    fi
}

# --- Check file naming conventions ---
check_naming_conventions() {
    log_info "Checking file naming conventions..."

    local bad_names=0
    local bad_files=""

    # Kotlin files should be PascalCase
    while IFS= read -r -d '' file; do
        local filename
        filename=$(basename "$file" .kt)
        # Allow lowercase for package-level files (e.g., extensions.kt)
        # But flag files that mix conventions (e.g., order_Service.kt)
        if echo "$filename" | grep -E '_[a-z]' >/dev/null 2>&1; then
            if [[ "$filename" != *"Test"* ]] && [[ "$filename" != *"Spec"* ]]; then
                bad_names=1
                bad_files="${bad_files}\n  ${file} (use PascalCase, not snake_case)"
            fi
        fi
    done < <(find "$PROJECT_ROOT/src/main" -name "*.kt" -not -path "*/build/*" -print0 2>/dev/null)

    if [ "$bad_names" -eq 1 ]; then
        log_fail "File naming convention violations:${bad_files}"
        REPORT="${REPORT}\n--- naming convention violations ---${bad_files}\n"
        VIOLATIONS_FOUND=1
        return 1
    else
        log_pass "File naming conventions OK"
        return 0
    fi
}

# --- Main ---
main() {
    echo "========================================"
    echo "  Code Style Baseline Check"
    echo "========================================"
    echo "Project root: $PROJECT_ROOT"
    echo ""

    check_ktlint || true
    check_checkstyle || true
    check_import_ordering || true
    check_naming_conventions || true

    echo ""
    echo "========================================"

    if [ "$VIOLATIONS_FOUND" -eq 1 ]; then
        echo -e "${RED}BASELINE FAILED: Code style violations detected${NC}"
        echo ""
        echo "Violation details:"
        echo -e "$REPORT"
        echo ""
        echo "Fix the violations above and re-run this baseline."
        exit 1
    else
        echo -e "${GREEN}BASELINE PASSED: All code style checks passed${NC}"
        exit 0
    fi
}

main "$@"
