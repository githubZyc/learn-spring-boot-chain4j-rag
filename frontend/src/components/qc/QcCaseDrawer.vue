<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import type {
  QcCaseDetail,
  QcFinding,
  QcFindingStatus,
  QcSeverity,
  QcStageStatus
} from '../../types'

defineProps<{
  modelValue: boolean
  detail: QcCaseDetail | null
  loading: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  ack: [number]
  resolve: [number, string]
  ignore: [number, string]
}>()

const qcStatusLabels: Record<number, string> = {
  0: '未开始',
  1: '进行中',
  2: '已完成',
  3: '异常终止',
  4: '已归档',
  5: '已失效'
}

const severityLabels: Record<QcSeverity, string> = {
  CRITICAL: '严重',
  WARNING: '警告',
  INFO: '提示'
}

const findingStatusLabels: Record<QcFindingStatus, string> = {
  OPEN: '待处理',
  ACK: '处理中',
  RESOLVED: '已完成',
  IGNORED: '已忽略'
}

const stageStatusLabels: Record<QcStageStatus, string> = {
  DONE: '已完成',
  IN_PROGRESS: '进行中',
  PENDING: '未开始',
  MISSING: '数据缺失'
}

function field(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value)
}

function statusText(status: number): string {
  return qcStatusLabels[status] ?? `状态 ${status}`
}

function statusType(status: number): 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 1:
      return 'warning'
    case 2:
    case 4:
      return 'success'
    case 3:
      return 'danger'
    default:
      return 'info'
  }
}

function techniqueText(value: string): string {
  if (!value) {
    return '技术未填'
  }
  const labels: string[] = []
  if (value.split(',').map((item) => item.trim()).includes('1')) {
    labels.push('常规植发')
  }
  if (value.split(',').map((item) => item.trim()).includes('2')) {
    labels.push('长发植发')
  }
  return labels.length ? labels.join(' / ') : value
}

function severityType(severity: QcSeverity): 'danger' | 'warning' | 'info' {
  if (severity === 'CRITICAL') {
    return 'danger'
  }
  return severity === 'WARNING' ? 'warning' : 'info'
}

