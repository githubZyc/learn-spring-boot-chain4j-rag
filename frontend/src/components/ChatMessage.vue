<script setup lang="ts">
import type { ChatMessage } from '../types'
import MarkdownView from './MarkdownView.vue'
import SourceList from './SourceList.vue'

defineProps<{ message: ChatMessage; streaming?: boolean }>()
</script>

<template>
  <div class="message" :class="message.role">
    <div class="avatar">{{ message.role === 'user' ? '我' : 'AI' }}</div>
    <div class="bubble">
      <template v-if="message.role === 'user'">
        <div class="plain">{{ message.content }}</div>
      </template>
      <template v-else>
        <div v-if="!message.content && streaming" class="thinking">正在思考…</div>
        <MarkdownView v-else :content="message.content" />
        <el-alert
          v-if="message.error"
          :title="message.error"
          type="error"
          :closable="false"
          show-icon
          class="error"
        />
        <SourceList :sources="message.sources" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.message {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.message.user {
  flex-direction: row-reverse;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
  flex-shrink: 0;
  background: #409eff;
}

.message.assistant .avatar {
  background: #67c23a;
}

.bubble {
  max-width: 85%;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e4e7ed;
}

.message.user .bubble {
  background: #ecf5ff;
  border-color: #b3d8ff;
}

.plain {
  white-space: pre-wrap;
  word-break: break-word;
}

.thinking {
  color: #909399;
}

.error {
  margin-top: 8px;
}
</style>
