---
name: forge-arch-check
description: Check architecture compliance
trigger: /forge-arch-check
---

# /forge-arch-check — Architecture Compliance

Scan the codebase for architecture violations:

1. **Dependency Direction**: Controllers → Services → Repositories (never reverse)
2. **No Cross-Layer Calls**: Controllers must not access repositories directly
3. **No Circular Dependencies**: Between modules
4. **API Contract Consistency**: OpenAPI spec matches actual endpoints
5. **Service Boundaries**: No direct database access across service boundaries

Output violations with file paths and specific lines.
