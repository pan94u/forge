---
name: spring-boot-patterns
description: "Forge 项目 Spring Boot 约定。构造器注入强制、@ConfigurationProperties 优于 @Value、RFC 7807 错误响应。"
trigger: when working with Spring annotations, controllers, services, or configuration
tags: [spring-boot, spring, dependency-injection, configuration]
version: "2.0"
scope: platform
category: foundation
---

# Spring Boot Patterns — Forge 项目约定

## 分层架构强制规则

```
Controller (HTTP) → Service (Business Logic) → Repository (Data Access)
```

- **Controller 禁止直接访问 Repository**（必须通过 Service）
- **Service 禁止导入 HTTP 相关类**（HttpServletRequest, ResponseEntity, @PathVariable 等）
- **Repository 禁止包含业务逻辑**

## 依赖注入规则

- **构造器注入强制**：禁止 `@Autowired` 字段注入
- Kotlin `@Service` 类使用主构造器注入

## 配置管理规则

- **@ConfigurationProperties 优于 @Value**：禁止 `@Value` 散落在各类中
- 所有配置必须集中到 `@ConfigurationProperties` data class
- 嵌套配置用嵌套 data class（镜像 YAML 结构）
- 必须提供合理默认值（应用无外部配置也能启动）
- 使用 Bean Validation（`@NotBlank`, `@Min`）在启动时校验
- 时间类型用 `Duration`（Spring 自动解析 `5s`, `30m`）

## 错误处理规则

- 使用 `@ControllerAdvice` + RFC 7807 `ProblemDetail`
- 错误响应必须包含 `correlationId` 和 `timestamp`
- 业务异常统一继承 `BusinessException`
- 验证失败返回 422 + 字段级错误列表

## @Async 规则

- `@Async` 方法返回类型只能是 `void` 或 `CompletableFuture<T>`
- 必须配置自定义线程池（禁止 `SimpleAsyncTaskExecutor`）
- `CompletableFuture.get()` 必须设置超时

## Cache 规则

- Cache TTL 和 maxSize 必须显式配置（禁止无界缓存）
- 只缓存读多写少的数据
- 写操作使用 `@CachePut` 保持一致性
- 禁止缓存可变对象

## Event 规则

- 事件用不可变 `data class`，只含 ID 和关键数据
- 事务性副作用用 `@TransactionalEventListener(AFTER_COMMIT)`
- fire-and-forget 副作用用 `@Async` + `@EventListener`
