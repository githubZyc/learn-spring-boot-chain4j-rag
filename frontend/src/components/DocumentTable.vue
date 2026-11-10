<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DocumentItem, DocumentStatus } from '../types'
import { formatBytes, formatTime } from '../utils/format'

defineProps<{
  documents: DocumentItem[]
  loading: boolean
  busyId: string | null
}>()

const emit = defineEmits<{
  (e: 'delete', id: string): void
  (e: 'reindex', id: string): void
}>()

const STATUS_META: Record<DocumentStatus, { text: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  READY: { text: '可检索', type: 'success' },
  PROCESSING: { text: '处理中', type: 'warning' },
  PENDING: { text: '等待处理', type: 'info' },
  FAILED: { text: '失败', type: 'danger' }
}

function statusText(status: DocumentStatus): string {
  return STATUS_META[status]?.text ?? status
}

function statusType(status: DocumentStatus): 'success' | 'warning' | 'danger' | 'info' {
  return STATUS_META[status]?.type ?? 'info'
}

async function confirmDelete(id: string, fileName: string) {
  try {
    await ElMessageBox.confirm(`确定删除「${fileName}」吗？删除后向量数据也会被清理。`, '删除文档', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    emit('delete', id)
  } catch {
    // 用户取消
  }
}

function confirmReindex(id: string) {
  emit('reindex', id)
  ElMessage.info('已开始重建索引')
}
</script>

<template>
  <el-table :data="documents" v-loading="loading" empty-text="还没有文档，先上传一个吧" size="small">
    <el-table-column label="文件名" min-width="200">
      <template #default="{ row }">
        <div class="file-cell">
          <span class="file-name" :title="row.fileName">{{ row.fileName }}</span>
          <el-tooltip v-if="row.errorMessage" :content="row.errorMessage" placement="top">
            <el-tag size="small" type="danger">原因</el-tag>
          </el-tooltip>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="大小" width="90">
      <template #default="{ row }">{{ formatBytes(row.sizeBytes) }}</template>
    </el-table-column>
    <el-table-column label="Chunks" width="80" prop="chunkCount" />
    <el-table-column label="状态" width="100">
      <template #default="{ row }">
        <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="上传时间" width="150">
      <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
    </el-table-column>
    <el-table-column label="操作" width="120" fixed="right">
      <template #default="{ row }">
        <el-button
          link
          type="primary"
          size="small"
          :loading="busyId === row.id"
          :disabled="busyId !== null || row.status === 'PENDING' || row.status === 'PROCESSING'"
          @click="confirmReindex(row.id)"
        >
          重建
        </el-button>
        <el-button
          link
          type="danger"
          size="small"
          :disabled="busyId !== null"
          @click="confirmDelete(row.id, row.fileName)"
        >
          删除
        </el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.file-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
