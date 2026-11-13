<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import type {
  QcFinding,
  QcFindingFilters,
  QcFindingStatus,
  QcRule,
  QcSeverity
} from '../../types'

defineProps<{
  findings: QcFinding[]
  total: number
  loading: boolean
  rules: QcRule[]
  filters: QcFindingFilters
}>()

const emit = defineEmits<{
  'filter-change': [Partial<QcFindingFilters>]
  'page-change': [number]
  refresh: []
  view: [number]
  ack: [number]
  resolve: [number, string]
  ignore: [number, string]
}>()

const severityLabels: Record<QcSeverity, string> = {
  CRITICAL: '严重',
  WARNING: '警告',
  INFO: '提示'
}

const statusLabels: Record<QcFindingStatus, string> = {
  OPEN: '待处理',
  ACK: '处理中',
  RESOLVED: '已完成',
  IGNORED: '已忽略'
}

function severityLabel(severity: QcSeverity): string {
  return severityLabels[severity] ?? severity
}

function statusLabel(status: QcFindingStatus): string {
  return statusLabels[status] ?? status
}

function severityType(severity: QcSeverity): 'danger' | 'warning' | 'info' {
  if (severity === 'CRITICAL') {
    return 'danger'
  }
  return severity === 'WARNING' ? 'warning' : 'info'
}

function statusType(status: QcFindingStatus): 'danger' | 'warning' | 'success' | 'info' {
  switch (status) {
    case 'OPEN':
      return 'danger'
    case 'ACK':
      return 'warning'
    case 'RESOLVED':
      return 'success'
    default:
      return 'info'
  }
}

function changeStatus(value: string) {
  emit('filter-change', { status: (value || '') as QcFindingFilters['status'] })
}

function changeSeverity(value: string) {
  emit('filter-change', { severity: (value || '') as QcFindingFilters['severity'] })
}

function changeRule(value: string) {
  emit('filter-change', { ruleCode: value || '' })
}

function changeDepart(value: string) {
  emit('filter-change', { departId: value || '' })
}

function resetFilters() {
  emit('filter-change', {
    status: '',
    severity: '',
    ruleCode: '',
    departId: '',
    page: 1
  })
}

async function resolveFinding(row: QcFinding) {
  try {
    const { value } = await ElMessageBox.prompt('请填写处理说明（可选）', '处理异常', {
      inputPlaceholder: '例如：已补录麻醉数据',
      confirmButtonText: '确认完成',
      cancelButtonText: '取消'
    })
    emit('resolve', row.id, value ?? '')
  } catch {
    // 用户取消
  }
}

async function ignoreFinding(row: QcFinding) {
  try {
    const { value } = await ElMessageBox.prompt('请填写忽略原因（会记录）', '标记非问题', {
      inputPlaceholder: '例如：该病例为历史补录，无需处理',
      confirmButtonText: '确认忽略',
      cancelButtonText: '取消'
    })
    emit('ignore', row.id, value ?? '')
  } catch {
    // 用户取消
  }
}
</script>

<template>
  <div class="finding-panel">
    <div class="filter-row">
      <el-select
        :model-value="filters.status"
        placeholder="状态"
        clearable
        class="filter"
        @change="changeStatus"
      >
        <el-option label="待处理" value="OPEN" />
        <el-option label="处理中" value="ACK" />
        <el-option label="已完成" value="RESOLVED" />
        <el-option label="已忽略" value="IGNORED" />
      </el-select>
      <el-select
        :model-value="filters.severity"
        placeholder="级别"
        clearable
        class="filter"
        @change="changeSeverity"
      >
        <el-option label="严重" value="CRITICAL" />
        <el-option label="警告" value="WARNING" />
        <el-option label="提示" value="INFO" />
      </el-select>
      <el-select
        :model-value="filters.ruleCode"
        placeholder="规则"
        clearable
        class="filter rule"
        @change="changeRule"
      >
        <el-option v-for="rule in rules" :key="rule.code" :label="rule.name" :value="rule.code" />
      </el-select>
      <el-input
        :model-value="filters.departId"
        placeholder="分院 ID"
        clearable
        class="filter"
        @change="changeDepart"
      />
      <el-button @click="resetFilters">重置</el-button>
      <el-button :loading="loading" @click="emit('refresh')">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="findings" border size="small" row-key="id">
      <el-table-column label="级别" width="82">
        <template #default="{ row }">
          <el-tag :type="severityType(row.severity)" size="small">
            {{ severityLabel(row.severity) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ruleName" label="规则" width="126" show-overflow-tooltip />
      <el-table-column label="病例" width="168">
        <template #default="{ row }">
          <div class="case-no">{{ row.recordNo }}</div>
          <div class="case-sub">{{ row.departName || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="surgeryDate" label="手术日期" width="108" />
      <el-table-column prop="title" label="异常" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="92">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="208" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="emit('view', row.recordId)">查看</el-button>
          <el-button
            v-if="row.status === 'OPEN'"
            link
            type="warning"
            @click="emit('ack', row.id)"
          >
            认领
          </el-button>
          <el-button
            v-if="row.status === 'OPEN' || row.status === 'ACK'"
            link
            type="success"
            @click="resolveFinding(row)"
          >
            完成
          </el-button>
          <el-button
            v-if="row.status === 'OPEN' || row.status === 'ACK'"
            link
            type="info"
            @click="ignoreFinding(row)"
          >
            忽略
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="filters.size"
        :current-page="filters.page"
        @current-change="(page: number) => emit('page-change', page)"
      />
    </div>
  </div>
</template>

<style scoped>
.finding-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.filter {
  width: 120px;
}

.filter.rule {
  width: 170px;
}

.case-no {
  color: #303133;
  font-weight: 600;
}

.case-sub {
  color: #909399;
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
