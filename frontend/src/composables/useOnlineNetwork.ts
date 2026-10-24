import { shallowRef, watch, type Ref } from 'vue'
import { get } from '../api/client'
import type { CharacterEdge } from '../types/characterGraph'
import type { NetworkKind, OnlineNetwork, OnlineNode, OnlineRelations } from '../types/onlineNetwork'
import { mergeOnlineEdges } from '../utils/onlineNetwork'

/** Owns one subject/kind lifetime, including paging and bounded progressive reads. */
export function useOnlineNetwork(subjectId: Readonly<Ref<string>>, kind: Readonly<Ref<NetworkKind>>, enabled: Readonly<Ref<boolean>> = shallowRef(true)) {
  const nodes = shallowRef<OnlineNode[]>([]), edges = shallowRef<CharacterEdge[]>([])
  const total = shallowRef(0), page = shallowRef(0), loading = shallowRef(false), error = shallowRef('')
  const checked = shallowRef(0), failed = shallowRef(0), limited = shallowRef(0), stale = shallowRef(false), truncated = shallowRef(false)
  const attempt = shallowRef(0), nameNotice=shallowRef('')
  const cached = shallowRef(false), fetchedAt = shallowRef(''), relationError = shallowRef('')
  let controller: AbortController | undefined, generation = 0
  let failedIds: string[] = [], loadedIds = new Set<string>()
  const valid = (version: number) => version === generation && !controller?.signal.aborted
  function recordMeta(meta: OnlineNetwork['meta']) {
    cached.value ||= meta.cached; stale.value ||= meta.stale
    if (meta.fetchedAt && (!fetchedAt.value || meta.fetchedAt < fetchedAt.value)) fetchedAt.value = meta.fetchedAt
  }

  async function loadRelations(ids: string[], version: number, signal: AbortSignal) {
    for (let start = 0; start < ids.length && valid(version); start += 4) {
      const batch = ids.slice(start, start + 4)
      const results = await Promise.allSettled(batch.map(id => get<OnlineRelations>(`/explore/characters/${id.split(':').at(-1)}/relations`, signal)))
      if (!valid(version)) return
      let next = edges.value
      results.forEach((result, index) => {
        if (result.status === 'fulfilled') {
          next = mergeOnlineEdges(next, result.value.edges)
          if (result.value.meta.truncated) limited.value++
          recordMeta(result.value.meta); if(result.value.nameNotice)nameNotice.value=result.value.nameNotice
        } else { failedIds.push(batch[index]!); failed.value++; relationError.value = result.reason instanceof Error ? result.reason.message : '来源暂不可用' }
        checked.value++
      })
      // Relations to characters outside this work are kept off-screen by the
      // projection. They can become visible when a later roster page loads.
      edges.value = next.slice(0, 5000)
      if (next.length > 5000) truncated.value = true
      if (results.some(result => result.status === 'rejected' && result.reason?.status === 429)) {
        const postponed = ids.slice(start + batch.length)
        failedIds.push(...postponed); failed.value += postponed.length; checked.value += postponed.length
        relationError.value = '来源请求频率受限，已暂停后续关系读取，请稍后重试。'
        return
      }
    }
  }
  async function loadPage(nextPage: number, version = generation) {
    if (!controller || !valid(version)) return
    const signal = controller.signal, currentKind = kind.value
    loading.value = true; error.value = ''
    try {
      const response = await get<OnlineNetwork>(`/explore/anime/${encodeURIComponent(subjectId.value)}/network?kind=${currentKind}&page=${nextPage}`, signal)
      if (!valid(version)) return
      const merged = new Map(nodes.value.map(node => [node.id, node]))
      response.nodes.forEach(node => merged.set(node.id, node)); nodes.value = [...merged.values()]
      edges.value = mergeOnlineEdges(edges.value, response.edges)
      total.value = response.total; page.value = nextPage
      if(response.nameNotice)nameNotice.value=response.nameNotice; recordMeta(response.meta); truncated.value ||= response.meta.truncated
      if (currentKind === 'CHARACTERS') {
        const ids = response.nodes.map(node => node.id).filter(id => !loadedIds.has(id))
        ids.forEach(id => loadedIds.add(id)); await loadRelations(ids, version, signal)
      }
    } catch (cause) { if (valid(version)) error.value = cause instanceof Error ? cause.message : '在线图谱读取失败' }
    finally { if (valid(version)) loading.value = false }
  }
  async function retryRelations() {
    if (loading.value || !controller) return
    const ids = failedIds, version = generation, signal = controller.signal
    failedIds = []; checked.value -= ids.length; failed.value = 0; relationError.value = ''; loading.value = true
    try { await loadRelations(ids, version, signal) } finally { if (valid(version)) loading.value = false }
  }
  watch([subjectId, kind, attempt, enabled], (_value, _old, onCleanup) => {
    controller = new AbortController(); const current = controller, version = ++generation
    onCleanup(() => { current.abort(); if (version === generation) generation++ })
    nameNotice.value=''; nodes.value = []; edges.value = []; total.value = 0; page.value = 0; checked.value = 0; failed.value = 0; limited.value = 0
    stale.value = false; truncated.value = false; failedIds = []; loadedIds = new Set(); error.value = ''
    cached.value = false; fetchedAt.value = ''; relationError.value = ''
    loading.value = false
    if (enabled.value && subjectId.value) void loadPage(1, version)
  }, { immediate: true })
  return { nameNotice, nodes, edges, total, page, loading, error, checked, failed, limited, stale, truncated, cached, fetchedAt, relationError,
    refresh: () => { attempt.value++ }, loadMore: () => { if (!loading.value && page.value < 4) void loadPage(page.value + 1) }, retryRelations }
}
