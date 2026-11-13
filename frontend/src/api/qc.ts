import type {
  QcCaseDetail,
  QcFinding,
  QcFindingFilters,
  QcOverview,
  QcPage,
  QcRule,
  QcScanResult,
  SqlQueryResult
} from '../types'
import { request } from './http'

function queryString(params: object): string {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}

export function getQcOverview(params?: {
  from?: string
  to?: string
  departId?: string
}): Promise<QcOverview> {
  return request<QcOverview>(`/api/qc/overview${queryString(params ?? {})}`)
}

export function getQcFindings(params: {
  status?: string
  severity?: string
  ruleCode?: string
  departId?: string
  from?: string
  to?: string
  page: number
  size: number
}): Promise<QcPage<QcFinding>> {
  return request<QcPage<QcFinding>>(`/api/qc/findings${queryString(params)}`)
}

export function getQcCase(recordId: number): Promise<QcCaseDetail> {
  return request<QcCaseDetail>(`/api/qc/cases/${recordId}`)
}

export function askQc(question: string): Promise<SqlQueryResult> {
  return request<SqlQueryResult>('/api/qc/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question })
  })
}

export function scanQc(days = 30): Promise<QcScanResult> {
  return request<QcScanResult>(`/api/qc/scan?days=${days}`, { method: 'POST' })
}

export function ackQcFinding(id: number, operator = 'frontend'): Promise<QcFinding> {
  return request<QcFinding>(`/api/qc/findings/${id}/ack?operator=${encodeURIComponent(operator)}`, {
    method: 'POST'
  })
}

export function resolveQcFinding(
  id: number,
  note?: string,
  operator = 'frontend'
): Promise<QcFinding> {
  const query = queryString({ operator, note })
  return request<QcFinding>(`/api/qc/findings/${id}/resolve${query}`, { method: 'POST' })
}

export function ignoreQcFinding(
  id: number,
  note?: string,
  operator = 'frontend'
): Promise<QcFinding> {
  const query = queryString({ operator, note })
  return request<QcFinding>(`/api/qc/findings/${id}/ignore${query}`, { method: 'POST' })
}

export function getQcRules(): Promise<QcRule[]> {
  return request<QcRule[]>('/api/qc/rules')
}

export function filtersToParams(filters: QcFindingFilters): Record<string, string | number | undefined> {
  return {
    status: filters.status || undefined,
    severity: filters.severity || undefined,
    ruleCode: filters.ruleCode || undefined,
    departId: filters.departId || undefined,
    page: filters.page,
    size: filters.size
  }
}
