import assert from 'node:assert/strict'
import { writeFile } from 'node:fs/promises'

const base = process.env.PROJECT_API_BASE || 'http://127.0.0.1:5173/api/v1'
async function get(path) {
  const response = await fetch(base + path, { signal: AbortSignal.timeout(20000) })
  const body = await response.json()
  assert.equal(response.status, 200, body.message)
  assert.equal(body.code, 'OK')
  return body.data
}
const characters = await get('/explore/anime/328609/network')
const detail = await get('/explore/anime/328609')
assert.equal(detail.localEntity ?? null, null)
assert.equal(detail.meta.localStatus, 'OK')
assert.equal(characters.kind, 'CHARACTERS')
assert(characters.nodes.length > 4)
assert(characters.nodes.every(node => node.category === 'CHARACTER' && node.id.startsWith('character:bangumi:')))
assert.equal(characters.edges.length, 0) // no synthetic work-to-character hub
assert(characters.nodes.some(node => node.label === '后藤一里'))
const [member, organization, teacher, staff] = await Promise.all([
  get('/explore/characters/87968/relations'), get('/explore/characters/125172/relations'),
  get('/explore/characters/86246/relations'), get('/explore/anime/328609/network?kind=STAFF'),
])
const membership = member.edges.find(edge => edge.relationType === 'MEMBER' && [edge.source, edge.target].includes('character:bangumi:125172'))
assert(membership); assert.equal(membership.directed, false)
assert(organization.edges.some(edge => edge.id === membership.id))
assert(teacher.edges.some(edge => edge.source === 'character:bangumi:86246' && edge.target === 'character:bangumi:86247' && edge.label === '老师'))
assert(staff.nodes.every(node => ['PERSON', 'POSITION'].includes(node.category)))
assert(staff.edges.length > 0 && staff.edges.every(edge => edge.source.startsWith('person:') && edge.target.startsWith('position:') && edge.label))
for (const path of ['/explore/anime/1/network?kind=ALL', '/explore/anime/1/network?page=5', '/explore/characters/-1/relations']) {
  const response = await fetch(base + path); assert.equal(response.status, 400)
}
const cached = await get('/explore/characters/87968/relations'); assert.equal(cached.meta.cached, true)
const report = {
  checkedAt: new Date().toISOString(), work: '孤独摇滚！', subjectId: 328609,
  characters: characters.nodes.length, totalCharacters: characters.total,
  staff: staff.nodes.filter(node => node.category === 'PERSON').length, totalStaff: staff.total,
  positions: staff.nodes.filter(node => node.category === 'POSITION').length,
  onlineWithoutLocalArchive: true, separateNodeCategories: true, noSyntheticWorkHub: true,
  teacherDirection: true, memberReciprocalDeduplicated: true, publicCache: true, validation: true,
}
await writeFile(new URL('../data/samples/online-network-verification.json', import.meta.url), JSON.stringify(report, null, 2) + '\n')
console.log(JSON.stringify(report, null, 2))
