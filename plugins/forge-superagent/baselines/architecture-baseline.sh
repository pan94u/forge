#!/bin/bash
#
# architecture-baseline.sh
# Validates architectural rules:
# - No cross-layer calls (presentation must not call data layer directly)
# - Correct dependency direction (inner layers do not depend on outer layers)
# - No circular dependencies between modules
# - Service boundary integrity
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

# --- Detect project package structure ---
detect_base_package() {
    # Find the most common base package from source files
    local packages
    packages=$(grep -rh "^package " "$PROJECT_ROOT/src/main" --include="*.kt" --include="*.java" 2>/dev/null | \
               sed 's/package //' | sed 's/;$//' | sort | head -20)

    if [ -z "$packages" ]; then
        echo ""
        return
    fi

    # Find the common prefix
    local base_package
    base_package=$(echo "$packages" | sed 's/\.[^.]*$//' | sort | uniq -c | sort -rn | head -1 | awk '{print $2}')
    echo "$base_package"
}

# --- Check no cross-layer calls ---
check_no_cross_layer_calls() {
    log_info "Checking for cross-layer dependency violations..."

    local src_main="$PROJECT_ROOT/src/main"
    if [ ! -d "$src_main" ]; then
        log_info "No src/main directory found — skipping cross-layer check"
        return 0
    fi

    local violations=""
    local found=0

    # Rule 1: Controllers should NOT import Repository classes directly
    log_info "  Checking: Controllers must not depend on repositories directly..."

    local controller_files
    controller_files=$(find "$src_main" \( -name "*Controller.kt" -o -name "*Controller.java" \) \
                       -not -path "*/build/*" 2>/dev/null || true)

    while IFS= read -r file; do
        [ -z "$file" ] && continue

        # Check for direct repository imports
        local repo_imports
        repo_imports=$(grep -n 'import.*\.repository\.\|import.*\.repo\.\|import.*Repository$' "$file" 2>/dev/null || true)

        if [ -n "$repo_imports" ]; then
            found=1
            violations="${violations}\n  CROSS-LAYER: Controller directly depends on Repository"
            violations="${violations}\n    File: ${file}"
            violations="${violations}\n    ${repo_imports}"
            violations="${violations}\n    FIX: Controllers should depend on Service classes, not Repositories\n"
        fi

        # Check for direct JPA/JDBC imports in controllers
        local data_imports
        data_imports=$(grep -n 'import.*javax\.persistence\.\|import.*jakarta\.persistence\.\|import.*jdbc\.\|import.*JdbcTemplate\|import.*EntityManager' "$file" 2>/dev/null || true)

        if [ -n "$data_imports" ]; then
            found=1
            violations="${violations}\n  CROSS-LAYER: Controller directly uses persistence API"
            violations="${violations}\n    File: ${file}"
            violations="${violations}\n    ${data_imports}"
            violations="${violations}\n    FIX: Move data access logic to Repository/Service layer\n"
        fi
    done <<< "$controller_files"

    # Rule 2: Domain/model classes should NOT import from controller/web layer
    log_info "  Checking: Domain classes must not depend on web layer..."

    local domain_dirs=("domain" "model" "entity" "core")
    for dir_name in "${domain_dirs[@]}"; do
        local domain_files
        domain_files=$(find "$src_main" -path "*/${dir_name}/*" \( -name "*.kt" -o -name "*.java" \) \
                       -not -path "*/build/*" 2>/dev/null || true)

        while IFS= read -r file; do
            [ -z "$file" ] && continue

            local web_imports
            web_imports=$(grep -n 'import.*\.controller\.\|import.*\.web\.\|import.*\.api\.\|import.*springframework.*web\.\|import.*\.dto\.' "$file" 2>/dev/null || true)

            if [ -n "$web_imports" ]; then
                found=1
                violations="${violations}\n  CROSS-LAYER: Domain/model class depends on web/controller layer"
                violations="${violations}\n    File: ${file}"
                violations="${violations}\n    ${web_imports}"
                violations="${violations}\n    FIX: Domain classes must not depend on web layer (dependency inversion)\n"
            fi
        done <<< "$domain_files"
    done

    # Rule 3: Repository classes should NOT import from controller/web layer
    log_info "  Checking: Repositories must not depend on web layer..."

    local repo_files
    repo_files=$(find "$src_main" \( -path "*/repository/*" -o -path "*/repo/*" \) \
                 \( -name "*.kt" -o -name "*.java" \) -not -path "*/build/*" 2>/dev/null || true)

    while IFS= read -r file; do
        [ -z "$file" ] && continue

        local web_imports
        web_imports=$(grep -n 'import.*\.controller\.\|import.*\.web\.\|import.*\.dto\.' "$file" 2>/dev/null || true)

        if [ -n "$web_imports" ]; then
            found=1
            violations="${violations}\n  CROSS-LAYER: Repository depends on web/controller layer"
            violations="${violations}\n    File: ${file}"
            violations="${violations}\n    ${web_imports}"
            violations="${violations}\n    FIX: Repository layer must not depend on web/presentation layer\n"
        fi
    done <<< "$repo_files"

    if [ "$found" -eq 1 ]; then
        log_fail "Cross-layer dependency violations found"
        REPORT="${REPORT}\n--- Architecture: Cross-Layer Violations ---${violations}"
        VIOLATIONS_FOUND=1
        return 1
    else
        log_pass "No cross-layer dependency violations found"
        return 0
    fi
}

