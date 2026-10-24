import { beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, shallowRef } from 'vue'
import { get } from '../api/client'
import { useExploreRequest } from './useExploreRequest'
vi.mock('../api/client', () => ({ get: vi.fn() }))
beforeEach(() => { vi.mocked(get).mockReset() })
describe('explore request lifecycle', () => {
  it('retains explicitly labelled current-subject data on source failure but clears it for another subject', async () => {
    vi.mocked(get).mockResolvedValueOnce({ name: 'local', source: 'LOCAL' }).mockRejectedValueOnce(new Error('online offline')).mockRejectedValueOnce(new Error('other subject offline'))
    const scope = effectScope(), url = shallowRef('/1?source=LOCAL'), subject = shallowRef('1')
    const state = scope.run(() => useExploreRequest<{name:string;source:string}>(url,subject))!
    try {
      await nextTick(); await nextTick()
      url.value='/1?source=ONLINE'; await nextTick(); await nextTick()
      expect(state.data.value).toEqual({name:'local',source:'LOCAL'}); expect(state.error.value).toBe('online offline')
      subject.value='2';url.value='/2'; await nextTick();await nextTick()
      expect(state.data.value).toBeUndefined()
    } finally { scope.stop() }
  })
  it('aborts an old query and cannot let its late response overwrite a new query', async () => {
    let finish!: (value: unknown) => void
    vi.mocked(get).mockReturnValueOnce(new Promise(resolve => { finish = resolve })).mockResolvedValueOnce({ name: 'new' })
    const scope = effectScope(), url = shallowRef('/old')
    const state = scope.run(() => useExploreRequest<{ name: string }>(url))!
    try {
      url.value = '/new'; await nextTick()
      expect(vi.mocked(get).mock.calls[0]![1]?.aborted).toBe(true)
      finish({ name: 'old' }); await nextTick()
      expect(state.data.value?.name).toBe('new'); expect(state.error.value).toBe('')
    } finally { scope.stop() }
  })
  it('retries the same URL and clears a failed result instead of showing stale records', async () => {
    vi.mocked(get).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ name: 'retry' })
    const scope = effectScope(), url = shallowRef('/same')
    const state = scope.run(() => useExploreRequest<{ name: string }>(url))!
    try {
      await nextTick(); expect(state.error.value).toBe('offline'); expect(state.data.value).toBeUndefined()
      state.retry(); await nextTick(); await nextTick()
      expect(state.data.value?.name).toBe('retry'); expect(state.error.value).toBe('')
    } finally { scope.stop() }
  })
})
