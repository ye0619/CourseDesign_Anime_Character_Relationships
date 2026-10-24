import type { CreditEntity, CreditPage, CastCredit, CreditPosition } from '../types/credits'
import type { CharacterNode } from '../types/characterGraph'

export function creditNode(entity: CreditEntity): CharacterNode {
  return { ...entity, role: '', group: '', description: entity.description||'', characterType: entity.providerType }
}
export function mergeCredits(pages: CreditPage[]) {
  const people = new Map<string, CreditEntity>(), characters = new Map<string, CreditEntity>()
  const cast = new Map<string, CastCredit>(), positions = new Map<string, CreditPosition[]>()
  for (const page of pages) {
    page.people.forEach(person => people.set(person.id, person))
    page.characters.forEach(character => characters.set(character.id, character))
    page.cast.forEach(credit => cast.set(credit.id, credit))
    page.staff.forEach(credit => {
      const merged = new Map((positions.get(credit.personUid) || []).map(position => [JSON.stringify(position), position]))
      credit.positions.forEach(position => merged.set(JSON.stringify(position), position))
      positions.set(credit.personUid, [...merged.values()])
    })
  }
  const grouped = new Map<string, CastCredit[]>()
  cast.forEach(credit => grouped.set(credit.personUid, [...(grouped.get(credit.personUid) || []), credit]))
  const last = pages.at(-1), first = pages[0]
  const consistent = !!first && pages.every((page, index) => page.coverage.page === index + 1 && page.coverage.total === first.coverage.total && page.source.adapterVersion === first.source.adapterVersion && page.source.namePolicyVersion === first.source.namePolicyVersion)
  const complete = consistent && !!last?.coverage.exhausted && pages.every(page => !page.coverage.omittedRows && !page.coverage.omittedCredits && !page.coverage.unknownCastRows && !page.coverage.capped && !page.source.stale)
  return {
    people: [...people.values()].map(person => ({ person, node: creditNode(person), cast: grouped.get(person.id) || [], positions: positions.get(person.id) || [] })), characters,
    voicePeople: [...grouped.entries()].filter(([id, credits]) => people.get(id)?.providerType === 1 && credits.some(credit => credit.performanceType === 'VOICE')).length,
    loadedRows: pages.reduce((sum, page) => sum + page.coverage.returnedRows, 0), total: last?.coverage.total ?? 0,
    complete, consistent, stale: pages.some(page => page.source.stale), nextPage: last?.coverage.nextPage ?? null,
  }
}
export function creditLanguage(value: string | null) {
  return value ? ({ zh: '中文', ja: '日语', en: '英语', ko: '韩语' }[value] || value) : '来源未提供'
}
