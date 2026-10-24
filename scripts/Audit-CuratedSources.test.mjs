import { test } from 'node:test'
import assert from 'node:assert/strict'
import { auditWork } from './Audit-CuratedSources.mjs'
function fixture() {
  const work = { subjectId: 1424, uid: 'anime:bangumi:1424' }
  const input = (suffix, value) => ({ path: 'source' + suffix, bytes: Buffer.from(JSON.stringify(value)), value })
  const raw = { subject: input('', { id: 1424, type: 2 }),
    staff: input('/persons', [{ id: 1, type: 1, relation: '监督' }, { id: 99, type: 2, relation: '制作' }]),
    characters: input('/characters', [{ id: 9, type: 1, relation: '主角', actors: [{ id: 2, type: 1 }, { id: 2, type: 1 }] }]) }
  const normalized = { entities: [{ type: 'Anime', sourceId: '1424' }, { type: 'Person', sourceId: '1' },
    { type: 'Person', sourceId: '2' }, { type: 'Character', sourceId: '9' }],
  relations: [{ type: 'PARTICIPATED_IN', from: { type: 'Person', sourceId: '1' }, to: { type: 'Anime', sourceId: '1424' }, properties: { roleCode: 'DIRECTOR', roleName: '监督' } },
    { type: 'APPEARS_IN', from: { type: 'Character', sourceId: '9' }, to: { type: 'Anime', sourceId: '1424' }, properties: { characterRole: '主角' } },
    { type: 'VOICED', from: { type: 'Person', sourceId: '2' }, to: { type: 'Character', sourceId: '9' }, properties: { animeUid: 'anime:bangumi:1424', scopeKnown: true, sourceName: 'Bangumi作品内角色配音' } }] }
  const provenance = ['', '/persons', '/characters'].map(suffix => ({ url: 'https://api.bgm.tv/v0/subjects/1424' + suffix, status: 200, fetchedAt: '2026-10-23T05:00:00Z' }))
  return { work, raw, normalized, provenance }
}
const audit = f => auditWork(f.work, f.raw, f.normalized, f.provenance)
test('response accounting preserves exclusions and collapses repeated identities without inventing voice language', () => {
  const result = audit(fixture()), staff = result.groups[0], cast = result.groups[2]
  assert.equal(staff.sourceRows, 2); assert.equal(staff.excludedRows, 1); assert.equal(staff.uniqueRecords, 1)
  assert.equal(cast.acceptedRows, 2); assert.equal(cast.uniqueRecords, 1); assert.equal(cast.duplicateRows, 1)
  assert.equal(cast.collectionStatus, 'NOT_VERIFIED'); assert.equal(cast.relations[0].properties.language, undefined)
  assert.match(staff.relations[0].uid, /^relation:[a-f0-9]{64}$/)
})
test('missing normalized identities, membership, role facts or explicit voice scope fail the audit', () => {
  for (const mutate of [
    f => f.normalized.entities.pop(),
    f => f.normalized.relations.pop(),
    f => { f.normalized.relations[0].properties.roleName = '同名但不同职务' },
    f => { f.normalized.relations[2].properties.scopeKnown = false },
  ]) { const f = fixture(); mutate(f); assert.throws(() => audit(f)) }
})
test('a cached response retains cache time and unknown acquisition time; invalid provenance is rejected', () => {
  const f = fixture(); f.provenance[1] = { ...f.provenance[1], fetchedAt: undefined, cacheHit: true, cacheWrittenAt: '2026-10-17T05:00:00Z' }
  const group = audit(f).groups[0]
  assert.equal(group.fetchedAt, null); assert.equal(group.cacheWrittenAt, '2026-10-17T05:00:00Z')
  f.provenance[1].cacheWrittenAt = 'unknown'; assert.throws(() => audit(f))
  const failed = fixture(); failed.provenance[2].status = 429; assert.throws(() => audit(failed))
  const duplicate = fixture(); duplicate.provenance.push(duplicate.provenance[0]); assert.throws(() => audit(duplicate))
})
test('subject identity and same-name people are never joined by display names', () => {
  const f = fixture(); f.raw.subject.value.id = 3774; assert.throws(() => audit(f))
  const invalid = fixture(); invalid.work.uid = 'anime:bangumi:3774'; assert.throws(() => audit(invalid))
  const sameName = fixture(); sameName.raw.staff.value[0].name = '同名'; sameName.raw.characters.value[0].actors[0].name = '同名'
  const result = audit(sameName)
  assert.notEqual(result.groups[0].relations[0].sourceUid, result.groups[2].relations[0].sourceUid)
})
