import type { DocumentItem } from '../types'
import { request } from './http'

export function listDocuments(): Promise<DocumentItem[]> {
  return request<DocumentItem[]>('/api/documents')
}

export function uploadDocument(file: File): Promise<DocumentItem> {
  const form = new FormData()
  form.append('file', file)
  return request<DocumentItem>('/api/documents', { method: 'POST', body: form })
}

export function deleteDocument(id: string): Promise<void> {
  return request<void>(`/api/documents/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export function reindexDocument(id: string): Promise<DocumentItem> {
  return request<DocumentItem>(`/api/documents/${encodeURIComponent(id)}/reindex`, {
    method: 'POST'
  })
}
