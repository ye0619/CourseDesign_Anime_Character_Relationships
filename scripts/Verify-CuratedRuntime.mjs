import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'

const root = new URL('..', import.meta.url)
const definition = JSON.parse(readFileSync(new URL('data/curated/local-library.v1.json', root), 'utf8'))
const archiveBytes = readFileSync(new URL('data/curated/local-library.archive.v1.json', root))
const archive = JSON.parse(archiveBytes), archiveVersion = createHash('sha256').update(archiveBytes).digest('hex')
const base = process.env.PROJECT_API_BASE || 'http://127.0.0.1:5173/api/v1'
async function get(path) {
  const response = await fetch(base + path, { signal: AbortSignal.timeout(15000) })
  const body = await response.json()
  assert.equal(response.status, 200, path + ': ' + body.code)
  return body.data
}
const library = await get('/curated')
assert.deepEqual(library.items.map(item => item.work.uid), definition.works.map(work => work.uid))
assert.equal(new Set(library.items.map(item => item.work.uid)).size, 20)
const items = []
for (const entry of library.items) {
  const id = entry.work.subjectId, uid = encodeURIComponent(entry.work.uid)
  const detail = await get('/explore/anime/' + id)
  assert.equal(detail.dataSource, 'LOCAL'); assert.equal(detail.meta.onlineStatus, 'SKIPPED')
  assert.equal(detail.entity.uid, entry.work.uid); assert.equal(detail.curatedVersion, library.version)
  const staff = await get('/anime/' + uid + '/staff?pageSize=1')
  const cast = await get('/anime/' + uid + '/cast?pageSize=1')
  const graph = await get('/character-graph?dataSource=NEO4J&animeUid=' + uid + '&maxNodes=24&maxEdges=100')
  assert.equal(staff.total, entry.coverage.staffRecords); assert.equal(cast.total, entry.coverage.castRecords)
  assert.equal(graph.work.uid, entry.work.uid); assert.equal(graph.meta.demo, false)
  const coverage = await get('/curated/' + id + '/coverage')
  assert.equal(coverage.definitionVersion, library.version); assert.equal(coverage.archiveVersion, archiveVersion)
  assert.equal(coverage.workStatus, 'ACTIVE'); assert.equal(coverage.groups.length, 3)
  const expected = archive.works.find(work => work.subjectId === id)
  for (const group of coverage.groups) {
    const frozen = expected.groups.find(item => item.kind === group.kind)
    assert.equal(group.uniqueRecords, frozen.uniqueRecords); assert.equal(group.difference.expected, frozen.uniqueRecords)
    assert.equal(group.difference.found, frozen.uniqueRecords); assert.equal(group.difference.missing, 0)
    assert.equal(group.difference.identityConflicts, 0); assert.equal(group.difference.duplicateIdentities, 0)
    assert.equal(group.collectionStatus, 'NOT_VERIFIED')
    if (group.source.cacheHit) { assert.equal(group.source.fetchedAt, null); assert.ok(group.source.cacheWrittenAt); assert.equal(group.source.freshness, 'NOT_VERIFIED') }
  }
  items.push({ subjectId: id, localDetail: true, upstream: 'SKIPPED', staffRecords: staff.total, castRecords: cast.total,
    graphNodes: graph.nodes.length, graphEdges: graph.edges.length, graphTruncated: graph.meta.truncated,
    sourceCoverage: coverage.groups.map(group => ({ kind: group.kind, ...group.difference, samples: undefined })) })
}
const result = { checkedAt: new Date().toISOString(), datasetId: library.datasetId, version: library.version, archiveVersion,
  frozenRelationsFound: items.flatMap(item => item.sourceCoverage).reduce((sum, group) => sum + group.found, 0),
  passed: true, scope: 'Read-only real local database and HTTP; frozen-source differences are not an import submission or a human identity/plot audit', items }
writeFileSync(new URL('.local/curated-runtime-acceptance.json', root), JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify({ passed: true, subjects: items.length, datasetId: library.datasetId, record: fileURLToPath(new URL('.local/curated-runtime-acceptance.json', root)) }))
