export interface SseEvent {
  event: string
  data: string
}

/**
 * 把字节流解析成 SSE 事件。EventSource 不支持 POST，所以这里手动解析 fetch 的 ReadableStream。
 */
export async function consumeSse(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: SseEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  const reader = body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const flushFrame = (frame: string) => {
    const lines = frame.split('\n')
    let event = 'message'
    const dataLines: string[] = []
    for (const line of lines) {
      if (line.startsWith(':')) {
        continue
      }
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).replace(/^ /, ''))
      }
    }
    if (dataLines.length > 0) {
      onEvent({ event, data: dataLines.join('\n') })
    }
  }

  try {
    while (true) {
      if (signal?.aborted) {
        break
      }
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      let separator = buffer.indexOf('\n\n')
      while (separator >= 0) {
        flushFrame(buffer.slice(0, separator))
        buffer = buffer.slice(separator + 2)
        separator = buffer.indexOf('\n\n')
      }
    }
    buffer += decoder.decode()
    if (buffer.trim().length > 0) {
      flushFrame(buffer)
    }
  } finally {
    reader.releaseLock()
  }
}
