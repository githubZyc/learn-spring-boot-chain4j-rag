# 数据库知识库：一期补完与二期产品计划

| 项 | 内容 |
|---|---|
| 文档名称 | 数据库知识库一期补完与二期产品计划 |
| 版本 | v1.0 |
| 日期 | 2026-11-12 |
| 关联 PRD | `docs/PRD-企业级知识库平台演进.md` v1.1 |
| 文档状态 | 待评审 / 待排期 |
| 适用对象 | 产品、后端、前端、测试 |

---

## 1. 结论先行

### 1.1 一期还没结束

当前已跑通「Schema 同步 → 找表 → 多表 NL2SQL → 脱敏」，但这只是**能力闭环**，还不是**产品闭环**。缺 5 个企业级基础件：

1. **数据源管理**：现在只能用 `.env`，不能多数据源、不能切换、不能测试连接。
2. **审计与历史**：现在只有应用日志，无法回答“谁在何时查了什么”。
3. **样例行 + 关系提示**：模型看不到真实枚举值，SQL 仍会猜字段（例如把 `qc_status` 猜成 `archive_status`）。
4. **评估体系**：没有量化，改提示词/模型只能靠感觉。
5. **结果分页/导出**：只有 200 行截断，不能翻页、不能导出、不能取消长查询。

### 1.2 推荐顺序

```text
一期补完（P1.5，约 3 个 Sprint）
  数据源管理 → 审计 → 样例/关系 → 评估基线 → 分页/导出/状态

二期（P2，约 3 个 Sprint）
  统一路由 → 可观测 → 权限/多租户 → 多数据源 → 查询规划 → 缓存/Agent 试点
```

### 1.3 一句话建议

> **先用 3 个 Sprint 把“能配置、能追溯、能量化”补齐，再进入二期做“更聪明”。**

### 1.4 现在不要做

- ❌ 全量业务数据向量化
- ❌ 无评估集就上多 Agent 自治
- ❌ 无权限设计就接生产医疗数据
- ❌ 跨库联邦查询（先把单库多表做稳）
- ❌ 复杂 BI/报表平台（不是当前阶段的核心）

---

## 2. 现状盘点（代码层）

| 能力 | 代码位置 | 状态 | 企业级缺口 |
|---|---|---|---|
| 文件入库 | `IngestionService` / `TextExtractorRegistry` | ✅ | 无 |
| 文档问答 | `ChatStreamService` / `RagAssistant` | ✅ | 路由/混合未做 |
| 数据源 SPI | `KnowledgeConnector` / `KnowledgeConnectorRegistry` | ✅ | 只有 MySQL |
| Schema 同步 | `MySqlSchemaExtractor` / `SchemaIndexService` | ✅ | 只有全量，无增量/漂移 |
| 找表 | `SchemaSearchService` / `SchemaReranker` | ✅ | 无关键词混合检索 |
| 多表查数 | `SqlQueryService` | ✅ | 无查询规划/澄清 |
| SQL 护栏 | `SqlValidator` / `MySqlQueryExecutor` | ✅ | 无审计落库 |
| 敏感脱敏 | `SensitiveColumnDetector` / `SensitiveMasker` | ✅ | 规则可配置性一般 |
| 数据源配置 | `AppProperties.SchemaDataSource` | 🟡 | 仅 env，无持久化/多源 |
| 状态 | `SchemaIndexService` 内存态 | 🟡 | 重启丢失，无 `sync_task` |
| 审计 | 应用日志 | 🟡 | 无表、无查询接口 |
| 评估 | 无 | ⬜ | 无黄金集、无门禁 |
| 权限 | 无（测试环境刻意跳过） | ⬜ | 生产前必补 |
| 可观测 | 日志 | ⬜ | 无 Trace/指标/成本 |
| 前端 | `SchemaPanel.vue` 找表/查数 | 🟡 | 无数据源管理、无历史、无分页 |

---

# 第一部分：一期补完（P1.5）

## 3. 数据源管理（最高优先）

