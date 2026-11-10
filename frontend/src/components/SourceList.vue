<script setup lang="ts">
import type { Source } from '../types'

defineProps<{ sources: Source[] }>()

function scoreText(score: number): string {
  return score > 0 ? score.toFixed(3) : '-'
}
</script>

<template>
  <div v-if="sources.length > 0" class="source-list">
    <el-collapse>
      <el-collapse-item :title="`引用来源（${sources.length}）`" name="sources">
        <div v-for="source in sources" :key="`${source.docId}-${source.index}`" class="source-item">
          <div class="source-header">
            <el-tag size="small" type="info">[{{ source.index }}]</el-tag>
            <span class="file-name">{{ source.fileName }}</span>
            <span class="meta">
              chunk {{ source.chunkIndex ?? '-' }} · 相似度 {{ scoreText(source.score) }}
            </span>
          </div>
          <div class="snippet">{{ source.snippet }}</div>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.source-list {
  margin-top: 10px;
}

.source-item + .source-item {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #e4e7ed;
}

.source-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.file-name {
  font-weight: 600;
  font-size: 13px;
}

.meta {
  color: #909399;
  font-size: 12px;
}

.snippet {
  margin-top: 6px;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
