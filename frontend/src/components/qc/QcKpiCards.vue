<script setup lang="ts">
import { computed } from 'vue'
import type { QcOverview } from '../../types'

const props = defineProps<{ overview: QcOverview | null }>()

const cards = computed(() => {
  const overview = props.overview
  return [
    {
      key: 'cases',
      label: '应收数病例',
      value: overview?.shouldHaveData ?? 0,
      hint: `共 ${overview?.totalCases ?? 0} 例`,
      tone: 'blue'
    },
    {
      key: 'open',
      label: '未闭环异常',
      value: overview?.openFindings ?? 0,
      hint: `严重 ${overview?.criticalFindings ?? 0}`,
      tone: 'orange'
    },
    {
      key: 'abnormal',
      label: '异常终止',
      value: overview?.abnormalTerminated ?? 0,
      hint: '需要复盘',
      tone: 'red'
    },
    {
      key: 'archive',
      label: '7 天归档率',
      value: `${Math.round((overview?.archiveRate ?? 0) * 100)}%`,
      hint: `平均 ${overview?.avgArchiveDays ?? 0} 天`,
      tone: 'green'
    },
    {
      key: 'stale',
      label: '状态不一致',
      value: overview?.staleInProgress ?? 0,
      hint: '进行中但已结束',
      tone: 'purple'
    }
  ]
})
</script>

<template>
  <div>
    <div class="kpi-grid">
      <div v-for="card in cards" :key="card.key" class="kpi-card" :class="`tone-${card.tone}`">
        <div class="kpi-label">{{ card.label }}</div>
        <div class="kpi-value">{{ card.value }}</div>
        <div class="kpi-hint">{{ card.hint }}</div>
      </div>
    </div>
    <div v-if="overview" class="range-hint">
      数据范围：{{ overview.from }} ~ {{ overview.to }}
    </div>
  </div>
</template>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.kpi-card {
  position: relative;
  overflow: hidden;
  padding: 16px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
}

.kpi-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
}

.kpi-label {
  color: #909399;
  font-size: 12px;
}

.kpi-value {
  margin-top: 8px;
  color: #303133;
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}

.kpi-hint {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}

.tone-blue::before {
  background: #409eff;
}

.tone-orange::before {
  background: #e6a23c;
}

.tone-red::before {
  background: #f56c6c;
}

.tone-green::before {
  background: #67c23a;
}

.tone-purple::before {
  background: #8e44ad;
}

.range-hint {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
  text-align: right;
}

@media (max-width: 1100px) {
  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
