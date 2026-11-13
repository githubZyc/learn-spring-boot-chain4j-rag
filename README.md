# learn-spring-boot-chain4j-rag

基于 **Spring Boot 3.5 + langchain4j 1.23 + Qdrant + Vue 3** 的知识库（RAG）实战项目。

上传 `.md` / `.txt` / `.pdf` 文档 → 切分 → 向量化写入 Qdrant → 基于知识库进行 **SSE 流式问答**，并展示引用来源。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21、Spring Boot 3.5.16、Spring Data JPA、H2、PDFBox（PDF 解析） |
| LLM / RAG | langchain4j 1.23.0、`@AiService`、TokenStream、Qwen(DashScope) |
| 向量库 | Qdrant `v1.17.1`（gRPC 6334） |
| 前端 | Vue 3.5、Vite 5、TypeScript、Element Plus |

langchain4j 版本与 `learn-chain4j` 项目保持一致：核心 `1.23.0`，beta 模块 `1.23.0-beta33`。

## 项目结构

```text
learn-spring-boot-chain4j-rag/
├── pom.xml
├── docker-compose.yml              # 可选：本地 Qdrant
├── .env.example                    # 复制为 .env 填写 API Key
├── src/main/java/org/yc/learn/rag/
│   ├── ai/RagAssistant.java        # @AiService 流式助手
│   ├── config/                     # Qdrant、ChatMemoryProvider、执行器
│   ├── controller/                 # 文档管理 + 流式问答
│   ├── service/                    # 入库、向量删除、SSE
│   ├── extractor/                  # 文件类型 → 文本的策略实现（TXT/MD/PDF）
│   ├── domain/ repository/ dto/    # H2 文档元数据
│   └── util/TextNormalizer.java
├── src/main/resources/application.yml
├── data/                           # 运行时生成：H2 数据库 + 上传文件（gitignore）
└── frontend/                       # Vue 3 + Vite + TS
```

## 前置条件

- JDK 21
- Node.js 18+（本项目锁定 Vite 5，兼容 Node 18）
- 可访问 DashScope 的 API Key
- Qdrant 已启动（REST 6333、gRPC 6334）

## 快速开始

### 1. 启动 Qdrant

本机已有容器时直接复用：

```bash
docker ps | grep qdrant
curl -s http://localhost:6333/collections
```

没有容器时：

```bash
docker compose up -d
```

### 2. 配置 API Key

```bash
cp .env.example .env
# 编辑 .env，填入 DASHSCOPE_API_KEY
```

`application.yml` 通过 `spring.config.import` 读取 `.env`，也支持直接设置同名环境变量。

默认沿用 `learn-chain4j` 的模型配置：

```properties
DASHSCOPE_CHAT_MODEL=deepseek-v4.1-flash-1017
DASHSCOPE_CHAT_MULTIMODAL=true
DASHSCOPE_EMBEDDING_MODEL=text-embedding-v3
DASHSCOPE_EMBEDDING_DIMENSION=1024
```

> 向量维度必须与 Qdrant 集合一致（默认 1024 / Cosine）。集合会在启动时自动创建；如果已有集合维度不同，应用会启动失败并提示删除集合。

### 数据库知识库配置（可选）

在 `.env` 中配置测试库连接，重启后会创建独立集合 `schema_kb`：

```properties
SCHEMA_DB_URL=jdbc:mysql://127.0.0.1:3306/your_database?useSSL=false&serverTimezone=Asia/Shanghai
SCHEMA_DB_USERNAME=readonly_user
SCHEMA_DB_PASSWORD=change_me
SCHEMA_DB_DATABASE=your_database
```

中文表别名放在 UTF-8 文件 `data/schema-aliases.properties`（每行 `表名=别名`），不要直接写进 `.env`，否则会按 Latin-1 解码成乱码。

### 3. 启动后端

```bash
./mvnw spring-boot:run
# 或
./mvnw -DskipTests package && java -jar target/learn-spring-boot-chain4j-rag-0.0.1-SNAPSHOT.jar
```