### 3.1 目标

从“改 `.env` 重启”升级为“页面配置、测试连接、切换、多数据源”。

### 3.2 代码改动

| 层 | 新增/修改 | 说明 |
|---|---|---|
| domain | `DataSourceEntity` | id, name, type, url, username, encryptedPassword, databaseName, active, createdAt, updatedAt |
| repository | `DataSourceRepository` | `findByActiveTrue()`、按名称查询 |
| service | `DataSourceService` | 增删改查、`testConnection`、`activate`、`resolveActiveConfig` |
| service | `PasswordCipher` | AES-GCM；主密钥来自 `APP_SECRET_KEY`，不落库 |
| service | `SchemaDatasourceSupport` | 从静态工具改为 Spring Bean；优先读激活数据源，env 作为兜底 |
| dto | `DataSourceDto` / `DataSourceRequest` / `ConnectionTestDto` | 不返回密码 |
| controller | `DataSourceController` | `/api/datasources` CRUD、`/test`、`/activate` |
| Qdrant | `schema_kb` payload 增加 `dataSourceId` | 多源隔离，检索时按激活源过滤 |
| frontend | `DataSourcePanel.vue` / `api/datasources.ts` / `useDataSources.ts` | 卡片、表单、测试、激活 |

### 3.3 接口草案

| Method | Path | 说明 |
|---|---|---|
| `GET` | `/api/datasources` | 列表（不含密码） |
| `POST` | `/api/datasources` | 新建 |
| `PUT` | `/api/datasources/{id}` | 修改 |
| `DELETE` | `/api/datasources/{id}` | 删除（同时清理该源向量） |
| `POST` | `/api/datasources/{id}/test` | 测试连接 |
| `POST` | `/api/datasources/{id}/activate` | 设为当前数据源 |

### 3.4 验收标准

- [ ] 页面可新建 MySQL 数据源，密码加密存储，接口不回显。
- [ ] 可测试连接，失败信息可读且不含密码。
- [ ] 可切换数据源，找表/查数立即使用新源。
- [ ] 删除数据源会清理其 `schema_kb` 向量。
- [ ] 未配置数据源时，env 兜底仍可用（平滑迁移）。
- [ ] 单测覆盖：密码加解密、激活切换、配置解析。

**工作量：** 4~6 人日。

---

## 4. SQL 审计与查询历史

### 4.1 目标

每次找表/查数都能回答：**谁、何时、问了什么、执行了什么 SQL、返回多少行、耗时多少、是否成功**。

### 4.2 代码改动

| 层 | 新增/修改 | 说明 |
|---|---|---|
| domain | `QueryAuditEntity` | traceId, tenantId, userId, action, dataSourceId, question, sqlText, rowCount, costMs, success, errorCode, createdAt |
| repository | `QueryAuditRepository` | 分页查询、按时间范围 |
| service | `AuditService` | `record(...)`、分页查询、过期清理 |
| service | `SqlQueryService` | 成功/失败都记录；SQL 存审计表，不存结果集 |
| controller | `SchemaController` | `GET /api/schema/audit?page=&size=` |
| config | 定时清理任务 | 保留 180 天 |
| frontend | 查询历史表格 | 时间、问题、表、SQL、行数、耗时、状态 |

### 4.3 验收标准

- [ ] 每次 `/api/schema/query` 成功/失败都有审计记录。
- [ ] 审计表不保存结果集与敏感值。
- [ ] 可按时间/表/用户分页查询。
- [ ] 180 天前记录自动清理。
- [ ] 审计写入失败不影响主流程（记录错误日志）。

**工作量：** 2~3 人日。

---

## 5. 样例行 + 表关系提示

### 5.1 目标

提升 NL2SQL 准确率，减少“字段猜错、枚举值猜错、JOIN 键猜错”。

### 5.2 代码改动

