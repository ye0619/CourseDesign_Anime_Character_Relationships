import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

export const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const uid = (type, id) => type.toLowerCase() + ':bangumi:' + id
const positiveId = id => Number.isSafeInteger(id) && id > 0 && id <= 2147483647
const roleMap = { '监督': 'DIRECTOR', '脚本': 'SCRIPT', '演出': 'EPISODE_DIRECTOR', '音乐': 'MUSIC', '人物设定': 'CHARACTER_DESIGN', '原作': 'ORIGINAL_CREATOR', '系列构成': 'SERIES_COMPOSITION' }
const roleCode = role => Object.hasOwn(roleMap, role) ? roleMap[role] : 'BGM_' + hash(role).slice(0, 16).toUpperCase()
export function relationIdentity(row) {
  const source = row.sourceUid || uid(row.from.type, row.from.sourceId)
  const target = row.targetUid || uid(row.to.type, row.to.sourceId)
  const key = ['bangumi', row.type, source, target], p = row.properties
  if (row.type === 'PARTICIPATED_IN') key.push(p.roleCode)
  if (row.type === 'VOICED') key.push(p.animeUid || '', p.language || '', p.versionLabel || '')
  return 'relation:' + hash(JSON.stringify(key))
}
function record(type, from, to, properties) {
  const row = { type, sourceUid: from, targetUid: to, properties }
  return { uid: relationIdentity(row), sourceUid: from, targetUid: to, properties }
}
function reference(row, side) { return row[side + 'Uid'] || uid(row[side].type, row[side].sourceId) }
function indexed(rows) {
  const found = new Map()
  for (const row of rows) {
    const old = found.get(row.uid)
    if (old) assert.deepEqual(old, row, 'Duplicate identity has conflicting properties')
    found.set(row.uid, row)
  }
  return found
}
function capture(path, suffix, bytes, provenance, id) {
  const url = 'https://api.bgm.tv/v0/subjects/' + id + suffix
  const rows = provenance.filter(request => request.url === url)
  assert.equal(rows.length, 1, 'Exactly one provenance entry required: ' + url)
  const source = rows[0]
  assert.equal(source.status, 200, 'Successful source response required: ' + url)
  const cached = source.cacheHit === true
  const time = cached ? source.cacheWrittenAt : source.fetchedAt
  assert.equal(typeof time, 'string', 'Source timestamp required')
  assert(Number.isFinite(Date.parse(time)), 'Invalid source timestamp')
  return { sourceUrl: url, rawSha256: hash(bytes), archiveFile: path, cacheHit: cached,
    fetchedAt: cached ? null : source.fetchedAt, cacheWrittenAt: cached ? source.cacheWrittenAt : null }
}
/** Reconstruct only facts actually accepted by Fetch-Bangumi; do not infer identities by name. */
export function auditWork(work, raw, normalized, provenance) {
  const id = work.subjectId, anime = uid('Anime', id)
  assert(positiveId(id) && work.uid === anime, 'Invalid curated identity')
  const subject = raw.subject.value, persons = raw.staff.value, characters = raw.characters.value
  assert.equal(subject.id, id, 'Subject response identity mismatch')
  assert.equal(subject.type, 2, 'Curated source must be anime')
  assert(Array.isArray(persons) && Array.isArray(characters), 'Source lists must be arrays')
  const staff = [], appearances = [], cast = []
  let excludedStaff = 0, excludedActors = 0, actorRows = 0
  const names = new Set(normalized.entities.map(entity => uid(entity.type, entity.sourceId)))
  assert(names.has(anime), 'Anime is missing from normalized package')
  for (const person of persons) {
    assert(positiveId(person.id), 'Invalid source person identity')
    if (person.type !== 1) { excludedStaff++; continue }
    assert(names.has(uid('Person', person.id)), 'Person missing from normalized package')
    assert(typeof person.relation === 'string' && person.relation.trim(), 'Natural-person staff role is missing')
    staff.push(record('PARTICIPATED_IN', uid('Person', person.id), anime,
      { roleCode: roleCode(person.relation), roleName: person.relation }))
  }
  for (const character of characters) {
    assert(positiveId(character.id), 'Invalid source character identity')
    assert(names.has(uid('Character', character.id)), 'Character missing from normalized package')
    assert(typeof character.relation === 'string', 'Character role is missing')
    appearances.push(record('APPEARS_IN', uid('Character', character.id), anime, { characterRole: character.relation }))
    assert(character.actors == null || Array.isArray(character.actors), 'Actors must be an array')
    for (const actor of character.actors || []) {
      actorRows++
      if (actor == null || actor.type !== 1) { excludedActors++; continue }
      assert(positiveId(actor.id) && names.has(uid('Person', actor.id)), 'Actor missing from normalized package')
      cast.push(record('VOICED', uid('Person', actor.id), uid('Character', character.id),
        { animeUid: anime, scopeKnown: true, sourceName: 'Bangumi作品内角色配音' }))
    }
  }
  const groups = []
  for (const [kind, type, records, sourceRows, excludedRows, input, suffix] of [
    ['STAFF', 'PARTICIPATED_IN', staff, persons.length, excludedStaff, raw.staff, '/persons'],
    ['CHARACTERS', 'APPEARS_IN', appearances, characters.length, 0, raw.characters, '/characters'],
    ['CAST', 'VOICED', cast, actorRows, excludedActors, raw.characters, '/characters'],
  ]) {
    const expected = indexed(records)
    const actual = indexed(normalized.relations.filter(row => row.type === type &&
      (type === 'VOICED' ? row.properties.animeUid === anime : reference(row, 'to') === anime))
      .map(row => ({ uid: relationIdentity(row), sourceUid: reference(row, 'from'), targetUid: reference(row, 'to'), properties: row.properties })))
    assert.deepEqual([...actual.keys()].sort(), [...expected.keys()].sort(), 'Normalized relationship membership differs: ' + id + '/' + kind)
    for (const [key, row] of expected) assert.deepEqual(actual.get(key), row, 'Normalized relationship facts differ: ' + key)
    groups.push({ kind, relationshipType: type, sourceRows, acceptedRows: records.length, excludedRows,
      duplicateRows: records.length - expected.size, uniqueRecords: expected.size, responseAudit: 'VERIFIED',
      collectionStatus: 'NOT_VERIFIED', ...capture(input.path, suffix, input.bytes, provenance, id),
      relations: [...expected.values()].sort((a, b) => a.uid.localeCompare(b.uid)) })
  }
  return { subjectId: id, uid: anime, subject: capture(raw.subject.path, '', raw.subject.bytes, provenance, id), groups }
}
export function buildArchive(root) {
  const read = path => { const bytes = readFileSync(resolve(root, path)); return { path, bytes, value: JSON.parse(bytes.toString('utf8').replace(/^\uFEFF/, '')) } }
  const definition = read('data/curated/local-library.v1.json')
  const normalized = read('data/normalized/bangumi-curated-20-2026-10-23.json')
  const provenance = read('data/raw/bangumi/bangumi-curated-20-2026-10-23-provenance.json')
  assert.equal(definition.value.works.length, 20)
  assert.equal(new Set(definition.value.works.map(work => work.uid)).size, 20)
  assert.equal(normalized.value.source, 'bangumi'); assert.equal(normalized.value.schemaVersion, 1)
  assert.deepEqual(normalized.value.entities.filter(entity => entity.type === 'Anime').map(entity => Number(entity.sourceId)).sort((a,b)=>a-b),
    definition.value.works.map(work => work.subjectId).sort((a,b)=>a-b), 'Normalized package contains another work')
  assert.equal(provenance.value.failures.length, 0)
  const works = definition.value.works.map(work => auditWork(work, {
    subject: read('data/raw/bangumi/v0-subjects-' + work.subjectId + '.json'),
    staff: read('data/raw/bangumi/v0-subjects-' + work.subjectId + '-persons.json'),
    characters: read('data/raw/bangumi/v0-subjects-' + work.subjectId + '-characters.json'),
  }, normalized.value, provenance.value.requests))
  const entityIds = new Set(works.map(work => work.uid))
  for (const work of works) for (const group of work.groups) for (const row of group.relations) {
    entityIds.add(row.sourceUid); entityIds.add(row.targetUid)
  }
  assert.equal(normalized.value.entities.length, entityIds.size, 'Normalized entity count differs from accepted source identities')
  assert.deepEqual(normalized.value.entities.map(entity => uid(entity.type, entity.sourceId)).sort(), [...entityIds].sort(),
    'Normalized entities include missing, duplicated or unreferenced identities')
  assert.equal(works.flatMap(work => work.groups).reduce((sum, group) => sum + group.acceptedRows, 0), normalized.value.relations.length)
  return { schemaVersion: 1, datasetId: definition.value.datasetId, definitionVersion: hash(definition.bytes),
    normalizedPackageSha256: hash(normalized.bytes), provenanceSha256: hash(provenance.bytes),
    adapterVersion: 'Fetch-Bangumi/0.3+archive-audit/1', verifiedAt: new Date().toISOString(),
    verificationScope: 'Local archived bytes and normalized stable identities/facts agree. Provenance timestamps are not cryptographically authenticated. No upstream freshness, human identity or plot-fact audit is implied.',
    works }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = fileURLToPath(new URL('..', import.meta.url)), archive = buildArchive(root)
  const target = resolve(root, 'data/curated/local-library.archive.v1.json')
  writeFileSync(target, JSON.stringify(archive, null, 2) + '\n')
  console.log(JSON.stringify({ passed: true, works: archive.works.length,
    uniqueRelationships: archive.works.flatMap(work => work.groups).reduce((sum, group) => sum + group.uniqueRecords, 0),
    sourceResponses: archive.works.length * 3, target }))
}
