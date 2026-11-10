import type { Source } from '../types'
import { consumeSse } from '../utils/sse'

export interface ChatHandlers {
  onMeta?: (conversationId: string) => void
  onSources?: (sources: Source[]) => void
  onToken?: (text: string) => void
  onDone?: () => void
  onError?: (message: string) => void
}

export async function streamChat(
  conversationId: string,
  message: string,
  handlers: ChatHandlers,
  signal?: AbortSignal
): Promise<void> {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream'
    },
    body: JSON.stringify({ conversationId, message }),
    signal
  })

  if (!response.ok) {
    let detail = `请求失败（HTTP ${response.status}）`
    try {
      const body = await response.json()
      detail = body.message || detail
    } catch {
      // ignore
    }
    throw new Error(detail)
  }
  if (!response.body) {
    throw new Error('浏览器不支持流式响应')
  }

  await consumeSse(response.body, ({ event, data }) => {
    try {
      const payload = JSON.parse(data)
      switch (event) {
        case 'meta':
          handlers.onMeta?.(payload.conversationId)
          break
        case 'sources':
          handlers.onSources?.(payload as Source[])
          break
        case 'token':
          handlers.onToken?.(payload.text ?? '')
          break
        case 'done':
          handlers.onDone?.()
          break
        case 'error':
          handlers.onError?.(payload.message ?? '生成回答失败')
          break
        default:
          break
      }
    } catch {
      // 忽略无法解析的事件，避免整段对话中断
    }
  }, signal)
}
