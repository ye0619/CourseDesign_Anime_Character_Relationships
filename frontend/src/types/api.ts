export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  requestId: string | null
}

export interface Readiness {
  status: 'READY' | 'NOT_READY'
  dependencies: Record<string, 'UP' | 'DOWN'>
  checkedAt: string
}
