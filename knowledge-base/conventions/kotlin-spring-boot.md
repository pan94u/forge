# Kotlin + Spring Boot 开发规范

## 项目约定

### 包结构
```
com.forge.{module}/
├── controller/    # REST Controllers
├── service/       # Business Services
├── model/         # DTOs and Data Classes
├── entity/        # JPA Entities
├── repository/    # Spring Data Repositories
├── config/        # Spring Configuration
└── websocket/     # WebSocket Handlers
```

### 命名规范
- **Controller**: `{Resource}Controller.kt` (e.g., `WorkspaceController.kt`)
- **Service**: `{Feature}Service.kt` (e.g., `ClaudeAgentService.kt`)
- **Entity**: `{Name}Entity.kt` (e.g., `ChatMessageEntity.kt`)
- **Repository**: `{Entity}Repository.kt` (e.g., `ChatSessionRepository.kt`)

### Kotlin 惯例
- 优先使用 `data class` 表示 DTO
- 使用 `val` 而非 `var`（不可变优先）
- 空安全：避免 `!!`，使用 `?.` 和 `?:` 操作符
- 使用 `sealed class` 表示有限状态集
- 集合操作优先使用函数式 API（`map`, `filter`, `fold`）

### Spring Boot 规范
- 配置通过 `application.yml` + 环境变量
- 数据库迁移使用 Flyway（`db/migration/V{n}__{description}.sql`）
- 异常处理在 Controller 层统一处理
- 日志使用 SLF4J + Logback
- 测试使用 JUnit 5 + MockK + AssertJ

### API 设计
- RESTful 风格：`GET /api/{resource}`, `POST /api/{resource}`, etc.
- 响应统一使用 JSON
- 错误响应包含 `code`, `message`, `details` 字段
- 分页参数：`page`, `size`, `sort`

### 依赖管理
- Spring Boot 版本由 root `build.gradle.kts` 统一管理
- Kotlin 版本由 root 统一管理（1.9.25）
- 子模块不声明显式版本，继承 root 配置
