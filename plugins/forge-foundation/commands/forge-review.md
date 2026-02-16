---
name: forge-review
description: AI-powered code review combining multiple Skills
trigger: /forge-review
---

# /forge-review — Code Review

When the user runs `/forge-review`, perform a comprehensive code review:

## Steps

1. **Get Changes**: Run `git diff --staged` or `git diff HEAD~1` to get changed files
2. **Convention Check**: Load relevant Foundation Skills (java-conventions, kotlin-conventions, spring-boot-patterns) and check all changes against conventions
3. **Security Scan**: Load security-practices skill. Check for:
   - Hardcoded credentials
   - SQL injection vulnerabilities
   - Missing input validation
   - Insecure configurations
4. **Test Coverage**: Load testing-standards skill. Check:
   - New code has corresponding tests
   - Test naming follows conventions
   - Coverage targets met
5. **API Contract**: If API changes, check against api-design skill
6. **Architecture**: Check dependency direction and layering rules

## Output Format

```markdown
## Code Review Report

### Summary
- Files changed: X
- Issues found: Y (Z critical)

### Critical Issues
- [SECURITY] Hardcoded API key in PaymentConfig.kt:15
- [SQL_INJECTION] String concatenation in UserRepository.kt:42

### Warnings
- [CONVENTION] Method naming doesn't follow should_X_when_Y in OrderServiceTest.kt:30
- [COVERAGE] No tests for new RefundService.cancelRefund() method

### Suggestions
- [IMPROVEMENT] Consider using @ConfigurationProperties instead of @Value in line 20
```
