# Forge Eval — 端到端验收测试指南

> 手动逐步操作，验证评估引擎从 API 到数据库的完整链路。

## 前置条件

```bash
# 1. 确保 JDK 21
java -version  # 需要 21+

# 2. 构建后端（跳过测试加速）
./gradlew :web-ide:backend:bootJar -x test

# 3. 启动后端（H2 内存库，安全关闭）
cd web-ide/backend
java -jar build/libs/backend-0.1.0-SNAPSHOT.jar \
  --spring.datasource.url=jdbc:h2:mem:evaltest \
  --forge.security.enabled=false

# 等待看到: Started ForgeWebIdeApplication
# 后端运行在 http://localhost:8080
```

---

## 场景 1：创建评估套件

> 验证套件 CRUD 基础功能

### TC-1.1 创建 Forge 编码 Agent 评估套件

```bash
curl -s -X POST http://localhost:8080/api/eval/v1/suites \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "SuperAgent Coding Eval",
    "description": "Forge SuperAgent 编码能力评估",
    "platform": "FORGE",
    "agentType": "CODING",
    "lifecycle": "CAPABILITY",
    "tags": ["coding", "phase1"]
  }' | jq .
```

**预期**:
- [ ] HTTP 201
- [ ] 返回 JSON 含 `id` (UUID)
- [ ] `platform` = `"FORGE"`
- [ ] `taskCount` = `0`

**记录**: `SUITE_ID=<返回的 id>`

### TC-1.2 创建 Synapse 对话 Agent 评估套件

```bash
curl -s -X POST http://localhost:8080/api/eval/v1/suites \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Synapse Conversation Eval",
    "description": "Synapse 对话质量评估",
    "platform": "SYNAPSE",
    "agentType": "CONVERSATIONAL",
    "tags": ["synapse", "conversation"]
  }' | jq .
```

**预期**:
- [ ] HTTP 201
- [ ] `platform` = `"SYNAPSE"`

### TC-1.3 列出所有套件

```bash
curl -s http://localhost:8080/api/eval/v1/suites | jq .
```

**预期**:
- [ ] `totalElements` = `2`
- [ ] `content` 数组包含两个套件

### TC-1.4 按 platform 过滤

```bash
curl -s 'http://localhost:8080/api/eval/v1/suites?platform=FORGE' | jq .
```

**预期**:
- [ ] `totalElements` = `1`
- [ ] 只返回 FORGE 套件

### TC-1.5 查询不存在的套件

```bash
curl -s -w "\nHTTP_CODE:%{http_code}\n" \
  http://localhost:8080/api/eval/v1/suites/00000000-0000-0000-0000-000000000000
```

**预期**:
- [ ] HTTP 404
- [ ] 返回 `{"error": "Suite not found: ..."}`

---

## 场景 2：添加评估任务

> 验证任务创建 + 评分器配置

### TC-2.1 添加带 Code-Based 断言的任务

```bash
curl -s -X POST http://localhost:8080/api/eval/v1/suites/${SUITE_ID}/tasks \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Hello World 函数生成",
    "description": "Agent 应该生成一个 Kotlin hello world 函数",
    "prompt": "用 Kotlin 写一个 hello world 函数",
    "graderConfigs": [{
      "type": "CODE_BASED",
      "assertions": [
        {"type": "contains", "expected": "fun ", "description": "包含函数定义"},
        {"type": "contains", "expected": "hello", "description": "包含 hello", "caseSensitive": false},
        {"type": "not_contains", "expected": "TODO", "description": "无 TODO"},
        {"type": "matches_pattern", "expected": "fun\\s+\\w+", "description": "函数签名格式正确"}
      ]
    }],
    "difficulty": "EASY",
    "tags": ["kotlin", "hello-world"],
    "baselinePassRate": 0.9
  }' | jq .
```

**预期**:
- [ ] HTTP 201
- [ ] 返回完整 EvalTask，含 `graderConfigs[0].assertions` 数组（4 个断言）
- [ ] `difficulty` = `"EASY"`

**记录**: `TASK1_ID=<返回的 id>`

### TC-2.2 添加带工具断言的任务

```bash
curl -s -X POST http://localhost:8080/api/eval/v1/suites/${SUITE_ID}/tasks \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Bug 修复 — 枚举序列化",
    "description": "Agent 应使用 workspace 工具修复枚举大小写问题",
    "prompt": "修复 WorkspaceStatus 枚举序列化为大写的 bug",
    "graderConfigs": [{
      "type": "CODE_BASED",
      "assertions": [
        {"type": "contains", "expected": "JsonValue", "description": "使用 @JsonValue"},
        {"type": "tool_used", "expected": "workspace_write_file", "description": "写入修复代码"},
        {"type": "tool_not_used", "expected": "workspace_compile", "description": "先不编译（分析阶段）"},
        {"type": "tool_call_order", "expected": "search_knowledge,workspace_write_file", "description": "先搜后写"}
      ]
    }],
    "difficulty": "MEDIUM",
    "tags": ["bugfix", "enum"]
  }' | jq .
```

