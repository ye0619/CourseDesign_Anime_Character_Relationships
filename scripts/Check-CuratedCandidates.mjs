import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const read = path => JSON.parse(readFileSync(new URL('../' + path, import.meta.url), 'utf8'))
const draft = read('data/curated/relationship-review-candidates.v1.json')
const archive = read('data/curated/local-library.archive.v1.json')
const dictionary = read('backend/src/main/resources/fixtures/character-relation-types.json')
assert.equal(draft.kind, 'RELATION_REVIEW_CANDIDATES')
assert.equal(draft.publishedToGraph, false); assert.equal(draft.importable, false)
assert(Array.isArray(draft.candidates) && draft.candidates.length <= 200)
const sourceIds = new Set(draft.sources.map(source => source.id))
for (const source of draft.sources) {
  const url = new URL(source.url)
  assert.equal(url.protocol, 'https:'); assert.equal(url.username, ''); assert.equal(url.password, '')
  assert(['bocchi.rocks', 'www.madoka-magica.com', 'shingeki.tv'].includes(url.hostname))
}
const keys = new Set()
for (const candidate of draft.candidates) {
  assert(!keys.has(candidate.candidateId)); keys.add(candidate.candidateId)
  assert.equal(candidate.reviewStatus, 'CANDIDATE'); assert.equal(candidate.reviewedBy, null); assert.equal(candidate.reviewedAt, null)
  for (const flag of ['scopeApproved', 'directionApproved', 'spoilerApproved', 'endedApproved', 'identityApproved']) assert.equal(candidate[flag], false)
  assert(sourceIds.has(candidate.sourceId)); assert(dictionary.some(type => type.code === candidate.relationType))
  const work = archive.works.find(item => item.subjectId === candidate.subjectId)
  assert(work, 'Candidate work must be curated')
  const characters = new Set(work.groups.find(group => group.kind === 'CHARACTERS').relations.map(row => row.sourceUid))
  assert(characters.has(candidate.source) && characters.has(candidate.target) && candidate.source !== candidate.target)
}
console.log(JSON.stringify({ candidateSafety: true, candidates: keys.size, reviewed: 0, published: 0 }))
