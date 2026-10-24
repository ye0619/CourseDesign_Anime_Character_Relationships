import assert from 'node:assert/strict'
import { writeFileSync } from 'node:fs'
const base = process.env.VERIFY_BASE_URL || 'http://127.0.0.1:5173'
async function read(query) {
  const response = await fetch(`${base}/api/v1/explore/anime${query}`, { signal: AbortSignal.timeout(20000) })
  assert.equal(response.status, 200, query)
  return (await response.json()).data
}
const joined = await read(`?keyword=${encodeURIComponent('葬送')}`)
assert.equal(joined.meta.onlineStatus, 'OK'); assert.equal(joined.meta.localStatus, 'OK')
const frieren = joined.online.items.find(e => e.sourceId === '400602')
assert(frieren?.localAvailable && frieren.localChecked)
assert(joined.local.items.some(e => e.uid === frieren.uid))
const remote = await read(`?keyword=${encodeURIComponent('孤独摇滚')}`)
assert.equal(remote.meta.onlineStatus, 'OK')
assert.equal(new Set(remote.online.items.map(e => e.uid)).size, remote.online.items.length)
const bocchi = remote.online.items.find(e => e.sourceId === '328609')
assert(bocchi && !bocchi.localAvailable && bocchi.localChecked)
const onlyOnlineDetail = await read('/328609'); assert.equal(onlyOnlineDetail.entity.nameCn, '孤独摇滚！'); assert.equal(onlyOnlineDetail.localEntity, null)
const bothDetail = await read('/400602'); assert.equal(bothDetail.localEntity.uid, 'anime:bangumi:400602')
const cached = await read(`?keyword=${encodeURIComponent('孤独摇滚')}`); assert(cached.meta.cached)
const localOne = await read('?mode=LOCAL'), localTwo = await read('?mode=LOCAL&localPage=2')
assert.equal(localOne.meta.onlineStatus, 'SKIPPED'); assert(localOne.local.total > 6)
assert(localOne.local.items.every(a => !localTwo.local.items.some(b => a.uid === b.uid)))
const fixtures = await read('?mode=LOCAL&source=fixture'); assert(fixtures.local.items.length > 0); assert(fixtures.local.items.every(e => e.source === 'fixture'))
const nextOnline = await read(`?keyword=${encodeURIComponent('孤独摇滚')}&page=2`)
assert.equal(nextOnline.online.page, 2); assert(nextOnline.online.items.every(a => !remote.online.items.some(b => a.uid === b.uid)))
for (const query of ['?year=1700', '?format=invalid', '?mode=LOCAL&page=0', '?source=fixture', '/-1']) assert.equal((await fetch(`${base}/api/v1/explore/anime${query}`)).status, 400)
const report = { checkedAt: new Date().toISOString(), source: 'Bangumi public API and local database; read-only verification', joined: { online: joined.online.total, local: joined.local.total, localMatch: frieren.uid }, onlineOnly: { online: remote.online.total, local: remote.local.total, subjectId: bocchi.sourceId }, onlineOnlyDetail: true, localDetailLink: true, cache: true, separatePagination: true, localSourceFilter: true, invalidQueriesRejected: true }
writeFileSync(new URL('../data/samples/explore-verification.json', import.meta.url), JSON.stringify(report, null, 2) + '\n')
console.log('Explore verified: online + local matching, online-only details, caching, independent pagination, source filter and invalid input rejection.')
