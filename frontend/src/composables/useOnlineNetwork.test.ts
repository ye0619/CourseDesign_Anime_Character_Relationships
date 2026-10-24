import { describe, expect, it, vi, beforeEach } from 'vitest'
import { effectScope, nextTick, shallowRef } from 'vue'
import { get } from '../api/client'
import { useOnlineNetwork } from './useOnlineNetwork'
import type { NetworkKind, OnlineNetwork } from '../types/onlineNetwork'
vi.mock('../api/client', () => ({ get: vi.fn() }))
const graph = (id: number, kind: NetworkKind = 'CHARACTERS'): OnlineNetwork => ({ subjectId: id, kind, nodes: [{ id: `character:bangumi:${id}`, label: String(id), role: '', group: '', description: '', category: 'CHARACTER', roles: [], sourceUrl: '' }], edges: [], page: 1, pageSize: 60, total: 1, meta: { fetchedAt: '', cached: false, stale: false, truncated: false, notice: '' } })
const settle = async () => { for (let i = 0; i < 8; i++) await nextTick() }
beforeEach(() => { vi.mocked(get).mockReset() })
describe('online graph loading lifecycle', () => {
  it('does not read online data while the source is disabled', async () => {
    const enabled=shallowRef(false), scope=effectScope()
    vi.mocked(get).mockResolvedValueOnce(graph(1)).mockResolvedValueOnce({nodes:[],edges:[],meta:{stale:false,truncated:false}})
    const state=scope.run(()=>useOnlineNetwork(shallowRef('1'),shallowRef<NetworkKind>('CHARACTERS'),enabled))!
    try {
      await settle();expect(vi.mocked(get)).not.toHaveBeenCalled()
      enabled.value=true;await settle();expect(vi.mocked(get)).toHaveBeenCalled()
      enabled.value=false;await settle();expect(state.nodes.value).toHaveLength(0);expect(state.loading.value).toBe(false)
    } finally {scope.stop()}
  })
  it('pauses unstarted batches on 429 and keeps partial results available for explicit retry', async () => {
    const roster = { ...graph(1), nodes: Array.from({ length: 12 }, (_, i) => ({ ...graph(1).nodes[0]!, id: `character:bangumi:${i + 1}` })) }
    vi.mocked(get).mockResolvedValueOnce(roster).mockRejectedValueOnce(Object.assign(new Error('rate limited'), { status: 429 })).mockResolvedValue({ nodes: [], edges: [], meta: { stale: false, truncated: false } })
    const scope = effectScope(), subject = shallowRef('1'), kind = shallowRef<NetworkKind>('CHARACTERS')
    const state = scope.run(() => useOnlineNetwork(subject, kind))!
    try {
      await settle()
      expect(vi.mocked(get)).toHaveBeenCalledTimes(5)
      expect(state.nodes.value).toHaveLength(12); expect(state.failed.value).toBe(9)
      expect(state.relationError.value).toContain('暂停'); expect(state.loading.value).toBe(false)
      await state.retryRelations(); expect(state.failed.value).toBe(0)
      expect(state.checked.value).toBe(12)
    } finally { scope.stop() }
  })
  it('aborts the previous work and ignores its late roster without fetching its relationships', async () => {
    let complete!: (value: OnlineNetwork) => void
    vi.mocked(get).mockReturnValueOnce(new Promise(resolve => { complete = resolve })).mockResolvedValueOnce(graph(2)).mockResolvedValueOnce({ nodes: [], edges: [], meta: { stale: false, truncated: false } })
    const scope = effectScope(), subject = shallowRef('1'), kind = shallowRef<NetworkKind>('CHARACTERS')
    const state = scope.run(() => useOnlineNetwork(subject, kind))!
    try {
      const previousSignal = vi.mocked(get).mock.calls[0]![1]
      subject.value = '2'; await settle(); complete(graph(1)); await settle()
      expect(previousSignal?.aborted).toBe(true); expect(state.nodes.value.map(node => node.id)).toEqual(['character:bangumi:2'])
      expect(vi.mocked(get).mock.calls.some(([path]) => path === '/explore/characters/1/relations')).toBe(false)
      expect(state.loading.value).toBe(false)
    } finally { scope.stop() }
  })
  it('marks a failed source as incomplete and retries only its failed relationships', async () => {
    vi.mocked(get).mockResolvedValueOnce(graph(1)).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ nodes: [], edges: [], meta: { stale: false, truncated: false } })
    const scope = effectScope(), subject = shallowRef('1'), kind = shallowRef<NetworkKind>('CHARACTERS')
    const state = scope.run(() => useOnlineNetwork(subject, kind))!
    try {
      await settle(); expect(state.failed.value).toBe(1); expect(state.checked.value).toBe(1)
      await state.retryRelations(); expect(state.failed.value).toBe(0); expect(state.checked.value).toBe(1)
      expect(vi.mocked(get).mock.calls.filter(([path]) => path.includes('/network?'))).toHaveLength(1)
    } finally { scope.stop() }
  })
  it('keeps at most four relation reads active and ignores late relation failures after switching kind', async () => {
    const release: (() => void)[] = [], signals: AbortSignal[] = []
    let active = 0, peak = 0
    vi.mocked(get).mockImplementation(async (path, signal) => {
      if (path.includes('kind=STAFF')) return { ...graph(2, 'STAFF'), nodes: [] } as never
      if (path.includes('/network?')) return { ...graph(1), nodes: Array.from({ length: 12 }, (_, i) => ({ ...graph(1).nodes[0], id: `character:bangumi:${i + 1}` })) } as never
      signals.push(signal!); peak = Math.max(peak, ++active)
      return await new Promise((_resolve, reject) => { release.push(() => { active--; reject(new Error('late upstream timeout')) }) })
    })
    const scope = effectScope(), subject = shallowRef('1'), kind = shallowRef<NetworkKind>('CHARACTERS')
    const state = scope.run(() => useOnlineNetwork(subject, kind))!
    try {
      await settle(); expect(peak).toBe(4)
      kind.value = 'STAFF'; await settle(); release.forEach(done => done()); await settle()
      expect(signals.every(signal => signal.aborted)).toBe(true)
      expect(state.failed.value).toBe(0); expect(state.nodes.value).toEqual([])
      expect(state.loading.value).toBe(false); expect(signals).toHaveLength(4)
    } finally { scope.stop() }
  })
  it('retains loaded pages on a later-page error and shows cache provenance', async () => {
    vi.mocked(get).mockResolvedValueOnce({ ...graph(1, 'STAFF'), total: 100, meta: { fetchedAt: '2026-10-20T01:00:00Z', cached: true, stale: true, truncated: false, notice: '' } }).mockRejectedValueOnce(new Error('rate limited'))
    const scope = effectScope(), subject = shallowRef('1'), kind = shallowRef<NetworkKind>('STAFF')
    const state = scope.run(() => useOnlineNetwork(subject, kind))!
    try {
      await settle(); state.loadMore(); state.loadMore(); await settle()
      expect(state.nodes.value).toHaveLength(1); expect(state.page.value).toBe(1)
      expect(state.error.value).toBe('rate limited'); expect(state.cached.value).toBe(true)
      expect(state.stale.value).toBe(true); expect(state.fetchedAt.value).toBe('2026-10-20T01:00:00Z')
      expect(vi.mocked(get)).toHaveBeenCalledTimes(2)
    } finally { scope.stop() }
  })
})
