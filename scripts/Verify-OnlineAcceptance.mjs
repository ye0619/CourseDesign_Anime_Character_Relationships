import assert from 'node:assert/strict'
import { writeFile } from 'node:fs/promises'

const base = process.env.VERIFY_BASE_URL || 'http://127.0.0.1:5173'
async function read(path) {
  const start = performance.now()
  const response = await fetch(`${base}/api/v1${path}`, { signal: AbortSignal.timeout(20000) })
  const result = await response.json()
  assert.equal(response.status, 200, `${path}: ${result.message}`)
  return { data: result.data, ms: Math.round(performance.now() - start) }
}
const works = []
// Sequential small samples of public sources. Full fault/concurrency tests use loopback sources.
for (const id of [328609, 400602, 975]) {
  const detail = (await read(`/explore/anime/${id}`)).data
  const characters = await read(`/explore/anime/${id}/network?kind=CHARACTERS`)
  const staff = await read(`/explore/anime/${id}/network?kind=STAFF`)
  assert(characters.data.nodes.every(node => node.category === 'CHARACTER'))
  assert.equal(characters.data.edges.length, 0)
  assert(staff.data.nodes.every(node => ['PERSON', 'POSITION'].includes(node.category)))
  const ids = new Set(staff.data.nodes.map(node => node.id))
  assert(staff.data.edges.every(edge => ids.has(edge.source) && ids.has(edge.target) && edge.label))
  let relations = 0, sampled = 0
  for (const node of characters.data.nodes.slice(0, 4)) {
    const response = (await read(`/explore/characters/${node.id.split(':').at(-1)}/relations`)).data
    assert(response.edges.every(edge => edge.label && edge.sourceUrl?.startsWith('https://bgm.tv/character/')))
    relations += response.edges.length; sampled++
    await new Promise(resolve => setTimeout(resolve, 150))
  }
  const cached = (await read(`/explore/anime/${id}/network?kind=CHARACTERS`)).data
  assert.equal(cached.meta.cached, true)
  let pageTwo = null
  if (characters.data.total > 60) {
    pageTwo = (await read(`/explore/anime/${id}/network?kind=CHARACTERS&page=2`)).data
    assert.equal(pageTwo.page, 2)
    const first = new Set(characters.data.nodes.map(node => node.id))
    assert(pageTwo.nodes.every(node => !first.has(node.id)))
  }
  const result = { id, title: detail.entity.nameCn || detail.entity.name, localAvailable: !!detail.localEntity,
    characters: characters.data.total, firstPage: characters.data.nodes.length, secondPage: pageTwo?.nodes.length ?? null,
    staff: staff.data.total, firstPageStaff: staff.data.nodes.filter(node => node.category === 'PERSON').length,
    sampledCharacters: sampled, sampledRelations: relations, characterMs: characters.ms, staffMs: staff.ms,
    rosterTruncated: characters.data.meta.truncated, staffTruncated: staff.data.meta.truncated, cache: true }
  works.push(result); console.log(JSON.stringify(result))
}
assert(works.some(work => !work.localAvailable), 'Must include an online-only work')
await writeFile(new URL('../data/samples/online-acceptance.json', import.meta.url), JSON.stringify({ checkedAt: new Date().toISOString(), source: 'Live public read-only Bangumi samples; sequential; not a stress test', works }, null, 2) + '\n')