function findingStatusType(status: QcFindingStatus): 'danger' | 'warning' | 'success' | 'info' {
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

async function resolveFinding(finding: QcFinding) {
  try {
    const { value } = await ElMessageBox.prompt('请填写处理说明（可选）', '处理异常', {
      inputPlaceholder: '例如：已补录麻醉数据',
      confirmButtonText: '确认完成',
      cancelButtonText: '取消'
    })
    emit('resolve', finding.id, value ?? '')
  } catch {
    // 用户取消
  }
}

async function ignoreFinding(finding: QcFinding) {
  try {
    const { value } = await ElMessageBox.prompt('请填写忽略原因（会记录）', '标记非问题', {
      inputPlaceholder: '例如：历史补录，无需处理',
      confirmButtonText: '确认忽略',
      cancelButtonText: '取消'
    })
    emit('ignore', finding.id, value ?? '')
  } catch {
    // 用户取消
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    size="62%"
    title="病例 360"
    @update:model-value="(visible: boolean) => emit('update:modelValue', visible)"
  >
    <div v-loading="loading" class="drawer-body">
      <template v-if="detail">
        <div class="case-head">
          <div>
            <h3>{{ detail.qcCase.recordNo }}</h3>
            <p>
              {{ detail.qcCase.departName }} · 手术日期 {{ detail.qcCase.surgeryDate }}
              · 客户 {{ detail.qcCase.accountName || '-' }}
            </p>
          </div>
          <div class="case-tags">
            <el-tag :type="statusType(detail.qcCase.qcStatus)">
              {{ statusText(detail.qcCase.qcStatus) }}
            </el-tag>
            <el-tag effect="plain">{{ techniqueText(detail.qcCase.transplantTechnique) }}</el-tag>
            <el-tag effect="plain">当前阶段 {{ detail.qcCase.currentStage }}</el-tag>
          </div>
        </div>

        <el-alert
          v-if="detail.missingStages.length"
          type="error"
          :closable="false"
          show-icon
          :title="`缺少阶段数据：${detail.missingStages.join('、')}`"
          class="missing-alert"
        />

        <section class="section">
          <h4>阶段时间线</h4>
          <div class="stages">
            <div
              v-for="stage in detail.stages"
              :key="stage.code"
              class="stage"
              :class="`stage-${stage.status.toLowerCase()}`"
            >
              <div class="stage-name">{{ stage.name }}</div>
              <div class="stage-status">{{ stageStatusLabels[stage.status] }}</div>
              <div class="stage-time">
                {{ stage.start || '-' }}<br />
                {{ stage.end || '-' }}
              </div>
              <div v-if="stage.durationMinutes !== null" class="stage-duration">
                {{ stage.durationMinutes }} 分钟
              </div>
            </div>
          </div>
        </section>

        <section class="section">
          <h4>基本信息</h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="客户">
              {{ field(detail.qcCase.accountName) }}（{{ field(detail.qcCase.accountCode) }}）
            </el-descriptions-item>
            <el-descriptions-item label="手术医生">{{ field(detail.qcCase.operationId) }}</el-descriptions-item>
            <el-descriptions-item label="质控护士">{{ field(detail.qcCase.qcNurseName) }}</el-descriptions-item>
            <el-descriptions-item label="病历编号">{{ field(detail.qcCase.caseId) }}</el-descriptions-item>
            <el-descriptions-item label="电子病历 ID">{{ field(detail.qcCase.dzblNo) }}</el-descriptions-item>
            <el-descriptions-item label="收费单">{{ field(detail.qcCase.chargeOrderId) }}</el-descriptions-item>
            <el-descriptions-item label="手术开始">{{ field(detail.qcCase.surgeryStartTime) }}</el-descriptions-item>
            <el-descriptions-item label="手术结束">{{ field(detail.qcCase.surgeryEndTime) }}</el-descriptions-item>
            <el-descriptions-item label="归档时间">{{ field(detail.qcCase.filedTime) }}</el-descriptions-item>
            <el-descriptions-item label="归档人">{{ field(detail.qcCase.filedBy) }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="section">
          <h4>开台</h4>
          <el-descriptions v-if="detail.qcCase.setup" :column="2" border size="small">
            <el-descriptions-item label="手术室">{{ field(detail.qcCase.setup?.operatingRoomName) }}</el-descriptions-item>
            <el-descriptions-item label="取发针">{{ field(detail.qcCase.setup?.extractionNeedle) }}</el-descriptions-item>
            <el-descriptions-item label="取发区域">{{ field(detail.qcCase.setup?.extractionArea) }}</el-descriptions-item>
            <el-descriptions-item label="难度因素">{{ field(detail.qcCase.setup?.difficultyFactorName) }}</el-descriptions-item>
            <el-descriptions-item label="师带徒">{{ field(detail.qcCase.setup?.mentorApprentice) }}</el-descriptions-item>
            <el-descriptions-item label="二级项">{{ field(detail.qcCase.setup?.mentorApprenticeSon) }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="缺少开台数据" :image-size="50" />
        </section>

        <section class="section">
          <h4>麻醉</h4>
          <el-descriptions v-if="detail.qcCase.anesthesia" :column="2" border size="small">
            <el-descriptions-item label="开始">{{ field(detail.qcCase.anesthesia?.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="结束">{{ field(detail.qcCase.anesthesia?.endTime) }}</el-descriptions-item>
            <el-descriptions-item label="时长">
              {{ detail.qcCase.anesthesia?.durationMinutes === null ? '-' : `${detail.qcCase.anesthesia?.durationMinutes} 分钟` }}
            </el-descriptions-item>
            <el-descriptions-item label="操作人">{{ field(detail.qcCase.anesthesia?.operatorName) }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ field(detail.qcCase.anesthesia?.remark) }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="缺少麻醉数据" :image-size="50" />
        </section>

        <section class="section">
          <h4>取发 / 分离</h4>
          <el-descriptions v-if="detail.qcCase.extraction" :column="2" border size="small">
            <el-descriptions-item label="取发开始">{{ field(detail.qcCase.extraction?.extractionStartTime) }}</el-descriptions-item>
            <el-descriptions-item label="取发结束">{{ field(detail.qcCase.extraction?.extractionEndTime) }}</el-descriptions-item>
            <el-descriptions-item label="仅拔毛">
              {{ detail.qcCase.extraction?.extractionOnly ? '是' : '否' }}
            </el-descriptions-item>
            <el-descriptions-item label="拔毛量">{{ field(detail.qcCase.extraction?.extractionCount) }}</el-descriptions-item>
            <el-descriptions-item label="常规单根">{{ field(detail.qcCase.extraction?.regularSeparationSingle) }}</el-descriptions-item>
            <el-descriptions-item label="常规多根">{{ field(detail.qcCase.extraction?.regularSeparationMulti) }}</el-descriptions-item>
            <el-descriptions-item label="长发单根">{{ field(detail.qcCase.extraction?.longSeparationSingle) }}</el-descriptions-item>
            <el-descriptions-item label="长发多根">{{ field(detail.qcCase.extraction?.longSeparationMulti) }}</el-descriptions-item>
            <el-descriptions-item label="护士">{{ field(detail.qcCase.extraction?.nurseName) }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="缺少取发数据" :image-size="50" />
        </section>

        <section class="section">
          <h4>种植</h4>
          <el-descriptions v-if="detail.qcCase.planting" :column="2" border size="small">
            <el-descriptions-item label="开始">{{ field(detail.qcCase.planting?.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="结束">{{ field(detail.qcCase.planting?.endTime) }}</el-descriptions-item>
            <el-descriptions-item label="常规单根">{{ field(detail.qcCase.planting?.regularPlantingSingle) }}</el-descriptions-item>
            <el-descriptions-item label="常规多根">{{ field(detail.qcCase.planting?.regularPlantingMulti) }}</el-descriptions-item>
            <el-descriptions-item label="常规位置">{{ field(detail.qcCase.planting?.regularPlantingPosition) }}</el-descriptions-item>
            <el-descriptions-item label="长发单根">{{ field(detail.qcCase.planting?.longPlantingSingle) }}</el-descriptions-item>
            <el-descriptions-item label="长发多根">{{ field(detail.qcCase.planting?.longPlantingMulti) }}</el-descriptions-item>
            <el-descriptions-item label="长发位置">{{ field(detail.qcCase.planting?.longPlantingPosition) }}</el-descriptions-item>
            <el-descriptions-item label="护士">{{ field(detail.qcCase.planting?.nurseName) }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="缺少种植数据" :image-size="50" />
        </section>

        <section class="section">
          <h4>异常（{{ detail.findings.length }}）</h4>
          <el-empty v-if="!detail.findings.length" description="本病例没有异常" :image-size="60" />
          <div v-else class="finding-list">
            <div v-for="finding in detail.findings" :key="finding.id" class="finding-item">
              <div class="finding-head">
                <div class="finding-title">
                  <el-tag :type="severityType(finding.severity)" size="small">
                    {{ severityLabels[finding.severity] }}
                  </el-tag>
                  <strong>{{ finding.title }}</strong>
                </div>
                <el-tag :type="findingStatusType(finding.status)" size="small">
                  {{ findingStatusLabels[finding.status] }}
                </el-tag>
              </div>
              <p class="finding-detail">{{ finding.detail }}</p>
              <div class="finding-actions">
                <el-button
                  v-if="finding.status === 'OPEN'"
                  link
                  type="warning"
                  @click="emit('ack', finding.id)"
                >
                  认领
                </el-button>
                <el-button
                  v-if="finding.status === 'OPEN' || finding.status === 'ACK'"
                  link
                  type="success"
                  @click="resolveFinding(finding)"
                >
                  完成
                </el-button>
                <el-button
                  v-if="finding.status === 'OPEN' || finding.status === 'ACK'"
                  link
                  type="info"
                  @click="ignoreFinding(finding)"
                >
                  忽略
                </el-button>
              </div>
            </div>
          </div>
        </section>
      </template>
      <el-empty v-else description="选择一条异常查看病例详情" />
    </div>
  </el-drawer>
</template>

<style scoped>
.drawer-body {
  min-height: 300px;
}

.case-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.case-head h3 {
  margin: 0;
  font-size: 18px;
}

.case-head p {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}

.case-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.missing-alert {
  margin-top: 12px;
}

.section {
  margin-top: 20px;
}

.section h4 {
  margin: 0 0 10px;
  font-size: 14px;
}

.stages {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.stage {
  padding: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fafafa;
}

.stage-name {
  font-weight: 600;
}

.stage-status {
  margin-top: 4px;
  font-size: 12px;
}

.stage-time {
  margin-top: 6px;
  color: #909399;
  font-size: 11px;
  line-height: 1.5;
}

.stage-duration {
  margin-top: 4px;
  color: #606266;
  font-size: 11px;
}

.stage-done {
  border-color: #b3e19d;
  background: #f0f9eb;
}

.stage-in_progress {
  border-color: #f3d19e;
  background: #fdf6ec;
}

.stage-missing {
  border-color: #fab6b6;
  background: #fef0f0;
}

.stage-pending {
  opacity: 0.7;
}

.finding-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.finding-item {
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}

.finding-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.finding-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.finding-detail {
  margin: 8px 0 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}

.finding-actions {
  margin-top: 6px;
  display: flex;
  justify-content: flex-end;
}
</style>
