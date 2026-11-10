<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { ChatMessage } from '../types'
import ChatMessageView from './ChatMessage.vue'

const props = defineProps<{
  messages: ChatMessage[]
  streaming: boolean
  error: string | null
}>()

const emit = defineEmits<{
  (e: 'send', text: string): void
  (e: 'stop'): void
  (e: 'new-conversation'): void
}>()

const input = ref('')
const listRef = ref<HTMLElement | null>(null)

function submit() {
  const text = input.value.trim()
  if (!text || props.streaming) {
    return
  }
  input.value = ''
  emit('send', text)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    submit()
  }
}

watch(
  () => props.messages.map((message) => message.content).join('|'),
  async () => {
    const element = listRef.value
    if (!element) {
      return
    }
    const nearBottom = element.scrollHeight - element.scrollTop - element.clientHeight < 120
    if (nearBottom || props.streaming) {
      await nextTick()
      element.scrollTop = element.scrollHeight
    }
  }
)
</script>

<template>
  <section class="chat-panel">
    <header class="chat-header">
      <div>
        <h2>知识库问答</h2>
        <p>回答只基于已入库文档，并附带引用来源</p>
      </div>
      <el-button size="small" :disabled="streaming" @click="emit('new-conversation')">
        新会话
      </el-button>
    </header>

    <div ref="listRef" class="chat-list">
      <div v-if="messages.length === 0" class="empty">
        <div class="empty-title">开始提问吧</div>
        <div class="empty-tip">例如：合同中心接口的分页参数是什么？</div>
      </div>
      <ChatMessageView
        v-for="(message, index) in messages"
        :key="index"
        :message="message"
        :streaming="streaming && index === messages.length - 1"
      />
    </div>

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
      class="chat-error"
    />

    <footer class="chat-input">
      <el-input
        v-model="input"
        type="textarea"
        :rows="3"
        resize="none"
        placeholder="输入问题，Enter 发送，Shift+Enter 换行"
        :disabled="streaming"
        @keydown="onKeydown"
      />
      <div class="actions">
        <el-button v-if="streaming" type="warning" @click="emit('stop')">停止</el-button>
        <el-button v-else type="primary" :disabled="!input.trim()" @click="submit">
          发送
        </el-button>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #fafbfc;
  border-radius: 10px;
  border: 1px solid #e4e7ed;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
}

.chat-header h2 {
  margin: 0;
  font-size: 16px;
}

.chat-header p {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
}

.chat-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px;
}

.empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #909399;
  gap: 6px;
}

.empty-title {
  font-size: 18px;
  color: #606266;
}

.chat-error {
  margin: 0 18px 8px;
}

.chat-input {
  padding: 12px 18px 16px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
}

.actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