**预期**:
- [ ] HTTP 201
- [ ] 4 个断言含 `tool_used`、`tool_not_used`、`tool_call_order` 类型

**记录**: `TASK2_ID=<返回的 id>`

### TC-2.3 列出套件任务

```bash
curl -s http://localhost:8080/api/eval/v1/suites/${SUITE_ID}/tasks | jq '.[].name'
```

**预期**:
- [ ] 返回 2 个任务名

---

## 场景 3：执行评估运行（单试）

> 验证 Engine 编排 → Grader 评分 → DB 持久化 → 报告生成

### TC-3.1 启动单试运行

```bash
curl -s -X POST http://localhost:8080/api/eval/v1/runs \
  -H 'Content-Type: application/json' \
  -d '{
    "suiteId": "'${SUITE_ID}'",
    "trialsPerTask": 1
  }' | jq .
```

**预期**:
- [ ] HTTP 201
- [ ] `status` = `"COMPLETED"`（同步执行）
- [ ] `trialsPerTask` = `1`
- [ ] `trials` 数组有 2 个元素（2 个任务各 1 试）
- [ ] 每个 trial 有 `outcome`、`score`、`durationMs`
- [ ] `summary.totalTasks` = `2`
- [ ] `summary.totalTrials` = `2`

**记录**: `RUN1_ID=<返回的 id>`

### TC-3.2 查询运行详情

```bash
curl -s http://localhost:8080/api/eval/v1/runs/${RUN1_ID} | jq '{
  status,
  trialsPerTask,
  summary: .summary,
  trialCount: (.trials | length),
  firstTrial: .trials[0]
}'
```

**预期**:
- [ ] 与 TC-3.1 返回一致
- [ ] `trials[0].grades` 不为空

### TC-3.3 获取 JSON 报告

```bash
curl -s http://localhost:8080/api/eval/v1/runs/${RUN1_ID}/report | jq '{
  suiteName,
  platform,
  "taskCount": (.taskResults | length),
  "summaryPassRate": .summary.overallPassRate
}'
```

**预期**:
- [ ] `suiteName` = `"SuperAgent Coding Eval"`
- [ ] `platform` = `"FORGE"`
- [ ] `taskCount` = `2`

### TC-3.4 获取 Markdown 报告

```bash
curl -s 'http://localhost:8080/api/eval/v1/runs/'${RUN1_ID}'/report?format=markdown'
```

**预期**:
- [ ] 输出以 `# Forge Eval Report` 开头
- [ ] 包含 Summary 表格
- [ ] 包含每个任务的断言结果（`[x]` 或 `[ ]`）

---

## 场景 4：多试运行 + Pass@k / Pass^k

> 验证非确定性处理能力

### TC-4.1 启动 k=3 多试运行

```bash
curl -s -X POST http://localhost:8080/api/eval/v1/runs \
  -H 'Content-Type: application/json' \
  -d '{
    "suiteId": "'${SUITE_ID}'",
    "trialsPerTask": 3
  }' | jq '{
    status,
    trialsPerTask,
    "trialCount": (.trials | length),
    "passAtK": .summary.passAtK,
    "passPowerK": .summary.passPowerK,
    "overallPassRate": .summary.overallPassRate
  }'
```

**预期**:
- [ ] `trialsPerTask` = `3`
- [ ] `trialCount` = `6`（2 任务 × 3 试）
- [ ] `passAtK` 和 `passPowerK` 字段存在且为数值（非 null）
- [ ] 每个任务的 3 个 trial 编号分别为 1、2、3

**记录**: `RUN3_ID=<返回的 id>`

### TC-4.2 验证试次编号

```bash
curl -s http://localhost:8080/api/eval/v1/runs/${RUN3_ID} | \
  jq '[.trials[] | {taskName, trialNumber, outcome}]'
```

**预期**:
- [ ] 6 行记录
- [ ] 每个 taskName 各出现 3 次（trialNumber 1/2/3）

---

## 场景 5：外部轨迹提交（模拟 Synapse 集成）

> 验证跨平台评估能力——外部 Agent 提交轨迹由 Forge Eval 评分

### TC-5.1 提交带工具调用的轨迹

