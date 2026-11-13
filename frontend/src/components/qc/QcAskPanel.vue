<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { SqlQueryResult } from '../../types'

const props = defineProps<{
  asking: boolean
  result: SqlQueryResult | null
}>()

const emit = defineEmits<{ ask: [question: string] }>()

const question = ref('')
const samples = [
  '昨天有哪些手术缺麻醉数据？',
  '本月异常终止的手术有哪些？',
  '哪个分院归档最慢？',
  '麻醉时长超过 2 小时的手术？',
  '状态还是进行中但已经手术结束的病例？',
  '取发数量和种植数量差异超过 10% 的病例？'
]

const rowObjects = computed(() => {
  const result = props.result
  if (!result) {
    return []
  }
  return result.rows.map((row) =>
    Object.fromEntries(result.columns.map((column, index) => [column, row[index] ?? '']))
  )
})

function submit() {
  const value = question.value.trim()
  if (!value) {
    ElMessage.warning('请输入问题')
    return
  }
  emit('ask', value)
}

function askSample(sample: string) {
  question.value = sample
  emit('ask', sample)
}
</script>

<template>
  <div class="ask-panel">
    <div class="ask-title">
      <h3>质控问答</h3>
      <span>只查询手术质控相关表</span>
    </div>

    <div class="ask-row">
      <el-input
        v-model="question"
        placeholder="例如：昨天有哪些手术缺麻醉数据？"
        clearable
        @keyup.enter="submit"
      />
      <el-button type="primary" :loading="asking" @click="submit">提问</el-button>
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

    <div v-if="result" class="result">
      <div class="result-head">
        <div class="tables">
          <el-tag v-for="table in result.tables" :key="table.tableName" size="small" type="success">
            {{ table.tableName }}
          </el-tag>
        </div>
        <span class="meta">{{ result.rowCount }} 行 · {{ result.costMs }}ms</span>
      </div>
      <div v-if="result.maskedColumns.length" class="masked">
        <el-tag type="warning" size="small">
          已脱敏：{{ result.maskedColumns.join('、') }}
        </el-tag>
      </div>
      <pre class="sql">{{ result.sql }}</pre>
      <el-table :data="rowObjects" border size="small" max-height="320">
        <el-table-column
          v-for="column in result.columns"
          :key="column"
          :prop="column"
          :label="column"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>
    </div>

    <el-empty v-else-if="!asking" description="用自然语言问质控数据，回答会附带 SQL" :image-size="72" />
  </div>
</template>

<style scoped>
.ask-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.ask-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 12px;
}

.ask-title h3 {
  margin: 0;
  font-size: 15px;
}

.ask-title span {
  color: #909399;
  font-size: 12px;
}

.ask-row {
  display: flex;
  gap: 8px;
}

.samples {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.sample {
  cursor: pointer;
}

.result {
  margin-top: 14px;
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tables {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.meta {
  color: #909399;
  font-size: 12px;
}

.masked {
  margin-top: 8px;
}

.sql {
  margin: 10px 0;
  padding: 10px;
  max-height: 160px;
  overflow: auto;
  background: #f5f7fa;
  border-radius: 6px;
  color: #303133;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
