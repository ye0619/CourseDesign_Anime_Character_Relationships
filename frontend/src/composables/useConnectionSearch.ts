import { computed, onScopeDispose, shallowRef } from 'vue'
import { get, mutate } from '../api/client'
import type { ConnectionQuery, ConnectionResult } from '../types/connections'
export function useConnectionSearch() {
  const result = shallowRef<ConnectionResult>(), pending = shallowRef(false), error = shallowRef('')
  let generation = 0, controller: AbortController | undefined, taskId: string | undefined, timer: ReturnType<typeof setTimeout> | undefined
  const running = computed(() => pending.value || result.value?.status === 'RUNNING')
  async function discard() {
    generation++; controller?.abort(); clearTimeout(timer); pending.value = false
    const id = taskId; taskId = undefined
    if (id) await mutate(`/connections/searches/${id}`, 'DELETE').catch(() => undefined)
  }
  async function cancel() {
    const previous = result.value, current = generation + 1
    await discard()
    if (previous && current === generation) result.value = { ...previous, status: 'CANCELLED', stopReason: 'CANCELLED', searchExhausted: false, exactPathCount: null }
  }
  function reset() { void discard(); result.value = undefined; error.value = '' }
  async function start(query: ConnectionQuery) {
    const current = generation + 1; await discard(); if (current !== generation) return
    pending.value = true; error.value = ''; result.value = undefined
    controller = new AbortController(); const signal = controller.signal
    const poll = async (id: string) => {
      try {
        const data = await get<ConnectionResult>(`/connections/searches/${id}`, signal)
        if (current !== generation) return
        result.value = data
        if (data.status === 'RUNNING') timer = setTimeout(() => void poll(id), 400)
        else taskId = undefined
      } catch (cause) { if (current === generation && !signal.aborted) { error.value = cause instanceof Error ? cause.message : '读取联系失败'; pending.value = false; if(result.value)result.value={...result.value,status:'FAILED'} } }
    }
    try {
      const data = await mutate<ConnectionResult>('/connections/searches', 'POST', query)
      if (current !== generation) { void mutate(`/connections/searches/${data.queryId}`, 'DELETE').catch(() => undefined); return }
      taskId = data.queryId; result.value = data
      if (data.status === 'RUNNING') timer = setTimeout(() => void poll(data.queryId), 200)
    } catch (cause) { if (current === generation) error.value = cause instanceof Error ? cause.message : '查询失败' }
    finally { if (current === generation) pending.value = false }
  }
  onScopeDispose(() => { void discard() })
  return { result, running, error, start, cancel, reset }
}
