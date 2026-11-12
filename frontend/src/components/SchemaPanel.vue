<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { SchemaStatus, SchemaTableMatch, SqlQueryResult } from '../types'

const props = defineProps<{
  status: SchemaStatus | null
  matches: SchemaTableMatch[]
  queryResult: SqlQueryResult | null
  asking: boolean
  querying: boolean
}>()

const emit = defineEmits<{
  sync: []
  ask: [question: string]
  query: [question: string]
}>()

const mode = ref<'ask' | 'query'>('ask')
const question = ref('')

const askSamples = ['电子病历数据存在哪个表？', '客户信息存在哪个表？', '订单数据存在哪个表？']
const querySamples = ['e__dzbl 里今天生成的数据有哪些？', '电子病历最近 7 天有多少条？']
const samples = computed(() => (mode.value === 'ask' ? askSamples : querySamples))

const tagType = computed<'success' | 'warning' | 'danger' | 'info'>(() => {
  switch (props.status?.status) {
    case 'READY':
      return 'success'
    case 'RUNNING':
      return 'warning'
    case 'FAILED':
      return 'danger'
    default:
      return 'info'
  }
})

const rowObjects = computed(() => {
  const result = props.queryResult
  if (!result) {
    return []
  }
  return result.rows.map((row) =>
    Object.fromEntries(result.columns.map((column, index) => [column, row[index] ?? '']))
  )
})

function handleSubmit() {
  const value = question.value.trim()
  if (!value) {
    ElMessage.warning('请输入问题')
    return
  }
  if (mode.value === 'ask') {
    emit('ask', value)
  } else {
    emit('query', value)
  }
}

function askSample(sample: string) {
  question.value = sample
  if (mode.value === 'ask') {
    emit('ask', sample)
  } else {
    emit('query', sample)
  }
}
</script>

<template>
  <div class="schema-panel">
    <div class="pane-card">
      <div class="schema-head">
        <div>
          <h2>数据库知识库</h2>
          <p class="hint">先同步 MySQL 表结构，再「找表」或「查数」</p>
        </div>
        <el-button
          type="primary"
          :loading="status?.status === 'RUNNING'"
          :disabled="status !== null && !status.configured"
          @click="emit('sync')"
        >
          同步表结构
        </el-button>
      </div>

      <el-alert
        v-if="status && !status.configured"
        type="warning"
        :closable="false"
        title="未配置数据库连接"
        description="请在后端 .env 中设置 SCHEMA_DB_URL / SCHEMA_DB_USERNAME / SCHEMA_DB_PASSWORD 后重启服务。"
      />
      <div v-else-if="status" class="meta">
        <el-tag :type="tagType" size="small">{{ status.status }}</el-tag>
        <span>库：{{ status.database || '-' }}</span>
        <span>集合：{{ status.collection }}</span>
        <span v-if="status.tableCount > 0">已同步 {{ status.tableCount }} 张表</span>
        <span v-if="status.error" class="error">{{ status.error }}</span>
      </div>
    </div>

    <div class="pane-card grow">
      <div class="mode-row">
        <el-radio-group v-model="mode" size="small">
          <el-radio-button value="ask">找表</el-radio-button>
          <el-radio-button value="query">查数</el-radio-button>
        </el-radio-group>
        <span class="hint">
          {{ mode === 'ask' ? '自然语言定位表名与字段' : '生成只读 SQL 并返回数据' }}
        </span>
      </div>

      <div class="ask-row">
        <el-input
          v-model="question"
          :placeholder="
            mode === 'ask'
              ? '例如：电子病历数据存在哪个表？'
              : '例如：e__dzbl 里今天生成的数据有哪些？'
          "
          clearable
          @keyup.enter="handleSubmit"
        />
        <el-button type="primary" :loading="asking || querying" @click="handleSubmit">
          {{ mode === 'ask' ? '查找' : '查询' }}
        </el-button>
      </div>

      <div class="samples">
        <el-tag
          v-for="sample in samples"
          :key="sample"
          class="sample"
          effect="plain"
          @click="askSample(sample)"
        >
          {{ sample }}
        </el-tag>
      </div>

      <div v-if="mode === 'ask' && matches.length" class="matches">
        <el-card v-for="match in matches" :key="match.tableName" shadow="never" class="match">
          <div class="match-head">
            <div>
              <strong>{{ match.tableName }}</strong>
              <span v-if="match.tableComment" class="comment">{{ match.tableComment }}</span>
              <el-tag v-if="match.rankReason" size="small" type="info" class="reason">
                {{ match.rankReason }}
              </el-tag>
            </div>
            <el-tag size="small" type="success">{{ (match.score * 100).toFixed(1) }}%</el-tag>
          </div>
          <div v-if="match.defaultTimeColumn || match.sensitiveColumns.length" class="match-meta">
            <span v-if="match.defaultTimeColumn">默认时间字段：{{ match.defaultTimeColumn }}</span>
            <span v-if="match.sensitiveColumns.length">
              敏感字段：{{ match.sensitiveColumns.join('、') }}
            </span>
          </div>
          <pre class="snippet">{{ match.snippet }}</pre>
        </el-card>
      </div>

      <div v-else-if="mode === 'query' && queryResult" class="query-result">
        <div class="query-head">
          <div class="query-tables">
            <el-tag
              v-for="table in queryResult.tables"
              :key="table.tableName"
              size="small"
              type="success"
            >
              {{ table.tableName }}
              <span v-if="table.tableComment"> · {{ table.tableComment }}</span>
            </el-tag>
          </div>
          <span class="query-meta">
            返回 {{ queryResult.rowCount }} 行 · {{ queryResult.costMs }}ms
          </span>
        </div>
        <div v-if="queryResult.maskedColumns.length" class="masked">
          <el-tag type="warning" size="small">
            已脱敏：{{ queryResult.maskedColumns.join('、') }}
          </el-tag>
        </div>
        <pre class="sql">{{ queryResult.sql }}</pre>
        <el-table :data="rowObjects" border size="small" max-height="420">
          <el-table-column
            v-for="column in queryResult.columns"
            :key="column"
            :prop="column"
            :label="column"
            min-width="120"
            show-overflow-tooltip
          />
        </el-table>
      </div>

      <el-empty
        v-else-if="!asking && !querying"
        :description="mode === 'ask' ? '输入问题，看看哪张表最匹配' : '输入问题，生成 SQL 并查询数据'"
      />
    </div>
  </div>
</template>

<style scoped>
.schema-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pane-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  padding: 16px;
}

.pane-card.grow {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.schema-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.schema-head h2 {
  margin: 0;
  font-size: 15px;
}

.hint {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  color: #606266;
  font-size: 13px;
}

.error {
  color: #f56c6c;
}

.mode-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.ask-row {
  display: flex;
  gap: 8px;
}

.samples {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0 4px;
}

.sample {
  cursor: pointer;
}

.matches {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}

.match-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.comment {
  margin-left: 8px;
  color: #606266;
}

.reason {
  margin-left: 8px;
}

.match-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}

.snippet {
  margin: 8px 0 0;
  padding: 10px;
  max-height: 180px;
  overflow: auto;
  background: #f5f7fa;
  border-radius: 6px;
  color: #606266;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.query-result {
  margin-top: 12px;
}

.query-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.query-tables {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.query-meta {
  color: #909399;
  font-size: 12px;
}

.masked {
  margin-top: 8px;
}

.sql {
  margin: 10px 0;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 6px;
  color: #303133;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