- 服务地址：<http://localhost:8080>
- H2 控制台：<http://localhost:8080/h2-console>（JDBC URL：`jdbc:h2:file:./data/ragdb`）

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 <http://localhost:5173>。Vite 已把 `/api` 代理到 `localhost:8080`，无需配置 CORS。

生产构建：

```bash
npm run build
```

## 功能说明

- **文档管理**：拖拽上传 `.md` / `.txt` / `.pdf`（≤10MB）。上传接口立即返回 `PENDING`，解析、切分、向量化在后台线程执行，前端轮询列表状态；支持删除与重建索引。
- **知识问答**：基于 Qdrant 检索 top-5 片段，SSE 逐 token 输出；回答附带引用来源（文件名、chunk、相似度、片段预览）。
- **会话记忆**：按 `conversationId` 隔离，`MessageWindowChatMemory` 保留最近 20 条；“新会话”会清理后端记忆。
- **失败可追踪**：入库失败的文档仍会保留记录，并在列表中展示失败原因，可重新索引；应用重启时残留的 `PENDING`/`PROCESSING` 会自动标记为 `FAILED`，避免卡死。
- **并发安全**：删除会先取消并等待该文档的后台任务结束，再清理向量和记录；重建索引时会拒绝重复提交。
- **数据库知识库（找表）**：配置 MySQL 数据源后，同步 `information_schema` 表结构到独立集合 `schema_kb`，用自然语言查找表名。一表一文档，支持表别名、默认时间字段与敏感字段标注；结果会做「主表优先」重排，明细 / 共享 / 备份 / 日志类表自动降权。
- **数据库知识库（查数）**：自然语言 → 只读 SQL → 执行 → 返回表格，支持多表 JOIN。SQL 必须通过只读、禁写、禁注释、禁逗号连表、强制 200 行与超时校验；敏感字段按列名自动脱敏。
- **手术质控助手**：基于 `hospital_surgical_qc_*` 表做规则扫描、异常清单、病例 360、自然语言问答与闭环处理。内置 8 条规则：归档缺麻醉、应收阶段缺项、状态阶段不一致、异常终止、麻醉时长、取发/种植数量、归档超时、收费单/病历缺失。

## REST API

| Method | Path | 说明 |
|---|---|---|
| `POST` | `/api/documents` | 上传文档（multipart，字段 `file`），返回 `202` + `PENDING` 记录 |
| `GET` | `/api/documents` | 文档列表 |
| `GET` | `/api/documents/{id}` | 文档详情 |
| `DELETE` | `/api/documents/{id}` | 删除文档及其向量 |
| `POST` | `/api/documents/{id}/reindex` | 重建索引 |
| `POST` | `/api/chat/stream` | SSE 流式问答 `{conversationId, message}` |
| `DELETE` | `/api/chat/{conversationId}` | 清理会话记忆 |
| `POST` | `/api/schema/sync` | 触发后台同步数据库表结构（202） |
| `GET` | `/api/schema/status` | 数据库知识库同步状态 |
| `POST` | `/api/schema/ask` | 自然语言找表 `{question, maxResults?}` |
| `POST` | `/api/schema/query` | 自然语言查数（NL2SQL，支持多表 JOIN）`{question}`，返回 SQL 与结果表格 |
| `GET` | `/api/qc/overview` | 质控看板 `?from=&to=&departId=` |
| `GET` | `/api/qc/findings` | 质控异常分页 `?status=&severity=&ruleCode=&departId=&page=&size=` |
| `GET` | `/api/qc/cases/{recordId}` | 病例 360（主记录 + 四阶段 + 异常） |
| `POST` | `/api/qc/ask` | 质控范围自然语言问答 `{question}` |
| `POST` | `/api/qc/scan` | 触发质控扫描 `?days=30` |
| `POST` | `/api/qc/findings/{id}/ack` | 认领异常 `?operator=` |
| `POST` | `/api/qc/findings/{id}/resolve` | 完成异常 `?operator=&note=` |
| `POST` | `/api/qc/findings/{id}/ignore` | 标记非问题 `?operator=&note=` |
| `GET` | `/api/qc/rules` | 质控规则列表 |

SSE 事件：

