import { onScopeDispose, shallowRef, watch } from 'vue'
import { get } from '../api/client'
import type { Entity } from '../types/catalog'
import type { ExploreResult } from '../types/explore'
export interface SuggestionFilter { mode?: string; year?: string; format?: string; source?: string }
const cache = new Map<string, { at: number; items: Entity[]; notice: string }>()
export function useAnimeSuggestions(query: () => string, composing: () => boolean, filter: () => SuggestionFilter) {
  const items = shallowRef<Entity[]>([]), pending = shallowRef(false), error = shallowRef(''), notice = shallowRef(''), retryVersion = shallowRef(0)
  let controller: AbortController | undefined
  watch(() => [query().trim(), composing(), JSON.stringify(filter()), retryVersion.value] as const, ([text, inComposition], _old, onCleanup) => {
    controller?.abort(); items.value = []; error.value = ''; notice.value = ''; pending.value = false
    let current = true
    const active = new AbortController(); controller = active
    const timer = setTimeout(async () => {
      if (inComposition || Array.from(text).length < 2 || !current) return
      const params = new URLSearchParams({ keyword: text, mode: filter().mode || 'ONLINE' })
      for (const [key, value] of Object.entries(filter())) if (value) params.set(key, value)
      const key = params.toString(), cached = cache.get(key)
      if (cached && Date.now()-cached.at < 60_000) { items.value = cached.items; notice.value = cached.notice; return }
      pending.value = true
      try {
        const response = await get<ExploreResult>(`/explore/anime?${params}`, active.signal)
        if (!current) return
        const result = [...new Map([...response.online.items, ...response.local.items].map(entity => [entity.uid, entity])).values()].slice(0,8)
        if (!result.length && (response.meta.onlineStatus === 'ERROR' || response.meta.localStatus === 'ERROR')) throw new Error(response.meta.notice || '暂时无法获取候选作品')
        items.value = result; notice.value = response.meta.notice
        cache.set(key, { at: Date.now(), items: result, notice: response.meta.notice })
        while (cache.size > 64) cache.delete(cache.keys().next().value!)
      } catch (cause) { if (current && !active.signal.aborted) error.value = cause instanceof Error ? cause.message : '暂时无法获取候选作品' }
      finally { if (current) pending.value = false }
    }, 300)
    onCleanup(() => { current = false; active.abort(); clearTimeout(timer) })
  }, { immediate: true, flush: 'sync' })
  onScopeDispose(() => controller?.abort())
  return { items, pending, error, notice, retry: () => { retryVersion.value++ } }
}
