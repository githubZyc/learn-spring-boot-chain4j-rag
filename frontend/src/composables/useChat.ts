import { ref } from 'vue'
import type { ChatMessage, Source } from '../types'
import { streamChat } from '../api/chat'

const CONVERSATION_KEY = 'rag-conversation-id'

function newConversationId(): string {
  return `c-${crypto.randomUUID()}`
}

function loadConversationId(): string {
  const saved = localStorage.getItem(CONVERSATION_KEY)
  if (saved && /^[A-Za-z0-9_-]{1,64}$/.test(saved)) {
    return saved
  }
  const created = newConversationId()
  localStorage.setItem(CONVERSATION_KEY, created)
  return created
}

export function useChat() {
  const messages = ref<ChatMessage[]>([])
  const conversationId = ref(loadConversationId())
  const streaming = ref(false)
  const error = ref<string | null>(null)
  let controller: AbortController | null = null
  let stoppedByUser = false

  async function send(text: string) {
    const content = text.trim()
    if (!content || streaming.value) {
      return
    }

    error.value = null
    stoppedByUser = false
    messages.value.push({ role: 'user', content, sources: [] })
    messages.value.push({ role: 'assistant', content: '', sources: [] })
    // 取数组里的响应式代理，直接改它才能触发视图更新
    const assistant = messages.value[messages.value.length - 1]

    streaming.value = true
    controller = new AbortController()
    try {
      await streamChat(
        conversationId.value,
        content,
        {
          onSources: (sources: Source[]) => {
            assistant.sources = sources
          },
          onToken: (token: string) => {
            assistant.content += token
          },
          onError: (message: string) => {
            assistant.error = message
            error.value = message
          },
          onDone: () => {
            streaming.value = false
          }
        },
        controller.signal
      )
    } catch (e) {
      if (stoppedByUser) {
        return
      }
      const message = e instanceof Error ? e.message : '请求失败'
      assistant.error = message
      error.value = message
    } finally {
      streaming.value = false
      controller = null
    }
  }

  function stop() {
    stoppedByUser = true
    controller?.abort()
    streaming.value = false
  }

  async function newConversation() {
    stop()
    const previous = conversationId.value
    try {
      await fetch(`/api/chat/${encodeURIComponent(previous)}`, { method: 'DELETE' })
    } catch {
      // 清理失败不影响前端新开会话
    }
    conversationId.value = newConversationId()
    localStorage.setItem(CONVERSATION_KEY, conversationId.value)
    messages.value = []
    error.value = null
  }

  return { messages, conversationId, streaming, error, send, stop, newConversation }
}