| 组件 | 说明 |
|---|---|
| `SchemaDocument` | 新增 `sampleRows`、`relationships` |
| `MySqlSampleRowLoader` | 每表 3~5 行，主键倒序，排除 BLOB/TEXT，单值截断 200 字符 |
| `SchemaRelationshipDetector` | 纯函数：`xxx_id` + 注释“关联…” + 表名匹配，输出 `fromTable.fromColumn → toTable.toColumn` |
| `MySqlSchemaExtractor` | 可选加载样例与关系 |
| `SchemaIndexService` | 把样例/关系写入 Schema 文本与 payload |
| `SqlQueryService` | Prompt 增加“样例数据”“关系提示” |
| config | `app.schema.sample.enabled/rows/max-text-length`、`app.schema.relationship.enabled` |

### 5.3 验收标准

- [ ] 样例数据默认关闭，开启后只出现在生成上下文，不进入检索结果。
- [ ] 样例数据经过与查询结果相同的脱敏器。
- [ ] 关系提示能识别 `hospital_surgical_qc_anesthesia.record_id → hospital_surgical_qc_record.id`。
- [ ] 评估集 SQL 正确率相对基线提升 ≥ 5%。
- [ ] 单测覆盖：关系推断、样例截断、脱敏、开关关闭。

**工作量：** 3~4 人日。

---

## 6. 评估集与回归门禁

### 6.1 目标

让“改提示词/换模型/调检索”有量化依据，而不是靠感觉。

### 6.2 代码改动

| 组件 | 说明 |
|---|---|
| `eval/cases.yml` | 30 题起步，逐步扩到 100；分类：找表/查数/文档/安全 |
| `EvalRunner` | 独立 CLI（不走常规 `mvn test`，避免依赖真实服务） |
| `scripts/eval.sh` | 启动/连接测试环境，跑评估，输出 Markdown 报告 |
| 指标 | Top-1、Recall@5、SQL 执行正确率、结果正确率、拒答准确率 |
| 报告 | 按类别/难度汇总，保留历史趋势 |
| CI（可选） | 提示词/检索变更时触发，下降超阈值阻断 |

### 6.3 验收标准

- [ ] 一条命令跑完全部用例，输出通过率与失败明细。
- [ ] 失败用例包含：问题、期望表、实际表、生成 SQL、错误原因。
- [ ] 能对比两个版本的指标差异。
- [ ] 评估用例与代码一起版本化。
- [ ] 至少覆盖：单表、JOIN、聚合、时间条件、空结果、歧义、安全拒答。

**工作量：** 3~5 人日。

---

## 7. 结果分页、导出与状态修复

### 7.1 目标

从“固定 200 行截断”升级为可翻页、可导出、状态可追溯。

### 7.2 代码改动

| 组件 | 说明 |
|---|---|
| `SqlQueryRequest` | 增加 `page`、`pageSize`（上限 200） |
| `SqlQueryResponse` | 增加 `page`、`pageSize`、`hasMore` |
| `SqlValidator` | 支持外层 `LIMIT pageSize+1 OFFSET ...` |
| `MySqlQueryExecutor` | 透传分页参数 |
| 导出接口 | `GET /api/schema/query/export`，CSV，最多 1 万行，带审计 |
| `SchemaIndexService` | 同步状态持久化到 `sync_task` 表，重启不丢 |
| 前端 | 分页器、导出按钮、最后同步时间 |

### 7.3 验收标准

- [ ] 查数结果可翻页，`hasMore` 正确。
- [ ] 导出有行数上限与审计记录。
- [ ] 分页/导出仍强制敏感字段脱敏。
- [ ] 应用重启后 Schema 同步状态与表数量仍可见。
- [ ] 超过上限时给出明确提示，不静默截断。

**工作量：** 3~4 人日。

---

## 8. 体验与错误提示完善（低工作量高收益）

