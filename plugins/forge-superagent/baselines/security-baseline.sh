#!/bin/bash
#
# security-baseline.sh
# Scans for common security issues: hardcoded credentials, SQL injection patterns,
# XSS vulnerabilities, and insecure configurations.
# Exits with 0 on success, 1 on any finding.
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
NC='\033[0m'

log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_info() { echo "[INFO] $1"; }

# Source directories to scan (exclude build artifacts and test code)
SRC_DIRS=("src/main" "src/commonMain")

find_source_files() {
    local extensions=("$@")
    for src_dir in "${SRC_DIRS[@]}"; do
        local full_path="$PROJECT_ROOT/$src_dir"
        if [ -d "$full_path" ]; then
            for ext in "${extensions[@]}"; do
                find "$full_path" -name "*.$ext" -type f 2>/dev/null
            done
        fi
    done
}

# --- Check for hardcoded credentials ---
check_hardcoded_credentials() {
    log_info "Scanning for hardcoded credentials..."

    local findings=""
    local found=0

    # Patterns that indicate hardcoded secrets
    local patterns=(
        'password\s*=\s*"[^"]{3,}"'
        'passwd\s*=\s*"[^"]{3,}"'
        'secret\s*=\s*"[^"]{3,}"'
        'api[_-]?key\s*=\s*"[^"]{3,}"'
        'apikey\s*=\s*"[^"]{3,}"'
        'access[_-]?token\s*=\s*"[^"]{3,}"'
        'auth[_-]?token\s*=\s*"[^"]{3,}"'
        'private[_-]?key\s*=\s*"[^"]{3,}"'
        'jdbc:.*password=[^&"]*[^${}]'
        'BEGIN\s+(RSA\s+)?PRIVATE\s+KEY'
        'AKIA[0-9A-Z]{16}'
    )

    # Files to exclude (test fixtures, documentation, configuration templates)
    local exclude_patterns=(
        "*/test/*"
        "*/testFixtures/*"
        "*Test.kt"
        "*Test.java"
        "*Spec.kt"
        "*.md"
        "*.example"
        "*.template"
    )

    local exclude_args=""
    for pattern in "${exclude_patterns[@]}"; do
        exclude_args="${exclude_args} --exclude=${pattern}"
    done

    for pattern in "${patterns[@]}"; do
        local matches
        matches=$(grep -rn -E "$pattern" "$PROJECT_ROOT/src/main" $exclude_args 2>/dev/null || true)

        if [ -n "$matches" ]; then
            # Filter out false positives: empty strings, placeholder values, environment variable references
            local real_matches
            real_matches=$(echo "$matches" | grep -v -E '(=\s*""|\$\{|getenv|System\.getenv|@Value|config\.|properties\.)' || true)

            if [ -n "$real_matches" ]; then
                found=1
                findings="${findings}\n  Pattern: ${pattern}\n${real_matches}\n"
            fi
        fi
    done

    # Check for .env files committed to the repository
    if [ -f "$PROJECT_ROOT/.env" ]; then
        local gitignore_check
        gitignore_check=$(git -C "$PROJECT_ROOT" check-ignore .env 2>/dev/null || echo "")
        if [ -z "$gitignore_check" ]; then
            found=1
            findings="${findings}\n  .env file exists and is NOT in .gitignore — this file may contain secrets!\n"
        fi
    fi

    if [ "$found" -eq 1 ]; then
        log_fail "Potential hardcoded credentials detected:"
        REPORT="${REPORT}\n--- SECURITY: Hardcoded Credentials ---${findings}\n"
        VIOLATIONS_FOUND=1
        return 1
    else
        log_pass "No hardcoded credentials detected"
        return 0
    fi
}