```text
event: meta      data: {"conversationId":"c-..."}
event: sources   data: [{...}]
event: token     data: {"text":"..."}
event: done      data: {"conversationId":"c-..."}
event: error     data: {"message":"..."}
```

## 验证命令

```bash
# 上传
curl -s -F "file=@docs/demo.md" http://localhost:8080/api/documents

# 流式问答
curl -sN -X POST http://localhost:8080/api/chat/stream \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"conversationId":"c-test-1","message":"文档里说了什么？"}'

# 查看 Qdrant payload
curl -s -X POST http://localhost:6333/collections/knowledge_base/points/scroll \
  -H 'Content-Type: application/json' \
  -d '{"limit":1,"with_payload":true,"with_vector":false}'

# 手术质控：扫描 + 看板 + 病例 + 问答
curl -s -X POST "http://localhost:8080/api/qc/scan?days=120"
curl -s "http://localhost:8080/api/qc/overview?from=2026-08-01&to=2026-11-12"
curl -s "http://localhost:8080/api/qc/cases/1017"
curl -s -X POST http://localhost:8080/api/qc/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"状态还是进行中但已经手术结束的病例有哪些？"}'
```

## 关键设计

- **元数据与向量分离**：文档管理信息存 H2；chunk 向量与 `docId` / `fileName` / `index` 存入 Qdrant payload，删除时按 `docId` keyword 精确过滤。
- **异步入库**：上传只做校验 + 落盘 + 建记录，后台虚拟线程执行 embedding；前端每 2 秒轮询 `GET /api/documents` 更新状态。
- **重复入库防护**：每次 ingest 前先按 `docId` 删除旧向量，避免重建索引产生重复 chunk。
- **检索器**：由 `langchain4j-spring-boot-starter` 的 `RagAutoConfig` 基于 Qdrant `EmbeddingStore` 自动创建，检索参数在 `langchain4j.rag.retrieval.*` 配置。
- **SSE 安全**：`SseEmitter` 发送统一加锁、完成标记保证只结束一次，客户端断开时停止写入。
- **前端流式**：`EventSource` 不支持 POST，使用 `fetch` + `ReadableStream` 手动解析 SSE。
- **质控规则引擎**：`QcRule` + `QcRuleRegistry`，规则是纯判断；外部质控表只读，异常与闭环状态存应用库 H2；扫描按 `(ruleCode, recordId)` 幂等，人工忽略的异常不会被重新打开。
- **质控问答隔离**：复用 Schema RAG + NL2SQL，但表范围固定为 `hospital_surgical_qc_*`，不会问到客户/营销等无关数据。

## 已知限制

- 文本文件需为 UTF-8 编码；PDF 仅支持带文字层的版本，扫描件需要 OCR 扩展。
- 新增文件类型只需实现 `DocumentTextExtractor`，无需改动入库流程（见 `CLAUDE.md` 第 5.1 节）。
- 单文档知识库、无鉴权、会话记忆仅进程内保存。
- 数据库知识库的查数支持多表 JOIN：首轮把前 8 张候选表送入模型，模型引用其他表时会按需加载并重试（最多 2 次）；测试环境不做表级权限，只校验表在库中存在；结果最多 200 行。
- 多表查询的表数量由 `SCHEMA_QUERY_MAX_TABLES` 与 `SCHEMA_QUERY_MAX_EXPANSIONS` 调整。
- 测试库为 MySQL 5.7，SQL 生成提示词已避免 CTE / 窗口函数；换成 MySQL 8 后可放宽。
- 手术质控助手当前 8 条规则、只读；测试环境不做角色权限，规则阈值在 `app.qc.*` 配置，页面级规则配置尚未实现。
- 质控问答只允许 `hospital_surgical_qc_*` 表；跨模块的复杂问题会被拒绝，需要时再扩展表白名单。
- 质控看板与手动扫描默认按最近 120 天统计（测试数据集中在 8~11 月）；定时扫描仍使用 `app.qc.scan-days`（默认 30 天）。需要调整前端默认值时修改 `frontend/src/composables/useQc.ts` 的 `rangeDays`。