# --- Check dependency direction ---
check_dependency_direction() {
    log_info "Checking dependency direction (Clean Architecture rules)..."

    local src_main="$PROJECT_ROOT/src/main"
    if [ ! -d "$src_main" ]; then
        return 0
    fi

    local violations=""
    local found=0

    # Architectural layers (from inner to outer):
    # 1. Domain/Entity (innermost — no outward dependencies)
    # 2. Use Cases / Services
    # 3. Interface Adapters (Controllers, Repositories)
    # 4. Frameworks & Drivers (Spring, JPA configs)

    # Check: Domain classes should not depend on Spring framework
    local domain_dirs=("domain" "model" "entity" "core")
    for dir_name in "${domain_dirs[@]}"; do
        local domain_files
        domain_files=$(find "$src_main" -path "*/${dir_name}/*" \( -name "*.kt" -o -name "*.java" \) \
                       -not -path "*/build/*" -not -name "*Entity.kt" -not -name "*Entity.java" 2>/dev/null || true)

        while IFS= read -r file; do
            [ -z "$file" ] && continue

            # Domain should not import Spring (except JPA annotations which are acceptable)
            local spring_imports
            spring_imports=$(grep -n 'import.*org\.springframework' "$file" 2>/dev/null | \
                            grep -v 'stereotype\|beans\|transaction' || true)

            # Filter out JPA annotations (commonly used in domain entities)
            spring_imports=$(echo "$spring_imports" | grep -v 'data\.annotation' || true)

            if [ -n "$spring_imports" ]; then
                found=1
                violations="${violations}\n  DEPENDENCY DIRECTION: Domain class depends on Spring framework"
                violations="${violations}\n    File: ${file}"
                violations="${violations}\n    ${spring_imports}"
                violations="${violations}\n    FIX: Domain layer should be framework-independent\n"
            fi
        done <<< "$domain_files"
    done

    # Check: Service classes should not depend on HTTP concerns
    local service_files
    service_files=$(find "$src_main" -path "*/service/*" \( -name "*.kt" -o -name "*.java" \) \
                    -not -path "*/build/*" 2>/dev/null || true)

    while IFS= read -r file; do
        [ -z "$file" ] && continue

        local http_imports
        http_imports=$(grep -n 'import.*HttpServletRequest\|import.*HttpServletResponse\|import.*ResponseEntity\|import.*HttpStatus\|import.*RequestMapping' "$file" 2>/dev/null || true)

        if [ -n "$http_imports" ]; then
            found=1
            violations="${violations}\n  DEPENDENCY DIRECTION: Service class depends on HTTP/web concerns"
            violations="${violations}\n    File: ${file}"
            violations="${violations}\n    ${http_imports}"
            violations="${violations}\n    FIX: Service layer should not know about HTTP — move web logic to controllers\n"
        fi
    done <<< "$service_files"

    if [ "$found" -eq 1 ]; then
        log_fail "Dependency direction violations found"
        REPORT="${REPORT}\n--- Architecture: Dependency Direction Violations ---${violations}"
        VIOLATIONS_FOUND=1
        return 1
    else
        log_pass "Dependency direction is correct"
        return 0
    fi
}