# --- Check for SQL injection patterns ---
check_sql_injection() {
    log_info "Scanning for SQL injection vulnerabilities..."

    local findings=""
    local found=0

    # Pattern: string concatenation in SQL queries
    local sql_concat_patterns=(
        '"SELECT\s.*"\s*\+\s*'
        '"INSERT\s.*"\s*\+\s*'
        '"UPDATE\s.*"\s*\+\s*'
        '"DELETE\s.*"\s*\+\s*'
        '"WHERE\s.*"\s*\+\s*'
        'query\s*\(\s*".*"\s*\+'
        'createQuery\s*\(\s*".*"\s*\+'
        'createNativeQuery\s*\(\s*".*"\s*\+'
        'nativeQuery\s*=\s*true.*"\s*\+'
        'String\.format\s*\(\s*".*(?:SELECT|INSERT|UPDATE|DELETE|WHERE)'
    )

    while IFS= read -r file; do
        for pattern in "${sql_concat_patterns[@]}"; do
            local matches
            matches=$(grep -n -E "$pattern" "$file" 2>/dev/null || true)

            if [ -n "$matches" ]; then
                # Filter out parameterized queries (using ? or :param)
                local real_matches
                real_matches=$(echo "$matches" | grep -v -E '(\?|:param|:[\w]+)' || true)

                if [ -n "$real_matches" ]; then
                    found=1
                    findings="${findings}\n  ${file}:\n${real_matches}\n"
                fi
            fi
        done
    done < <(find_source_files "kt" "java")

    if [ "$found" -eq 1 ]; then
        log_fail "Potential SQL injection patterns detected (string concatenation in queries):"
        REPORT="${REPORT}\n--- SECURITY: SQL Injection Risk ---${findings}"
        REPORT="${REPORT}\n  FIX: Use parameterized queries with ? placeholders or named parameters (:paramName)\n"
        VIOLATIONS_FOUND=1
        return 1
    else
        log_pass "No SQL injection patterns detected"
        return 0
    fi
}

# --- Check for XSS vulnerabilities ---
check_xss_risks() {
    log_info "Scanning for XSS vulnerability patterns..."

    local findings=""
    local found=0

    # Check for unescaped output in templates
    local xss_patterns=(
        'innerHTML\s*='
        'dangerouslySetInnerHTML'
        'v-html\s*='
        '\$\{.*\}\s*(?!.*escape|.*encode|.*sanitize)'
        'document\.write\s*\('
        'eval\s*\('
        'response\.getWriter\(\)\.write\s*\(.*getParameter'
        'PrintWriter.*getParameter'
    )

    local template_files
    template_files=$(find "$PROJECT_ROOT/src" \( -name "*.html" -o -name "*.jsp" -o -name "*.ftl" -o -name "*.thymeleaf" -o -name "*.tsx" -o -name "*.jsx" -o -name "*.vue" \) -type f 2>/dev/null || true)

    local source_files
    source_files=$(find "$PROJECT_ROOT/src/main" \( -name "*.kt" -o -name "*.java" -o -name "*.ts" -o -name "*.js" \) -type f 2>/dev/null || true)

    local all_files="${template_files}"$'\n'"${source_files}"

    while IFS= read -r file; do
        [ -z "$file" ] && continue
        [ ! -f "$file" ] && continue

        for pattern in "${xss_patterns[@]}"; do
            local matches
            matches=$(grep -n -E "$pattern" "$file" 2>/dev/null || true)

            if [ -n "$matches" ]; then
                found=1
                findings="${findings}\n  ${file}:\n${matches}\n"
            fi
        done
    done <<< "$all_files"

    if [ "$found" -eq 1 ]; then
        log_fail "Potential XSS vulnerability patterns detected:"
        REPORT="${REPORT}\n--- SECURITY: XSS Risk ---${findings}"
        REPORT="${REPORT}\n  FIX: Always escape/encode user input before rendering. Use framework-provided escaping mechanisms.\n"
        VIOLATIONS_FOUND=1
        return 1
    else
        log_pass "No XSS vulnerability patterns detected"
        return 0
    fi
}

