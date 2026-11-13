<script setup lang="ts">
import type {
  QcCaseDetail,
  QcFinding,
  QcFindingFilters,
  QcOverview,
  QcRule,
  QcScanResult,
  SqlQueryResult
} from '../../types'
import QcKpiCards from './QcKpiCards.vue'
import QcFindingTable from './QcFindingTable.vue'
import QcAskPanel from './QcAskPanel.vue'
import QcCaseDrawer from './QcCaseDrawer.vue'

defineProps<{
  overview: QcOverview | null
  findings: QcFinding[]
  total: number
  rules: QcRule[]
  filters: QcFindingFilters
  loading: boolean
  scanning: boolean
  asking: boolean
  askResult: SqlQueryResult | null
  caseDetail: QcCaseDetail | null
  caseLoading: boolean
  scanResult: QcScanResult | null
  rangeDays: number
}>()

const emit = defineEmits<{
  refresh: []
  scan: []
  'filter-change': [Partial<QcFindingFilters>]
  'page-change': [number]
  'view-case': [number]
  ack: [number]
  resolve: [number, string]
  ignore: [number, string]
  ask: [string]
  'close-case': []
  'range-change': [number]
}>()
</script>

<template>
  <div class="qc-panel">
    <div class="pane-card">
      <div class="qc-head">
        <div>
          <h2>手术质控助手</h2>
          <p class="hint">先看异常清单，再看分院趋势；规则负责发现，AI 负责解释。</p>
        </div>
        <div class="head-actions">
          <span v-if="scanResult" class="scan-info">
            扫描 {{ scanResult.totalCases }} 例 · 命中 {{ scanResult.totalFindings }} 条 ·
            {{ scanResult.costMs }}ms
          </span>
          <el-button type="primary" :loading="scanning" @click="emit('scan')">
            扫描最近 {{ rangeDays }} 天
          </el-button>
        </div>
      </div>
      <QcKpiCards :overview="overview" />
    </div>

    <div class="qc-main">
      <div class="pane-card qc-list">
        <div class="pane-title">
          <h2>异常清单</h2>
          <span class="hint">支持按状态、级别、规则、分院筛选</span>
        </div>
        <QcFindingTable
          :findings="findings"
          :total="total"
          :loading="loading"
          :rules="rules"
          :filters="filters"
          @filter-change="emit('filter-change', $event)"
          @page-change="emit('page-change', $event)"
          @refresh="emit('refresh')"
          @view="emit('view-case', $event)"
          @ack="emit('ack', $event)"
          @resolve="(id: number, note: string) => emit('resolve', id, note)"
          @ignore="(id: number, note: string) => emit('ignore', id, note)"
        />
      </div>

      <div class="pane-card qc-ask">
        <QcAskPanel :asking="asking" :result="askResult" @ask="emit('ask', $event)" />
      </div>
    </div>

    <QcCaseDrawer
      :model-value="!!caseDetail"
      :detail="caseDetail"
      :loading="caseLoading"
      @update:model-value="(visible: boolean) => !visible && emit('close-case')"
      @ack="emit('ack', $event)"
      @resolve="(id: number, note: string) => emit('resolve', id, note)"
      @ignore="(id: number, note: string) => emit('ignore', id, note)"
    />
  </div>
</template>

<style scoped>
.qc-panel {
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

.qc-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.qc-head h2 {
  margin: 0;
  font-size: 17px;
}

.hint {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.scan-info {
  color: #909399;
  font-size: 12px;
}

.qc-main {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(340px, 1fr);
  gap: 16px;
  align-items: start;
}

.qc-list,
.qc-ask {
  min-width: 0;
  min-height: 540px;
}

.pane-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.pane-title h2 {
  margin: 0;
  font-size: 15px;
}

@media (max-width: 1100px) {
  .qc-main {
    grid-template-columns: 1fr;
  }

  .qc-list,
  .qc-ask {
    min-height: auto;
  }
}
</style>
