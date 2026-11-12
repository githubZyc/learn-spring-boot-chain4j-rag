import type { SchemaAskResponse, SchemaStatus, SqlQueryResult } from '../types'
import { request } from './http'

export function getSchemaStatus(): Promise<SchemaStatus> {
  return request<SchemaStatus>('/api/schema/status')
}

export function syncSchema(): Promise<SchemaStatus> {
  return request<SchemaStatus>('/api/schema/sync', { method: 'POST' })
}

export function askSchema(question: string): Promise<SchemaAskResponse> {
  return request<SchemaAskResponse>('/api/schema/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question })
  })
}

export function querySchema(question: string): Promise<SqlQueryResult> {
  return request<SqlQueryResult>('/api/schema/query', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question })
  })
}