# --- Check for insecure configurations ---
check_insecure_config() {
    log_info "Scanning for insecure configurations..."

    local findings=""
    local found=0

    # Check application configuration files
    local config_files
    config_files=$(find "$PROJECT_ROOT/src" \( -name "application*.yml" -o -name "application*.yaml" -o -name "application*.properties" \) -type f 2>/dev/null || true)

    while IFS= read -r file; do
        [ -z "$file" ] && continue
        [ ! -f "$file" ] && continue

        # Check for disabled CSRF protection
        if grep -n -E 'csrf.*disable|csrf.*false' "$file" >/dev/null 2>&1; then
            found=1
            local match
            match=$(grep -n -E 'csrf.*disable|csrf.*false' "$file")
            findings="${findings}\n  ${file}: CSRF protection may be disabled\n    ${match}\n"
        fi

        # Check for wildcard CORS origins
        if grep -n -E 'allowed-origins.*\*|allowedOrigins.*\*|access-control-allow-origin.*\*' "$file" >/dev/null 2>&1; then
            found=1
            local match
            match=$(grep -n -E 'allowed-origins.*\*|allowedOrigins.*\*|access-control-allow-origin.*\*' "$file")
            findings="${findings}\n  ${file}: Wildcard CORS origin detected (allows any domain)\n    ${match}\n"
        fi

        # Check for debug mode enabled in non-dev profiles
        local filename
        filename=$(basename "$file")
        if [[ "$filename" != *"-dev"* ]] && [[ "$filename" != *"-local"* ]]; then
            if grep -n -E 'debug\s*[:=]\s*true' "$file" >/dev/null 2>&1; then
                found=1
                local match
                match=$(grep -n -E 'debug\s*[:=]\s*true' "$file")
                findings="${findings}\n  ${file}: Debug mode enabled in non-dev profile\n    ${match}\n"
            fi
        fi

        # Check for HTTP (non-TLS) external service URLs
        if grep -n -E 'http://[^l][^o][^c]' "$file" >/dev/null 2>&1; then
            local http_matches
            http_matches=$(grep -n -E 'http://[^l][^o][^c]' "$file" | grep -v 'localhost' | grep -v '127.0.0.1' || true)
            if [ -n "$http_matches" ]; then
                found=1
                findings="${findings}\n  ${file}: Non-TLS HTTP URL for external service\n    ${http_matches}\n"
            fi
        fi
    done <<< "$config_files"

    # Check for sensitive files that should not be in the repository
    local sensitive_files=(".env" ".env.local" "credentials.json" "service-account.json" "id_rsa" "id_ed25519" "*.pem" "*.key")
    for pattern in "${sensitive_files[@]}"; do
        local matched
        matched=$(find "$PROJECT_ROOT" -maxdepth 3 -name "$pattern" -not -path "*/.git/*" -not -path "*/build/*" -not -path "*/node_modules/*" 2>/dev/null || true)
        if [ -n "$matched" ]; then
            found=1
            findings="${findings}\n  Sensitive file in repository: ${matched}\n"
        fi
    done

    if [ "$found" -eq 1 ]; then
        log_fail "Insecure configuration patterns detected:"
        REPORT="${REPORT}\n--- SECURITY: Insecure Configuration ---${findings}\n"
        VIOLATIONS_FOUND=1
        return 1
    else
        log_pass "No insecure configuration patterns detected"
        return 0
    fi
}

# --- Main ---
main() {
    echo "========================================"
    echo "  Security Baseline Check"
    echo "========================================"
    echo "Project root: $PROJECT_ROOT"
    echo ""

    check_hardcoded_credentials || true
    check_sql_injection || true
    check_xss_risks || true
    check_insecure_config || true

    echo ""
    echo "========================================"

    if [ "$VIOLATIONS_FOUND" -eq 1 ]; then
        echo -e "${RED}BASELINE FAILED: Security issues detected${NC}"
        echo ""
        echo "Security finding details:"
        echo -e "$REPORT"
        echo ""
        echo "Fix all security findings and re-run this baseline."
        echo "If a finding is a false positive, add a comment explaining why and suppress it."
        exit 1
    else
        echo -e "${GREEN}BASELINE PASSED: No security issues detected${NC}"
        exit 0
    fi
}

main "$@"
