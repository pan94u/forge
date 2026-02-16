#!/bin/bash
#
# api-contract-baseline.sh
# Verifies that OpenAPI specifications are complete and consistent with the implementation.
# Checks: spec existence, schema completeness, endpoint coverage, error response format.
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
NC='\033[0m'

log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_info() { echo "[INFO] $1"; }

# --- Find OpenAPI spec files ---
find_openapi_specs() {
    find "$PROJECT_ROOT" \( -name "openapi.yaml" -o -name "openapi.yml" -o -name "openapi.json" \
         -o -name "api-spec.yaml" -o -name "api-spec.yml" -o -name "swagger.yaml" -o -name "swagger.yml" \) \
         -not -path "*/build/*" -not -path "*/node_modules/*" -not -path "*/.gradle/*" 2>/dev/null
}

# --- Check that OpenAPI specs exist ---
check_spec_existence() {
    log_info "Checking for OpenAPI specification files..."

    local specs
    specs=$(find_openapi_specs)

    if [ -z "$specs" ]; then
        # Check if there are any controllers that would need specs
        local controllers
        controllers=$(find "$PROJECT_ROOT/src/main" \( -name "*Controller.kt" -o -name "*Controller.java" \) \
                      -not -path "*/build/*" 2>/dev/null | head -1)

        if [ -n "$controllers" ]; then
            log_fail "Controllers found but no OpenAPI spec file exists"
            REPORT="${REPORT}\n--- Missing OpenAPI Specification ---\n"
            REPORT="${REPORT}Controllers exist but no openapi.yaml/yml/json found.\n"
            REPORT="${REPORT}Create an OpenAPI 3.0+ specification file.\n"
            VIOLATIONS_FOUND=1
            return 1
        else
            log_info "No controllers and no API specs found — skipping API contract checks"
            return 0
        fi
    fi

    local spec_count
    spec_count=$(echo "$specs" | wc -l | tr -d ' ')
    log_pass "Found ${spec_count} OpenAPI spec file(s)"

    while IFS= read -r spec; do
        log_info "  - $spec"
    done <<< "$specs"

    return 0
}