```bash
curl -s -X POST http://localhost:8080/api/eval/v1/transcripts \
  -H 'Content-Type: application/json' \
  -d '{
    "suiteId": "'${SUITE_ID}'",
    "taskId": "'${TASK2_ID}'",
    "source": "EXTERNAL",
    "turns": [
      {"role": "user", "content": "修复 WorkspaceStatus 枚举序列化为大写的 bug"},
      {
        "role": "assistant",
        "content": "我来分析这个问题。根据知识库，Kotlin enum 默认序列化为大写。需要添加 @JsonValue 注解。",
        "toolCalls": [
          {"toolName": "search_knowledge", "arguments": {"query": "enum serialization"}}
        ]
      },
      {"role": "user", "content": "请修复"},
      {
        "role": "assistant",
        "content": "已修复，为 WorkspaceStatus 添加了 @JsonValue 属性返回小写字符串。",
        "toolCalls": [
          {"toolName": "workspace_write_file", "arguments": {"path": "WorkspaceStatus.kt"}}
        ]
      }
    ],
    "metadata": {"agent": "synapse-v1", "model": "claude-sonnet-4-6"}
  }' | jq .
```

**预期**:
- [ ] HTTP 201
- [ ] `transcriptId` 存在
- [ ] `grades` 数组不为空
- [ ] 评分结果反映断言通过情况：
  - `contains "JsonValue"` → 通过（assistant 内容包含 JsonValue）
  - `tool_used "workspace_write_file"` → 通过
  - `tool_not_used "workspace_compile"` → 通过
  - `tool_call_order "search_knowledge,workspace_write_file"` → 通过

**记录**: `TRANSCRIPT_ID=<返回的 transcriptId>`

### TC-5.2 查询轨迹详情

```bash
curl -s http://localhost:8080/api/eval/v1/transcripts/${TRANSCRIPT_ID} | jq '{
  source,
  "turnCount": (.turns | length),
  "toolCalls": [.toolCallSummary[].toolName]
}'
```

**预期**:
- [ ] `source` = `"EXTERNAL"`
- [ ] `turnCount` = `4`
- [ ] `toolCalls` = `["search_knowledge", "workspace_write_file"]`

### TC-5.3 提交不满足断言的轨迹

```bash
curl -s -X POST http://localhost:8080/api/eval/v1/transcripts \
  -H 'Content-Type: application/json' \
  -d '{
    "suiteId": "'${SUITE_ID}'",
    "taskId": "'${TASK2_ID}'",
    "source": "EXTERNAL",
    "turns": [
      {"role": "user", "content": "修复 bug"},
      {"role": "assistant", "content": "我不确定怎么修，建议手动处理。"}
    ],
    "metadata": {"agent": "bad-agent"}
  }' | jq '.grades'
```

**预期**:
- [ ] `grades[0].passed` = `false`
- [ ] `grades[0].score` < `1.0`
- [ ] 断言结果中 `contains "JsonValue"` 失败
- [ ] 断言结果中 `tool_used "workspace_write_file"` 失败

---

## 场景 6：回归检测

> 验证两次运行之间的回归比较

### TC-6.1 比较两次运行

```bash
curl -s 'http://localhost:8080/api/eval/v1/regressions?suiteId='${SUITE_ID}'&currentRunId='${RUN3_ID}'&baselineRunId='${RUN1_ID} | jq .
```

**预期**:
- [ ] 返回 `regressions` 数组
- [ ] `hasRegressions` 为布尔值
- [ ] 如果两次运行结果一致，`regressions` 为空数组

---

## 验收汇总

| 场景 | TC 数量 | 描述 |
|------|---------|------|
| 1. 套件 CRUD | 5 | 创建/列出/过滤/404 |
| 2. 任务管理 | 3 | Code-Based + 工具断言 |
| 3. 单试运行 | 4 | Engine 编排 → 评分 → 报告（JSON+MD） |
| 4. 多试运行 | 2 | Pass@k / Pass^k 指标 |
| 5. 轨迹提交 | 3 | 外部 Agent → 评分（pass + fail 场景） |
| 6. 回归检测 | 1 | 跨运行比较 |
| **合计** | **18** | |

### 价值验证清单

- [ ] **核心链路**: Suite → Task → Run → Grade → Report 端到端可用
- [ ] **12 种断言**: contains/not_contains/matches_pattern/json_schema/json_path/tool_used/tool_not_used/tool_call_count/tool_call_order/turn_count_max/structure/profile_routed
- [ ] **多试运行**: k>1 时报告含 Pass@k 和 Pass^k
- [ ] **跨平台**: Synapse 可通过 POST /transcripts 提交轨迹评分
- [ ] **回归守护**: 两次运行可比较，含统计显著性
- [ ] **报告双格式**: JSON（程序消费）+ Markdown（人类阅读）