| 问题 | 改进 |
|---|---|
| 多表问题被拒 | 已支持；错误提示改为“未加载表 X，已尝试扩展” |
| 表歧义 | 返回 Top-3 并提示用户指定表 |
| 字段不存在 | 把数据库错误回喂模型重试一次 |
| 空结果 | 明确“没有匹配数据”，并提示可能原因 |
| 长查询 | 前端显示进度/耗时，支持取消（可选） |
| Schema 未同步 | 明确提示“请先同步表结构”并提供按钮 |

**工作量：** 2~3 人日。

---

# 第二部分：二期规划（P2）

## 9. 统一问答路由（文档 / 数据 / 混合）

### 9.1 目标

用户不需要先选“找表/查数/文档问答”，系统自动判断。

### 9.2 代码改动

| 组件 | 说明 |
|---|---|
| `QueryRouter` | 规则优先（关键词/表名命中），LLM 分类兜底 |
| `POST /api/ask` | 统一入口，返回 `route` 字段 |
| `RouteDecision` | `DOC / SQL / HYBRID / CHITCHAT` + 置信度 + 理由 |
| `ChatStreamService` | 文档链路复用 |
| `SqlQueryService` | 数据链路复用 |
| 前端 | 保留手动模式作为高级选项 |

### 9.3 验收标准

- [ ] “报销流程是什么” → DOC。
- [ ] “e__dzbl 今天的数据” → SQL。
- [ ] “文档里的指标和数据库里的实际值对比” → HYBRID。
- [ ] 路由决策可记录、可解释。
- [ ] 路由错误时可手动切换模式。

**工作量：** 5~7 人日。

---

## 10. 查询规划与多轮澄清

### 10.1 目标

处理复杂问题：多跳、对比、聚合、歧义。

### 10.2 代码改动

| 组件 | 说明 |
|---|---|
| `QueryPlan` | 过滤条件、聚合维度、排序、涉及表、JOIN 键 |
| `QueryPlanner` | 规则 + LLM 生成计划 |
| `ClarificationService` | 表/字段歧义时反问 |
| `SqlQueryService` | 按计划生成，而不是一步到位 |
| 会话状态 | 记录待澄清问题与用户回答 |

### 10.3 验收标准

- [ ] “对比今年和去年销售额”能拆成两个时间范围再合并。
- [ ] 多张表分数接近时反问用户。
- [ ] 澄清后能继续原问题，不丢失上下文。
- [ ] 计划可展示、可审计。

**工作量：** 6~8 人日。

---

## 11. 可观测性与成本

### 11.1 代码改动

| 组件 | 说明 |
|---|---|
| `TraceFilter` | 生成 traceId，写入 MDC |
| Micrometer | 延迟、错误率、SQL 拒绝率、扩展率、token |
| 成本模型 | token × 单价 + embedding + 存储 |
| 结构化日志 | JSON，禁止敏感值 |
| Actuator | `/actuator/metrics`、`/health` |
| 前端/看板 | 质量、性能、成本、运营 |

### 11.2 验收标准

- [ ] 每次请求有 traceId，可串起检索/生成/校验/执行。
- [ ] 能在指标里看到 P95、错误率、SQL 拒绝率、单查询成本。
- [ ] 日志中无 API Key、无完整结果集。
- [ ] 关键指标有告警阈值。

**工作量：** 4~6 人日。

---

## 12. 权限与多租户

### 12.1 代码改动

| 组件 | 说明 |
|---|---|
| `tenantId` | 实体、Qdrant payload、会话、审计 |
| `AclTag` | 文档/数据源标签 |
| Qdrant filter | 检索强制 `tenantId + aclTags` |
| SQL 策略 | 行级 `WHERE` 注入、列级屏蔽 |
| 审计 | 租户/用户维度 |
| 迁移 | 历史数据回填默认租户 |

### 12.2 验收标准

- [ ] 租户 A 检索不到租户 B 的向量。
- [ ] 越权测试用例全部通过。
- [ ] SQL 行级权限生效。
- [ ] 审计可按租户/用户查询。
- [ ] 缓存键包含租户与权限标签。

**工作量：** 8~12 人日（生产前必做）。

---

## 13. 多数据源与方言抽象