# --- Validate OpenAPI spec structure ---
check_spec_completeness() {
    log_info "Validating OpenAPI specification completeness..."

    local specs
    specs=$(find_openapi_specs)
    [ -z "$specs" ] && return 0

    while IFS= read -r spec; do
        log_info "Validating: $spec"

        local issues=""
        local has_issues=false

        # Check for required top-level fields
        if ! grep -q 'openapi:' "$spec" 2>/dev/null && ! grep -q '"openapi"' "$spec" 2>/dev/null; then
            has_issues=true
            issues="${issues}\n  - Missing 'openapi' version field"
        fi

        if ! grep -q 'info:' "$spec" 2>/dev/null && ! grep -q '"info"' "$spec" 2>/dev/null; then
            has_issues=true
            issues="${issues}\n  - Missing 'info' section"
        fi

        if ! grep -q 'paths:' "$spec" 2>/dev/null && ! grep -q '"paths"' "$spec" 2>/dev/null; then
            has_issues=true
            issues="${issues}\n  - Missing 'paths' section"
        fi

        # Check that each path has at least one operation with responses defined
        local paths_without_responses
        paths_without_responses=$(awk '
            /^  \/[^:]*:/ { current_path=$0 }
            /^    (get|post|put|patch|delete):/ { current_op=$0; has_responses=0 }
            /responses:/ { has_responses=1 }
            /^    (get|post|put|patch|delete):/ && !has_responses && current_op != "" {
                print "  - " current_path " " current_op " missing responses"
            }
        ' "$spec" 2>/dev/null || true)

        if [ -n "$paths_without_responses" ]; then
            has_issues=true
            issues="${issues}\n${paths_without_responses}"
        fi

        # Check for request body schemas on POST/PUT/PATCH endpoints
        local post_without_body
        post_without_body=$(awk '
            BEGIN { in_op=0; has_body=0; op_line="" }
            /^    (post|put|patch):/ { in_op=1; has_body=0; op_line=NR": "$0 }
            /requestBody:/ && in_op { has_body=1 }
            /^    [a-z]/ && in_op && !/requestBody/ && !/responses/ && !/summary/ && !/description/ && !/operationId/ && !/tags/ && !/security/ && !/parameters/ {
                if (!has_body) { print "  - Line " op_line " may be missing requestBody" }
                in_op=0
            }
        ' "$spec" 2>/dev/null || true)

        # Check for error response schemas
        local missing_error_responses=0

        # Check if 400 responses are defined for POST/PUT/PATCH
        if grep -q 'post:\|put:\|patch:' "$spec" 2>/dev/null; then
            if ! grep -q "'400'\|\"400\"" "$spec" 2>/dev/null; then
                has_issues=true
                issues="${issues}\n  - No 400 (Bad Request) response defined for mutation endpoints"
                missing_error_responses=1
            fi
        fi

        # Check if 401 response is defined when security is used
        if grep -q 'security:\|securitySchemes:' "$spec" 2>/dev/null; then
            if ! grep -q "'401'\|\"401\"" "$spec" 2>/dev/null; then
                has_issues=true
                issues="${issues}\n  - Security configured but no 401 (Unauthorized) response defined"
            fi
        fi

        # Check for consistent error response schema
        if grep -q 'ErrorResponse\|errorResponse\|error_response' "$spec" 2>/dev/null; then
            log_pass "Error response schema defined in $spec"
        elif [ "$missing_error_responses" -eq 0 ]; then
            log_warn "No standard ErrorResponse schema found — consider defining one in components/schemas"
        fi

        if [ "$has_issues" = true ]; then
            log_fail "Spec validation issues in $spec:${issues}"
            REPORT="${REPORT}\n--- API Spec Completeness Issues: $(basename "$spec") ---${issues}\n"
            VIOLATIONS_FOUND=1
        else
            log_pass "Spec structure is complete: $spec"
        fi
    done <<< "$specs"

    return 0
}

# --- Check endpoints in spec match controllers in code ---
check_endpoint_coverage() {
    log_info "Checking endpoint coverage: controllers vs OpenAPI spec..."

    local specs
    specs=$(find_openapi_specs)
    [ -z "$specs" ] && return 0

    # Extract endpoints from controllers
    local controller_endpoints=""
    local controller_files
    controller_files=$(find "$PROJECT_ROOT/src/main" \( -name "*Controller.kt" -o -name "*Controller.java" \) \
                       -not -path "*/build/*" 2>/dev/null || true)

    [ -z "$controller_files" ] && return 0

    while IFS= read -r file; do
        [ -z "$file" ] && continue

        # Extract @RequestMapping, @GetMapping, @PostMapping, etc.
        local mappings
        mappings=$(grep -n '@\(Request\|Get\|Post\|Put\|Patch\|Delete\)Mapping' "$file" 2>/dev/null || true)

        while IFS= read -r mapping; do
            [ -z "$mapping" ] && continue
            # Extract the path from the mapping annotation
            local path
            path=$(echo "$mapping" | grep -o '"[^"]*"' | head -1 | tr -d '"')
            if [ -n "$path" ]; then
                controller_endpoints="${controller_endpoints}${path}\n"
            fi
        done <<< "$mappings"
    done <<< "$controller_files"

    if [ -z "$controller_endpoints" ]; then
        log_info "No endpoint paths extracted from controllers — skipping coverage check"
        return 0
    fi

    # Check each controller endpoint exists in the spec
    local missing_from_spec=""
    local has_missing=false

    while IFS= read -r endpoint; do
        [ -z "$endpoint" ] && continue

        local found_in_spec=false
        while IFS= read -r spec; do
            if grep -q "$endpoint" "$spec" 2>/dev/null; then
                found_in_spec=true
                break
            fi
        done <<< "$specs"

        if [ "$found_in_spec" = false ]; then
            has_missing=true
            missing_from_spec="${missing_from_spec}\n  - ${endpoint}"
        fi
    done < <(echo -e "$controller_endpoints" | sort -u)

    if [ "$has_missing" = true ]; then
        log_fail "Controller endpoints not documented in OpenAPI spec:${missing_from_spec}"
        REPORT="${REPORT}\n--- Missing from API Spec ---${missing_from_spec}\n"
        REPORT="${REPORT}Add these endpoints to the OpenAPI specification.\n"
        VIOLATIONS_FOUND=1
        return 1
    else
        log_pass "All controller endpoints are documented in the OpenAPI spec"
        return 0
    fi
}

# --- Check for authentication requirements ---
check_auth_documentation() {
    log_info "Checking authentication documentation in API spec..."

    local specs
    specs=$(find_openapi_specs)
    [ -z "$specs" ] && return 0

    while IFS= read -r spec; do
        # Check if any controller uses security annotations
        local has_security_annotations
        has_security_annotations=$(grep -r '@PreAuthorize\|@Secured\|@RolesAllowed\|@WithMockUser' \
                                   "$PROJECT_ROOT/src/main" 2>/dev/null | head -1 || true)

        if [ -n "$has_security_annotations" ]; then
            # Verify spec has security schemes defined
            if ! grep -q 'securitySchemes:' "$spec" 2>/dev/null; then
                log_fail "Controllers use security annotations but spec has no securitySchemes defined"
                REPORT="${REPORT}\n--- Missing Security Schemes ---\n"
                REPORT="${REPORT}Add securitySchemes to the OpenAPI spec components section.\n"
                VIOLATIONS_FOUND=1
            else
                log_pass "Security schemes documented in spec"
            fi
        else
            log_info "No security annotations found in controllers — skipping auth documentation check"
        fi
    done <<< "$specs"

    return 0
}

# --- Try OpenAPI spec validation tool if available ---
check_with_validation_tool() {
    log_info "Checking for OpenAPI validation tools..."

    local specs
    specs=$(find_openapi_specs)
    [ -z "$specs" ] && return 0

    # Try spectral (popular OpenAPI linter)
    if command -v spectral &>/dev/null; then
        log_info "Running Spectral OpenAPI linter..."
        while IFS= read -r spec; do
            local output
            output=$(spectral lint "$spec" 2>&1 || true)
            local error_count
            error_count=$(echo "$output" | grep -c "error" || echo "0")

            if [ "$error_count" -gt 0 ]; then
                log_fail "Spectral found ${error_count} error(s) in $spec"
                REPORT="${REPORT}\n--- Spectral Linter Errors ---\n${output}\n"
                VIOLATIONS_FOUND=1
            else
                log_pass "Spectral validation passed for $spec"
            fi
        done <<< "$specs"
        return 0
    fi

    # Try swagger-cli
    if command -v swagger-cli &>/dev/null; then
        log_info "Running swagger-cli validation..."
        while IFS= read -r spec; do
            if swagger-cli validate "$spec" 2>/dev/null; then
                log_pass "swagger-cli validation passed for $spec"
            else
                log_fail "swagger-cli validation failed for $spec"
                VIOLATIONS_FOUND=1
            fi
        done <<< "$specs"
        return 0
    fi

    log_info "No OpenAPI validation tool found (spectral, swagger-cli). Install one for deeper validation."
    return 0
}

# --- Main ---
main() {
    echo "========================================"
    echo "  API Contract Baseline Check"
    echo "========================================"
    echo "Project root: $PROJECT_ROOT"
    echo ""

    check_spec_existence || true
    check_spec_completeness || true
    check_endpoint_coverage || true
    check_auth_documentation || true
    check_with_validation_tool || true

    echo ""
    echo "========================================"

    if [ "$VIOLATIONS_FOUND" -eq 1 ]; then
        echo -e "${RED}BASELINE FAILED: API contract issues detected${NC}"
        echo ""
        echo "API contract issue details:"
        echo -e "$REPORT"
        echo ""
        echo "Fix all API contract issues and re-run this baseline."
        exit 1
    else
        echo -e "${GREEN}BASELINE PASSED: API contracts are complete and consistent${NC}"
        exit 0
    fi
}

main "$@"
