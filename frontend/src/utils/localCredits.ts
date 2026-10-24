import type { Entity, Page, Related } from '../types/catalog'
import { title } from '../types/catalog'
import type { CreditEntity, CreditKind, CreditPage } from '../types/credits'

const text = (value: unknown): string | null => typeof value === 'string' && value.trim() ? value : null
function creditEntity(entity: Entity): CreditEntity {
  const type = (entity as Entity & { providerType?: number }).providerType
  return { id: entity.uid, label: title(entity), originalName: entity.name, imageUrl: entity.imageUrl || '', category: entity.type === 'CHARACTER' ? 'CHARACTER' : 'PERSON',
    providerType: typeof type === 'number' ? type : null, aliases: entity.aliases || [], nameAssertions: entity.nameAssertions || [], namePolicyVersion: 'local-records/v1',
    sourceUrl: entity.source === 'bangumi' ? `https://bgm.tv/${entity.type === 'CHARACTER' ? 'character' : 'person'}/${entity.sourceId}` : '' }
}
/** Adapt saved records without inventing person types, languages, episodes or source classifications. */
export function localCreditPage(data: Page<Related>, uid: string, kind: CreditKind): CreditPage {
  const people = new Map<string,CreditEntity>(), characters = new Map<string,CreditEntity>()
  const staff: CreditPage['staff'] = [], cast: CreditPage['cast'] = []
  let omittedCredits = 0
  for (const [index,row] of data.items.entries()) {
    people.set(row.entity.uid,creditEntity(row.entity))
    const relation = row.relation || {}
    if (kind === 'STAFF') staff.push({ personUid: row.entity.uid, positions: [{ code: text(relation.roleCode) || '', label: text(relation.roleName) || text(relation.roleCode) || '职务未知', episodeScope: text(relation.episodeScope), description: text(relation.description) }] })
    else if (row.character) {
      characters.set(row.character.uid,creditEntity(row.character))
      cast.push({ id: text(relation.uid) || `${uid}:${row.entity.uid}:${row.character.uid}:${data.page}:${index}`, subjectUid: uid, personUid: row.entity.uid, characterUid: row.character.uid,
        sourceRelation: typeof relation.sourceRelation === 'number' ? relation.sourceRelation : null, sourceRelationLabel: text(relation.sourceRelationLabel) || '配音记录', performanceType: 'VOICE',
        language: text(relation.language), languageSource: text(relation.language) ? 'relation' : null, version: text(relation.versionLabel), episodeScope: text(relation.episodeScope), description: text(relation.description), sourceField: 'saved-VOICED' })
    } else omittedCredits++
  }
  const exhausted = data.page * data.pageSize >= data.total
  const timestamps = data.items.map(row => text(row.relation?.updatedAt)).filter((value): value is string => !!value).sort()
  return { subjectUid: uid, kind, people: [...people.values()], characters: [...characters.values()], staff, cast, nameNotice: data.nameNotice,
    coverage: { page: data.page, pageSize: data.pageSize, total: data.total, returnedRows: data.items.length, parsedRows: data.items.length, omittedRows: 0, omittedCredits, unknownCastRows: 0,
      nextPage: exhausted ? null : data.page + 1, exhausted, capped: false, completeness: exhausted && !omittedCredits ? 'COMPLETE' : 'PARTIAL' },
    source: { provider: 'NEO4J', sourceUrl: /^anime:bangumi:[1-9]\d*$/.test(uid) ? `https://bgm.tv/subject/${uid.split(':').at(-1)}` : '', sourceField: kind === 'STAFF' ? 'saved-PARTICIPATED_IN' : 'saved-VOICED',
      adapterVersion: 'local-credits/v1', fetchedAt: timestamps[0] || '', cached: false, stale: false, namePolicyVersion: 'local-records/v1' }, snapshotVersion: 'local-records/v1' }
}
