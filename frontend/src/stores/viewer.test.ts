import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useViewerStore } from './viewer'
import { get, mutate } from '../api/client'
import type { ViewerState } from '../types/viewer'
vi.mock('../api/client', () => ({ get: vi.fn(), mutate: vi.fn() }))
const empty: ViewerState = { bound: false, profile: null, mode: null, verified: false, requiresReauthorization: false, syncStatus: 'IDLE', processed: 0, remoteTotal: 0, truncated: false, error: null, lastSyncedAt: null }
const bound: ViewerState = { ...empty, bound: true, profile: { id: 1, username: 'sai', nickname: 'Sai', avatar: null }, mode: 'PUBLIC', syncStatus: 'SUCCEEDED' }
beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks(); vi.useFakeTimers() })
afterEach(() => { vi.clearAllTimers(); vi.useRealTimers() })
describe('viewer request races', () => {
  it('does not restore a removed account when an older sync request finishes', async () => {
    let resolve!: (value: ViewerState) => void
    vi.mocked(mutate).mockImplementationOnce(() => new Promise(r => { resolve = r })).mockResolvedValueOnce(null)
    vi.mocked(get).mockResolvedValue(empty)
    const store = useViewerStore(), pending = store.sync()
    await store.unbind(); resolve(bound); await pending
    expect(store.state?.bound).toBe(false)
  })
  it('ignores an older status response that arrives after a newer response', async () => {
    let resolve!: (value: ViewerState) => void
    vi.mocked(get).mockImplementationOnce(() => new Promise(r => { resolve = r })).mockResolvedValueOnce(empty)
    const store = useViewerStore(), first = store.refresh()
    await store.refresh(); resolve(bound); await first
    expect(store.state?.bound).toBe(false)
  })
  it('does not report an obsolete request failure after a newer successful refresh', async () => {
    let reject!: (reason: unknown) => void
    vi.mocked(get).mockImplementationOnce(() => new Promise((_, r) => { reject = r })).mockResolvedValueOnce(empty)
    const store = useViewerStore(), first = store.refresh()
    await store.refresh(); reject(new Error('old network failure'))
    await expect(first).resolves.toBeUndefined()
    expect(store.state?.bound).toBe(false)
  })
})
