import { beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, shallowRef } from 'vue'
import { get } from '../api/client'
import type { CreditKind, CreditPage } from '../types/credits'
import { useSubjectCredits } from './useSubjectCredits'
import { mergeCredits } from '../utils/subjectCredits'
vi.mock('../api/client', () => ({ get: vi.fn() }))
const entity = (id: string) => ({ id, label: '同名', originalName: null, imageUrl: '', category: 'PERSON' as const, providerType: 1, aliases: [], nameAssertions: [], namePolicyVersion: 'names/1', sourceUrl: '' })
const page = (subject = '1', kind: CreditKind = 'CAST', index = 1, total = 1): CreditPage => ({
  subjectUid: `anime:bangumi:${subject}`, kind, people: [entity('person:bangumi:1')], characters: [], cast: [], staff: [],
  coverage: { page: index, pageSize: 60, total, returnedRows: Math.min(60, total - (index-1)*60), parsedRows: 1, omittedRows: 0, omittedCredits: 0, unknownCastRows: 0, nextPage: index*60 < total ? index+1 : null, exhausted: index*60 >= total, capped: false, completeness: total <= 60 ? 'COMPLETE' : 'PARTIAL' },
  source: { provider: 'BANGUMI', sourceUrl: '', sourceField: '', adapterVersion: 'adapter/1', fetchedAt: '2026-10-20T00:00:00Z', cached: false, stale: false, namePolicyVersion: 'names/1' }, snapshotVersion: 'snapshot/1',
})
const settle = async () => { for (let i=0;i<8;i++) await nextTick() }
beforeEach(() => vi.mocked(get).mockReset())
describe('public credits lifecycle and completeness', () => {
  it('uses the same credits API for saved and online works and rejects late responses', async () => {
    let complete!: (value: unknown) => void
    vi.mocked(get).mockReturnValueOnce(new Promise(resolve => { complete=resolve })).mockResolvedValueOnce(page())
    const scope=effectScope(), source=shallowRef<'LOCAL'|'ONLINE'>('LOCAL')
    const state=scope.run(()=>useSubjectCredits(shallowRef('1'),shallowRef<CreditKind>('STAFF'),source))!
    const signal=vi.mocked(get).mock.calls[0]![1]
    try {
      expect(vi.mocked(get).mock.calls[0]?.[0]).toBe('/explore/anime/1/credits?kind=STAFF&page=1')
      source.value='ONLINE'
      vi.mocked(get).mockReset().mockResolvedValueOnce(page('1','STAFF'))
      await settle()
      complete({items:[],page:1,pageSize:60,total:0});await settle()
      expect(signal?.aborted).toBe(true)
      expect(state.section.value.pages[0]?.source.provider).toBe('BANGUMI')
      expect(vi.mocked(get).mock.calls[0]?.[0]).toContain('/explore/anime/1/credits?kind=STAFF')
    } finally {scope.stop()}
  })
  it('reads one category lazily and keeps cast available after independent staff failure', async () => {
    vi.mocked(get).mockResolvedValueOnce(page()).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(page('1','STAFF'))
    const scope=effectScope(), subject=shallowRef('1'), kind=shallowRef<CreditKind>('CAST')
    const state=scope.run(() => useSubjectCredits(subject,kind))!
    try {
      await settle();expect(vi.mocked(get)).toHaveBeenCalledTimes(1)
      kind.value='STAFF';await settle();expect(state.section.value.error).toBe('offline')
      kind.value='CAST';await settle();expect(state.merged.value.people).toHaveLength(1);expect(state.section.value.error).toBe('')
      kind.value='STAFF';await settle();expect(vi.mocked(get)).toHaveBeenCalledTimes(2)
      state.retry();await settle();expect(state.merged.value.complete).toBe(true)
    } finally {scope.stop()}
  })
  it('retains previous pages on failure, deduplicates rapid reads and retries only the failed page', async () => {
    vi.mocked(get).mockResolvedValueOnce(page('1','CAST',1,61)).mockRejectedValueOnce(new Error('429')).mockResolvedValueOnce(page('1','CAST',2,61))
    const scope=effectScope(), state=scope.run(() => useSubjectCredits(shallowRef('1'),shallowRef<CreditKind>('CAST')))!
    try {
      await settle();expect(state.merged.value.complete).toBe(false)
      state.loadMore();state.loadMore();await settle();expect(state.section.value.pages).toHaveLength(1)
      expect(state.section.value.failedPage).toBe(2);state.retry();await settle()
      expect(state.merged.value.complete).toBe(true);expect(state.merged.value.people).toHaveLength(1)
      expect(vi.mocked(get).mock.calls.map(([url])=>url.split('page=')[1])).toEqual(['1','2','2'])
    } finally {scope.stop()}
  })
  it('aborts changed work and disposal and ignores late responses', async () => {
    let complete!: (value: CreditPage) => void
    vi.mocked(get).mockReturnValueOnce(new Promise(resolve => {complete=resolve})).mockResolvedValueOnce(page('2'))
    const scope=effectScope(), subject=shallowRef('1'), state=scope.run(()=>useSubjectCredits(subject,shallowRef<CreditKind>('CAST')))!
    const firstSignal=vi.mocked(get).mock.calls[0]![1]
    subject.value='2';await settle();complete(page('1'));await settle()
    expect(firstSignal?.aborted).toBe(true);expect(state.section.value.pages[0]?.subjectUid).toBe('anime:bangumi:2')
    vi.mocked(get).mockReturnValueOnce(new Promise(resolve => {complete=resolve}))
    state.refresh();const pendingSignal=vi.mocked(get).mock.calls[2]![1]
    scope.stop();expect(pendingSignal?.aborted).toBe(true)
    complete(page('3'));await settle();expect(state.section.value.pages[0]?.subjectUid).toBe('anime:bangumi:2')
  })
  it('does not join changed total into a false complete snapshot and allows refresh', async () => {
    vi.mocked(get).mockResolvedValueOnce(page('1','CAST',1,61)).mockResolvedValueOnce(page('1','CAST',2,62)).mockResolvedValueOnce(page('1','CAST',1,62))
    const scope=effectScope(), state=scope.run(()=>useSubjectCredits(shallowRef('1'),shallowRef<CreditKind>('CAST')))!
    try {
      await settle();state.loadMore();await settle();expect(state.section.value.error).toContain('已变化')
      expect(state.section.value.pages).toHaveLength(1);expect(state.merged.value.complete).toBe(false)
      state.refresh();await settle();expect(state.merged.value.total).toBe(62);expect(state.section.value.error).toBe('')
    } finally {scope.stop()}
  })
  it('never treats missing pages, unknown casts or stale data as complete', () => {
    const first=page('1','CAST',1,61), last=page('1','CAST',2,61)
    expect(mergeCredits([last]).complete).toBe(false)
    expect(mergeCredits([first,last]).complete).toBe(true)
    expect(mergeCredits([{...page(), coverage: {...page().coverage, unknownCastRows: 1}}]).complete).toBe(false)
    expect(mergeCredits([{...page(), source: {...page().source, stale: true}}]).complete).toBe(false)
  })
  it('counts stable people once, preserves multiple roles and never merges same names or counts organizations as voice people', () => {
    const fixture=page();fixture.people.push(entity('person:bangumi:2'),{...entity('person:bangumi:3'),providerType: 3})
    const cast=(id: string, personUid: string, characterUid: string) => ({ id, subjectUid: fixture.subjectUid, personUid, characterUid, sourceRelation: 0, sourceRelationLabel: 'CV 原声', performanceType: 'VOICE' as const, language: null, languageSource: null, version: null, episodeScope: null, description: null, sourceField: '' })
    fixture.cast=[cast('a','person:bangumi:1','c1'),cast('b','person:bangumi:1','c2'),cast('c','person:bangumi:2','c1'),cast('d','person:bangumi:3','c3')]
    const result=mergeCredits([fixture]);expect(result.voicePeople).toBe(2);expect(result.people).toHaveLength(3)
    expect(result.people[0]!.cast).toHaveLength(2)
  })
})
