import request from '../utils/request'

export interface NaturalQueryColumn {
  key: string
  label: string
  type: string
}

export interface NaturalQueryResult {
  summary: string
  columns: NaturalQueryColumn[]
  rows: Record<string, unknown>[]
  sqlPreview?: string
  planPreview?: Record<string, unknown>
  clarificationRequired: boolean
  clarificationOptions: string[]
  intent?: string
  page: number
  size: number
  total: number
}

export function runNaturalQuery(data: { question: string; page?: number; size?: number }) {
  return request.post('/natural-query', data, { timeout: 60000 }) as unknown as Promise<NaturalQueryResult>
}