### 13.1 目标

从“MySQL only”扩展到 PostgreSQL / HTTP API / SaaS。

### 13.2 代码改动

| 组件 | 说明 |
|---|---|
| `SqlDialect` | 分页、时间函数、标识符引用、LIMIT 语法 |
| `PostgresKnowledgeConnector` | 连接器 + Schema 提取器 |
| `HttpKnowledgeConnector` | 拉取 JSON → RawDocument |
| Prompt | 按方言注入规则 |
| `SqlValidator` | 按方言校验 |
| 测试 | Testcontainers 起 PostgreSQL |

### 13.3 验收标准

- [ ] 新增 PostgreSQL 数据源无需改核心服务。
- [ ] 方言差异由 `SqlDialect` 封装，不散落 if/switch。
- [ ] 两种数据源可同时存在并切换。

**工作量：** 6~8 人日。

---

## 14. 语义缓存

### 14.1 代码改动

| 组件 | 说明 |
|---|---|
| `SemanticCache` | 问题 embedding + 租户 + 权限 + 模型版本 |
| 存储 | Redis（docker-compose 增加）或 Qdrant 独立集合 |
| 失效 | 数据源同步、Schema 变更、权限变更 |
| 指标 | 命中率、节省成本 |

### 14.2 验收标准

- [ ] 高频重复问题命中缓存，延迟显著下降。
- [ ] 权限/租户不同不会串缓存。
- [ ] 源数据更新后缓存失效。

**工作量：** 3~5 人日。

---

## 15. Agent 化（工具调用）

### 15.1 目标

从“固定流程”到“模型自主选择工具”，但**先单 Agent，后多 Agent**。

### 15.2 代码改动

| 组件 | 说明 |
|---|---|
| `@Tool` 方法 | `searchSchema`、`runReadOnlySql`、`syncSchema`、`listTables` |
| `DataAgent` | 单 Agent + 工具调用 |
| 工具白名单 | 只读、参数校验、超时 |
| 轨迹 | 每步工具、输入输出、耗时 |
| Critic（后置） | 校验答案/引用 |

### 15.3 验收标准

- [ ] 单 Agent 能根据问题自主选择“找表/查数”。
- [ ] 工具调用有审计与超时。
- [ ] 轨迹可回放。
- [ ] 不引入写操作工具。

**工作量：** 8~10 人日。

---

## 16. 优先级（RICE）

> RICE = (Reach × Impact × Confidence) / Effort，分值 1~10 自评，仅用于排序。

| 举措 | Reach | Impact | Confidence | Effort | RICE | 结论 |
|---|---|---|---|---|---|---|
| 数据源管理 | 9 | 9 | 9 | 6 | 121 | **S1** |
| SQL 审计 | 8 | 8 | 10 | 3 | 213 | **S1** |
| 样例 + 关系 | 8 | 8 | 7 | 5 | 90 | **S2** |
| 评估集 | 10 | 9 | 9 | 5 | 162 | **S2** |
| 分页/导出/状态 | 7 | 6 | 9 | 4 | 95 | **S3** |
| 统一路由 | 9 | 8 | 7 | 6 | 84 | S4 |
| 可观测 | 9 | 8 | 8 | 5 | 115 | S4 |
| 权限/多租户 | 5 | 10 | 7 | 10 | 35 | S5（生产前） |
| 多数据源 | 6 | 7 | 8 | 7 | 48 | S5 |
| 查询规划 | 6 | 7 | 6 | 7 | 36 | S6 |
| 语义缓存 | 5 | 6 | 6 | 4 | 45 | S6 |
| Agent 化 | 4 | 8 | 4 | 9 | 14 | 暂缓 |

---

## 17. Sprint 排期（建议）

