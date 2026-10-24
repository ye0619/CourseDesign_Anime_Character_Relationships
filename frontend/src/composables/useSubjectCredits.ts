import { computed, shallowRef, watch, onScopeDispose, type Ref } from 'vue'
import { get } from '../api/client'
import type { CreditKind, CreditPage } from '../types/credits'
import { mergeCredits } from '../utils/subjectCredits'
import { localCreditPage } from '../utils/localCredits'
import type { Page, Related } from '../types/catalog'

interface Section { pages: CreditPage[]; loading: boolean; error: string; failedPage: number | null }
const empty = (): Section => ({ pages: [], loading: false, error: '', failedPage: null })
/** Each category owns its cursor and failure. Only explicit user actions read subsequent pages. */
export function useSubjectCredits(subjectId: Ref<string>, kind: Ref<CreditKind>, source: Readonly<Ref<'LOCAL' | 'ONLINE'>> = shallowRef('ONLINE'), uid: Readonly<Ref<string>> = computed(() => `anime:bangumi:${subjectId.value}`)) {
  const sections = shallowRef<Record<CreditKind, Section>>({ CAST: empty(), STAFF: empty() })
  const controllers = new Map<CreditKind, AbortController>()
  let generation = 0
  const section = computed(() => sections.value[kind.value])
  const merged = computed(() => mergeCredits(section.value.pages))
  function update(key: CreditKind, value: Partial<Section>) { sections.value = { ...sections.value, [key]: { ...sections.value[key], ...value } } }
  async function load(key: CreditKind, page: number, reset = false) {
    if (sections.value[key].loading) return
    const version = generation, id = subjectId.value, controller = new AbortController()
    controllers.set(key, controller); update(key, { loading: true, error: '', failedPage: null })
    try {
      const localUid = uid.value
      const result = source.value === 'LOCAL' && !/^anime:bangumi:[1-9]\d*$/.test(localUid)
        ? localCreditPage(await get<Page<Related>>(`/anime/${encodeURIComponent(localUid)}/${key === 'STAFF' ? 'staff' : 'cast'}?page=${page}&pageSize=60`, controller.signal), localUid, key)
        : await get<CreditPage>(`/explore/anime/${encodeURIComponent(id)}/credits?kind=${key}&page=${page}`, controller.signal)
      if (version !== generation || controller.signal.aborted) return
      if (result.subjectUid !== (source.value === 'LOCAL' ? localUid : `anime:bangumi:${id}`) || result.kind !== key || result.coverage.page !== page) throw new Error('来源分页与请求不一致，请重新读取')
      const previous = reset ? [] : sections.value[key].pages
      if (previous.length && (previous[0]!.coverage.total !== result.coverage.total || previous[0]!.source.adapterVersion !== result.source.adapterVersion || previous[0]!.source.namePolicyVersion !== result.source.namePolicyVersion)) throw new Error('读取期间来源总数或规则已变化，请从第一页重新读取')
      update(key, { pages: [...previous.filter(item => item.coverage.page < page), result] })
    } catch (cause) {
      if (version === generation && !controller.signal.aborted) update(key, { error: cause instanceof Error ? cause.message : '名册读取失败', failedPage: page })
    } finally {
      if (version === generation) { update(key, { loading: false }); controllers.delete(key) }
    }
  }
  watch([subjectId, source, uid], () => {
    generation++; controllers.forEach(controller => controller.abort()); controllers.clear()
    sections.value = { CAST: empty(), STAFF: empty() }
    void load(kind.value, 1)
  }, { immediate: true })
  watch(kind, key => { if (!sections.value[key].pages.length && !sections.value[key].loading && !sections.value[key].error) void load(key, 1) })
  onScopeDispose(() => { generation++; controllers.forEach(controller => controller.abort()); controllers.clear() })
  return { section, merged, loadMore: () => { if (merged.value.nextPage) void load(kind.value, merged.value.nextPage) },
    retry: () => { void load(kind.value, section.value.failedPage || 1) }, refresh: () => { void load(kind.value, 1, true) } }
}