# --- Check for circular dependencies between modules ---
check_circular_dependencies() {
    log_info "Checking for circular dependencies between modules..."

    local src_main="$PROJECT_ROOT/src/main"
    if [ ! -d "$src_main" ]; then
        return 0
    fi

    local base_package
    base_package=$(detect_base_package)

    if [ -z "$base_package" ]; then
        log_info "Could not detect base package — skipping circular dependency check"
        return 0
    fi

    log_info "  Base package: $base_package"

    # Build a dependency map: for each package, which other packages does it import?
    local dep_file
    dep_file=$(mktemp)
    trap "rm -f $dep_file" EXIT

    # Find all unique sub-packages under the base package
    local sub_packages
    sub_packages=$(grep -rh "^package " "$src_main" --include="*.kt" --include="*.java" 2>/dev/null | \
                   sed 's/package //' | sed 's/;$//' | \
                   grep "^${base_package}\." | \
                   sed "s/^${base_package}\.//" | \
                   sed 's/\..*//' | \
                   sort -u)

    if [ -z "$sub_packages" ]; then
        log_info "No sub-packages found under $base_package — skipping"
        return 0
    fi

    # For each sub-package, check what other sub-packages it imports
    local violations=""
    local found=0

    while IFS= read -r pkg_a; do
        [ -z "$pkg_a" ] && continue

        local pkg_a_files
        pkg_a_files=$(find "$src_main" -path "*/${pkg_a}/*" \( -name "*.kt" -o -name "*.java" \) 2>/dev/null || true)

        while IFS= read -r pkg_b; do
            [ -z "$pkg_b" ] && continue
            [ "$pkg_a" = "$pkg_b" ] && continue

            # Does package A import from package B?
            local a_imports_b=false
            while IFS= read -r file; do
                [ -z "$file" ] && continue
                if grep -q "import.*${base_package}\.${pkg_b}\." "$file" 2>/dev/null; then
                    a_imports_b=true
                    break
                fi
            done <<< "$pkg_a_files"

            if [ "$a_imports_b" = true ]; then
                # Does package B also import from package A? (circular!)
                local pkg_b_files
                pkg_b_files=$(find "$src_main" -path "*/${pkg_b}/*" \( -name "*.kt" -o -name "*.java" \) 2>/dev/null || true)

                local b_imports_a=false
                while IFS= read -r file; do
                    [ -z "$file" ] && continue
                    if grep -q "import.*${base_package}\.${pkg_a}\." "$file" 2>/dev/null; then
                        b_imports_a=true
                        break
                    fi
                done <<< "$pkg_b_files"

                if [ "$b_imports_a" = true ]; then
                    found=1
                    violations="${violations}\n  CIRCULAR: ${pkg_a} <-> ${pkg_b}"
                    violations="${violations}\n    ${pkg_a} imports from ${pkg_b} AND ${pkg_b} imports from ${pkg_a}"
                    violations="${violations}\n    FIX: Extract shared code to a common package or use interfaces to break the cycle\n"
                fi
            fi
        done <<< "$sub_packages"
    done <<< "$sub_packages"

    if [ "$found" -eq 1 ]; then
        log_fail "Circular dependencies detected"
        REPORT="${REPORT}\n--- Architecture: Circular Dependencies ---${violations}"
        VIOLATIONS_FOUND=1
        return 1
    else
        log_pass "No circular dependencies between modules"
        return 0
    fi
}

# --- Check service boundary integrity ---
check_service_boundaries() {
    log_info "Checking service boundary integrity..."

    local src_main="$PROJECT_ROOT/src/main"
    if [ ! -d "$src_main" ]; then
        return 0
    fi

    local violations=""
    local found=0

    # Check: services should not directly access another service's database tables
    # Detect by looking for repository imports from other service modules
    local service_modules
    service_modules=$(find "$PROJECT_ROOT" -maxdepth 2 -name "build.gradle.kts" -o -name "build.gradle" -o -name "pom.xml" 2>/dev/null | \
                      xargs -I {} dirname {} | sort -u)

    if [ "$(echo "$service_modules" | wc -l | tr -d ' ')" -le 1 ]; then
        log_info "Single-module project — skipping cross-service boundary check"
        return 0
    fi

    # In a multi-module project, check that modules don't import each other's repositories
    while IFS= read -r module_a; do
        [ -z "$module_a" ] && continue
        local module_a_name
        module_a_name=$(basename "$module_a")

        while IFS= read -r module_b; do
            [ -z "$module_b" ] && continue
            local module_b_name
            module_b_name=$(basename "$module_b")
            [ "$module_a_name" = "$module_b_name" ] && continue

            # Check if module A imports repositories from module B
            local cross_repo_imports
            cross_repo_imports=$(grep -rn "import.*${module_b_name}.*repository\.\|import.*${module_b_name}.*repo\." \
                                 "$module_a/src/main" 2>/dev/null || true)

            if [ -n "$cross_repo_imports" ]; then
                found=1
                violations="${violations}\n  BOUNDARY VIOLATION: ${module_a_name} directly accesses ${module_b_name}'s repository"
                violations="${violations}\n    ${cross_repo_imports}"
                violations="${violations}\n    FIX: Use ${module_b_name}'s API/client instead of direct repository access\n"
            fi
        done <<< "$service_modules"
    done <<< "$service_modules"

    if [ "$found" -eq 1 ]; then
        log_fail "Service boundary violations detected"
        REPORT="${REPORT}\n--- Architecture: Service Boundary Violations ---${violations}"
        VIOLATIONS_FOUND=1
        return 1
    else
        log_pass "Service boundaries are intact"
        return 0
    fi
}

# --- Main ---
main() {
    echo "========================================"
    echo "  Architecture Baseline Check"
    echo "========================================"
    echo "Project root: $PROJECT_ROOT"
    echo ""

    check_no_cross_layer_calls || true
    check_dependency_direction || true
    check_circular_dependencies || true
    check_service_boundaries || true

    echo ""
    echo "========================================"

    if [ "$VIOLATIONS_FOUND" -eq 1 ]; then
        echo -e "${RED}BASELINE FAILED: Architecture violations detected${NC}"
        echo ""
        echo "Architecture violation details:"
        echo -e "$REPORT"
        echo ""
        echo "Fix all architectural violations and re-run this baseline."
        exit 1
    else
        echo -e "${GREEN}BASELINE PASSED: Architecture rules are satisfied${NC}"
        exit 0
    fi
}

main "$@"