| Sprint | 周期 | 交付 | 验收 |
|---|---|---|---|
| **S1** | 2 周 | 数据源管理 + SQL 审计 | 页面可配置/切换数据源；查数可追溯 |
| **S2** | 2 周 | 样例行 + 关系提示 + 评估基线 | SQL 正确率提升；能一键跑评估 |
| **S3** | 2 周 | 分页/导出 + 状态持久化 + 体验优化 | 可翻页/导出；重启状态不丢 |
| **S4** | 2 周 | 统一路由 + 可观测性 | 自动判型；指标/成本可见 |
| **S5** | 2 周 | 权限/多租户 + 多数据源 | 租户隔离；PostgreSQL 可接 |
| **S6** | 2 周 | 查询规划 + 缓存/Agent 试点 | 复杂问题可拆解；缓存命中 |

> 每个 Sprint 结束必须满足 `CLAUDE.md` 第 10 节的文档同步清单与 DoD。

---

## 18. Sprint 1 代码级任务拆解

### 18.1 数据源管理（7 天）

| Day | 任务 | 产出 |
|---|---|---|
| 1 | 设计表结构与 DTO | `DataSourceEntity`、`DataSourceDto` |
| 2 | 密码加密 | `PasswordCipher` + 单测 |
| 3 | Repository + Service | CRUD、`testConnection`、`activate` |
| 4 | Controller + 错误码 | `/api/datasources` |
| 5 | 重构 `SchemaDatasourceSupport` | 从 DB 解析激活源，env 兜底 |
| 6 | 前端数据源页 | `DataSourcePanel.vue` |
| 7 | 联调 + 文档 | README/错误码/配置同步 |

### 18.2 SQL 审计（3 天）

| Day | 任务 | 产出 |
|---|---|---|
| 8 | 实体与 Repository | `QueryAuditEntity` |
| 9 | `AuditService` + 接入 `SqlQueryService` | 成功/失败都记录 |
| 10 | 查询接口 + 前端历史 + 清理任务 | `/api/schema/audit` |

### 18.3 Sprint 1 验收清单

- [ ] 新建/测试/激活/删除数据源
- [ ] 密码加密且不回显
- [ ] 切换后找表/查数使用新源
- [ ] 每次查数有审计记录
- [ ] 审计可按时间/表分页
- [ ] 单测 + 前端 build 通过

---

## 19. Definition of Done（每个功能）

- [ ] 代码实现 + 单元测试
- [ ] 前端类型同步 + `npm run build`
- [ ] `./mvnw -q test` 全绿
- [ ] 新增配置三处同步：`application.yml` / `AppProperties` / `.env.example`
- [ ] 新增错误码写入 `CLAUDE.md` 4.3
- [ ] README 的 API/功能/限制同步
- [ ] 安全用例：越权、注入、敏感字段
- [ ] 审计与日志就位
- [ ] 回滚方案明确

---

## 20. 风险与依赖

| 风险 | 影响 | 应对 |
|---|---|---|
| 密码加密主密钥丢失 | 数据源不可用 | 密钥托管 + 轮换方案 |
| 数据源切换导致向量混用 | 检索错表 | payload 加 `dataSourceId`，检索强过滤 |
| 样例数据泄露 PII | 合规风险 | 复用脱敏器 + 表级开关 |
| 评估集过拟合 | 指标虚高 | 定期补题 + 人工抽检 |
| 权限改造范围大 | 延期 | 先“只记录不拦截”灰度 |
| 多数据源方言差异 | SQL 错 | 方言抽象 + Testcontainers |

**依赖：** 只读从库、Redis（缓存）、PostgreSQL（多源）、Testcontainers（集成测试）。

---

## 21. 需要你拍板

1. **数据源管理**：密码加密主密钥用 `APP_SECRET_KEY`，是否可以？
2. **多数据源**：一期是否需要同时支持 PostgreSQL，还是先 MySQL？
3. **样例行**：医疗敏感表是否允许读取 3~5 行脱敏样例？
4. **权限**：生产环境是租户隔离，还是文档级 + 行级？
5. **评估**：谁来维护 100 题 ground truth（业务专家投入）？
6. **排期**：按上面 6 个 Sprint 推进，还是先做 S1/S2 看效果？
