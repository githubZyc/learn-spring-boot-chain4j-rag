import { ref } from 'vue'
import type {
  QcCaseDetail,
  QcFinding,
  QcFindingFilters,
  QcOverview,
  QcRule,
  QcScanResult,
  SqlQueryResult
} from '../types'
import {
  ackQcFinding,
  askQc,
  getQcCase,
  getQcFindings,
  getQcOverview,
  getQcRules,
  ignoreQcFinding,
  resolveQcFinding,
  scanQc
} from '../api/qc'

export function useQc() {
  const overview = ref<QcOverview | null>(null)
  const findings = ref<QcFinding[]>([])
  const total = ref(0)
  const rules = ref<QcRule[]>([])
  const loading = ref(false)
  const scanning = ref(false)
  const asking = ref(false)
  const caseLoading = ref(false)
  const caseDetail = ref<QcCaseDetail | null>(null)
  const askResult = ref<SqlQueryResult | null>(null)
  const scanResult = ref<QcScanResult | null>(null)
  const rangeDays = ref(120)
  const filters = ref<QcFindingFilters>({
    status: '',
    severity: '',
    ruleCode: '',
    departId: '',
    page: 1,
    size: 10
  })

  function formatDate(date: Date): string {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }

  function rangeParams(): { from: string; to: string } {
    const to = new Date()
    const from = new Date()
    from.setDate(from.getDate() - rangeDays.value)
    return { from: formatDate(from), to: formatDate(to) }
  }

  async function loadOverview() {
    overview.value = await getQcOverview(rangeParams())
  }

  async function loadFindings() {
    const current = filters.value
    const page = await getQcFindings({
      status: current.status || undefined,
      severity: current.severity || undefined,
      ruleCode: current.ruleCode || undefined,
      departId: current.departId || undefined,
      ...rangeParams(),
      page: current.page,
      size: current.size
    })
    findings.value = page.items
    total.value = page.total
  }

  async function loadRules() {
    rules.value = await getQcRules()
  }

  async function loadAll() {
    loading.value = true
    try {
      await Promise.all([loadOverview(), loadFindings(), loadRules()])
    } finally {
      loading.value = false
    }
  }

  async function scan() {
    scanning.value = true
    try {
      scanResult.value = await scanQc(rangeDays.value)
      await loadAll()
    } finally {
      scanning.value = false
    }
  }

  async function setRange(days: number) {
    rangeDays.value = days
    await loadAll()
  }

  async function openCase(recordId: number) {
    caseLoading.value = true
    try {
      caseDetail.value = await getQcCase(recordId)
    } finally {
      caseLoading.value = false
    }
  }

  function closeCase() {
    caseDetail.value = null
  }

  async function ask(question: string) {
    asking.value = true
    try {
      askResult.value = await askQc(question)
    } finally {
      asking.value = false
    }
  }

  async function ack(id: number) {
    await ackQcFinding(id)
    await refreshAfterAction()
  }

  async function resolve(id: number, note?: string) {
    await resolveQcFinding(id, note)
    await refreshAfterAction()
  }

  async function ignore(id: number, note?: string) {
    await ignoreQcFinding(id, note)
    await refreshAfterAction()
  }

  async function refreshAfterAction() {
    await Promise.all([loadOverview(), loadFindings()])
    if (caseDetail.value) {
      await openCase(caseDetail.value.qcCase.id)
    }
  }

  async function setFilters(partial: Partial<QcFindingFilters>) {
    const next = { ...filters.value, ...partial }
    if (partial.page === undefined) {
      next.page = 1
    }
    filters.value = next
    await loadFindings()
  }

  return {
    overview,
    findings,
    total,
    rules,
    loading,
    scanning,
    asking,
    caseLoading,
    caseDetail,
    askResult,
    scanResult,
    rangeDays,
    filters,
    loadAll,
    scan,
    setRange,
    openCase,
    closeCase,
    ask,
    ack,
    resolve,
    ignore,
    setFilters
  }
}
