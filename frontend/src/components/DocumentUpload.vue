<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { UploadRawFile, UploadRequestOptions } from 'element-plus'

const props = defineProps<{ uploading: boolean }>()
const emit = defineEmits<{ (e: 'upload', file: File): void }>()

const MAX_SIZE = 10 * 1024 * 1024

function beforeUpload(file: UploadRawFile): boolean {
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (extension !== 'md' && extension !== 'txt' && extension !== 'pdf') {
    ElMessage.error('支持 .md 和 .txt 文件，以及 .pdf 文件')
    return false
  }
  if (file.size > MAX_SIZE) {
    ElMessage.error('文件不能超过 10MB')
    return false
  }
  return true
}

function handleRequest(options: UploadRequestOptions) {
  emit('upload', options.file)
  // 返回已完成的 Promise，让 el-upload 结束自身的上传状态；实际结果由父组件提示
  return Promise.resolve()
}
</script>

<template>
  <div class="uploader">
    <el-upload
      drag
      :show-file-list="false"
      accept=".md,.txt,.pdf"
      :before-upload="beforeUpload"
      :http-request="handleRequest"
      :disabled="props.uploading"
    >
      <div class="upload-content">
        <div class="upload-symbol">+</div>
        <div class="upload-text">拖拽文件到这里，或 <em>点击选择</em></div>
        <div class="upload-hint">支持 .md / .txt / .pdf，单个文件不超过 10MB</div>
      </div>
    </el-upload>
    <el-progress
      v-if="props.uploading"
      :percentage="100"
      :indeterminate="true"
      :show-text="false"
    />
  </div>
</template>

<style scoped>
.upload-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 8px 0;
}

.upload-symbol {
  width: 34px;
  height: 34px;
  line-height: 30px;
  border-radius: 50%;
  border: 1px dashed #c0c4cc;
  color: #909399;
  font-size: 22px;
}

.upload-text em {
  color: #409eff;
  font-style: normal;
}

.upload-hint {
  color: #909399;
  font-size: 12px;
}
</style>
