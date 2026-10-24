import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, shallowRef } from 'vue'
import { get } from '../api/client'
import { useAnimeSuggestions } from './useAnimeSuggestions'
import type { ExploreResult } from '../types/explore'
vi.mock('../api/client', () => ({ get: vi.fn() }))
const response = (id: string): ExploreResult => ({ online: { items: [{ uid: id, sourceId: id, source: 'bangumi', name: id, type: 'ANIME', active: true }], page: 1, pageSize: 20, total: 1 }, local: { items: [], page: 1, pageSize: 20, total: 0 }, meta: { onlineStatus: 'OK', localStatus: 'SKIPPED', notice: '', fetchedAt: '', cached: false, stale: false } })
beforeEach(() => { vi.useFakeTimers(); vi.mocked(get).mockReset() })
afterEach(() => vi.useRealTimers())
describe('anime suggestions request lifecycle', () => {
  it('debounces, pauses composition and cancels obsolete responses and disposal', async () => {
    const scope = effectScope(), query = shallowRef(''), composing = shallowRef(false)
    let finish!: (value: ExploreResult) => void
    vi.mocked(get).mockReturnValueOnce(new Promise(resolve => { finish = resolve })).mockResolvedValue(response('second'))
    const state = scope.run(() => useAnimeSuggestions(() => query.value, () => composing.value, () => ({mode:'ONLINE'})))!
    query.value = '孤'; await vi.advanceTimersByTimeAsync(350); expect(get).not.toHaveBeenCalled()
    composing.value = true; query.value = '孤独'; await vi.advanceTimersByTimeAsync(350); expect(get).not.toHaveBeenCalled()
    composing.value = false; await vi.advanceTimersByTimeAsync(300); expect(get).toHaveBeenCalledTimes(1)
    const signal = vi.mocked(get).mock.calls[0]![1]
    query.value = '芙莉莲'; expect(signal?.aborted).toBe(true)
    await vi.advanceTimersByTimeAsync(300); finish(response('late')); await vi.advanceTimersByTimeAsync(0)
    expect(state.items.value[0]?.uid).toBe('second')
    query.value = ''; expect(state.items.value).toEqual([])
    scope.stop(); await vi.advanceTimersByTimeAsync(500); expect(get).toHaveBeenCalledTimes(2)
  })
})
