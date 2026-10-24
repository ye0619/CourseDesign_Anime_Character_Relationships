import { defineStore } from 'pinia'
import { ref } from 'vue'
import { get, mutate } from '../api/client'
import { clearActivityHistory } from '../composables/useActivityHistory'
import type { ViewerState } from '../types/viewer'

export const useViewerStore = defineStore('viewer', () => {
  const initialized=ref(false)
  const state = ref<ViewerState | null>(null), error = ref(''), loading = ref(false), busy = ref(false)
  let timer: ReturnType<typeof setTimeout> | undefined, revision = 0, requestVersion = 0
  async function refresh() {
    const version = revision, requestId = ++requestVersion
    let response: ViewerState
    try { response = await get<ViewerState>('/viewer') } catch(e) { if (version !== revision || requestId !== requestVersion) return; throw e }
    if (version !== revision || requestId !== requestVersion) return
    initialized.value = true; state.value = response
    clearTimeout(timer)
    if (response.syncStatus === 'RUNNING') timer = setTimeout(() => refresh().catch(e => { error.value = e instanceof Error ? e.message : '同步状态读取失败' }), 1600)
  }
  async function load() { loading.value = true; error.value = ''; try { await refresh() } catch(e) { error.value = e instanceof Error ? e.message : '读取账号信息失败' } finally { loading.value = false } }
  async function sync() { error.value = ''; const version = revision; const response = await mutate<ViewerState>('/viewer/sync', 'POST'); if (version !== revision) return; initialized.value = true; state.value = response; await refresh() }
  async function bind(username: string, token: string) { busy.value = true; error.value = ''; try { const previousProfile=state.value?.profile?.id; revision++; clearTimeout(timer); state.value = await mutate<ViewerState>('/viewer/bind', 'POST', token ? { token } : { username }); if(previousProfile && previousProfile !== state.value.profile?.id) clearActivityHistory(String(previousProfile)); if (!state.value.lastSyncedAt || Date.now() - Date.parse(state.value.lastSyncedAt) > 60_000) await sync() } catch(e) { await refresh().catch(() => {}); throw e } finally { busy.value = false } }
  async function unbind() { busy.value = true; try { const previousProfile=state.value?.profile?.id; await mutate('/viewer/binding', 'DELETE'); if(previousProfile)clearActivityHistory(String(previousProfile)); revision++; clearTimeout(timer); state.value = null; await refresh() } finally { busy.value = false } }
  return { initialized, state, error, loading, busy, load, refresh, sync, bind, unbind }
})

