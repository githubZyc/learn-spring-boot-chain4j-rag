import { onBeforeUnmount, ref } from 'vue'
import type { SchemaStatus, SchemaTableMatch } from '../types'
import { askSchema, getSchemaStatus, syncSchema } from '../api/schema'

const POLL_INTERVAL_MS = 2000

export function useSchema() {
  const status = ref<SchemaStatus | null>(null)
  const matches = ref<SchemaTableMatch[]>([])
  const asking = ref(false)
  let pollTimer: number | null = null

  function stopPolling() {
    if (pollTimer !== null) {
      window.clearTimeout(pollTimer)
      pollTimer = null
    }
  }

  function schedulePolling() {
    if (pollTimer !== null || status.value?.status !== 'RUNNING') {
      return
    }
    pollTimer = window.setTimeout(async () => {
      pollTimer = null
      try {
        status.value = await getSchemaStatus()
      } catch {
        // 轮询失败静默重试
      }
      schedulePolling()
    }, POLL_INTERVAL_MS)
  }

  async function loadStatus() {
    status.value = await getSchemaStatus()
    schedulePolling()
  }

  async function startSync() {
    status.value = await syncSchema()
    schedulePolling()
  }

  async function ask(question: string) {
    asking.value = true
    try {
      const response = await askSchema(question)
      matches.value = response.matches
    } finally {
      asking.value = false
    }
  }

  onBeforeUnmount(stopPolling)

  return { status, matches, asking, loadStatus, startSync, ask }
}
