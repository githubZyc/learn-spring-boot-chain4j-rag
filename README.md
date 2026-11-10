# learn-spring-boot-chain4j-rag

基于 **Spring Boot 3.5 + langchain4j 1.23 + Qdrant + Vue 3** 的知识库（RAG）实战项目。

上传 `.md` / `.txt` 文档 → 切分 → 向量化写入 Qdrant → 基于知识库进行 **SSE 流式问答**，并展示引用来源。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21、Spring Boot 3.5.16、Spring Data JPA、H2 |
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

- **文档管理**：拖拽上传 `.md` / `.txt`（≤10MB）。上传接口立即返回 `PENDING`，解析、切分、向量化在后台线程执行，前端轮询列表状态；支持删除与重建索引。
- **知识问答**：基于 Qdrant 检索 top-5 片段，SSE 逐 token 输出；回答附带引用来源（文件名、chunk、相似度、片段预览）。
- **会话记忆**：按 `conversationId` 隔离，`MessageWindowChatMemory` 保留最近 20 条；“新会话”会清理后端记忆。
- **失败可追踪**：入库失败的文档仍会保留记录，并在列表中展示失败原因，可重新索引；应用重启时残留的 `PENDING`/`PROCESSING` 会自动标记为 `FAILED`，避免卡死。
- **并发安全**：删除会先取消并等待该文档的后台任务结束，再清理向量和记录；重建索引时会拒绝重复提交。

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
```

## 关键设计

- **元数据与向量分离**：文档管理信息存 H2；chunk 向量与 `docId` / `fileName` / `index` 存入 Qdrant payload，删除时按 `docId` keyword 精确过滤。
- **异步入库**：上传只做校验 + 落盘 + 建记录，后台虚拟线程执行 embedding；前端每 2 秒轮询 `GET /api/documents` 更新状态。
- **重复入库防护**：每次 ingest 前先按 `docId` 删除旧向量，避免重建索引产生重复 chunk。
- **检索器**：由 `langchain4j-spring-boot-starter` 的 `RagAutoConfig` 基于 Qdrant `EmbeddingStore` 自动创建，检索参数在 `langchain4j.rag.retrieval.*` 配置。
- **SSE 安全**：`SseEmitter` 发送统一加锁、完成标记保证只结束一次，客户端断开时停止写入。
- **前端流式**：`EventSource` 不支持 POST，使用 `fetch` + `ReadableStream` 手动解析 SSE。

## 已知限制

- 仅支持 UTF-8 编码的 `.md` / `.txt`；PDF/Word 可通过引入 `langchain4j-document-parser-*` 扩展。
- 单知识库（单 Qdrant collection）、无鉴权、会话记忆仅进程内保存。
