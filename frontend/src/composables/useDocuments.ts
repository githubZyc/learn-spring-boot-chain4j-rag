import { onBeforeUnmount, ref } from 'vue'
import type { DocumentItem } from '../types'
import { deleteDocument, listDocuments, reindexDocument, uploadDocument } from '../api/documents'

const POLL_INTERVAL_MS = 2000

function hasUnfinished(documents: DocumentItem[]): boolean {
  return documents.some((item) => item.status === 'PENDING' || item.status === 'PROCESSING')
}

export function useDocuments() {
  const documents = ref<DocumentItem[]>([])
  const loading = ref(false)
  const uploading = ref(false)
  const busyId = ref<string | null>(null)
  let pollTimer: number | null = null

  function stopPolling() {
    if (pollTimer !== null) {
      window.clearTimeout(pollTimer)
      pollTimer = null
    }
  }

  function schedulePolling() {
    if (pollTimer !== null || !hasUnfinished(documents.value)) {
      return
    }
    pollTimer = window.setTimeout(async () => {
      pollTimer = null
      try {
        await refresh()
      } catch {
        // 轮询失败静默重试
      }
      schedulePolling()
    }, POLL_INTERVAL_MS)
  }

  /** 静默刷新（不触发 loading），供轮询使用 */
  async function refresh() {
    documents.value = await listDocuments()
    schedulePolling()
  }

  async function load() {
    loading.value = true
    try {
      documents.value = await listDocuments()
      schedulePolling()
    } finally {
      loading.value = false
    }
  }

  async function upload(file: File) {
    uploading.value = true
    try {
      const item = await uploadDocument(file)
      await load()
      return item
    } finally {
      uploading.value = false
    }
  }

  async function remove(id: string) {
    busyId.value = id
    try {
      await deleteDocument(id)
      await load()
    } finally {
      busyId.value = null
    }
  }

  async function reindex(id: string) {
    busyId.value = id
    try {
      const item = await reindexDocument(id)
      await load()
      return item
    } finally {
      busyId.value = null
    }
  }

  onBeforeUnmount(stopPolling)

  return { documents, loading, uploading, busyId, load, upload, remove, reindex }
}
