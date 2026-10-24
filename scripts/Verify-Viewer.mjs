import assert from 'node:assert/strict'
import { writeFileSync } from 'node:fs'
const base = process.env.VERIFY_BASE_URL || 'http://127.0.0.1:8080'
class Browser {
  cookies = new Map()
  async call(path, method = 'GET', body, csrf = true) {
    let headers = { Accept: 'application/json' }
    if (method !== 'GET' && csrf) {
      const token = await this.call('/session/csrf')
      headers[token.data.headerName] = token.data.token
    }
    headers.Cookie = [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; ')
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    const response = await fetch(`${base}/api/v1${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
    const cookieHeaders = response.headers.getSetCookie()
    for (const cookie of cookieHeaders) { const [name, value] = cookie.split(';')[0].split('='); if (/Max-Age=0/i.test(cookie)) this.cookies.delete(name); else this.cookies.set(name, value) }
    return { status: response.status, ...(await response.json()), cookieHeaders }
  }
}
const viewer = new Browser(), stranger = new Browser(), resumed = new Browser()
let bound = false
try {
  assert.equal((await viewer.call('/viewer')).data.bound, false)
  assert.equal((await viewer.call('/viewer/bind', 'POST', { username: 'sai' }, false)).status, 403)
  assert.equal((await viewer.call('/viewer/bind', 'POST', { username: 'https://evil.test/user/sai' })).status, 400)
  assert.equal((await viewer.call('/viewer/bind', 'POST', { token: 'short' })).status, 400)
  const binding = await viewer.call('/viewer/bind', 'POST', { username: 'https://bgm.tv/user/sai' })
  assert.equal(binding.status, 200, binding.message)
  bound = true
  assert.equal(binding.data.verified, false)
  assert.equal(binding.data.profile.username, 'sai')
  assert(binding.cookieHeaders.some(c => c.startsWith('animegraph_viewer=') && /HttpOnly/i.test(c) && /SameSite=Lax/i.test(c)))
  resumed.cookies.set('animegraph_viewer', viewer.cookies.get('animegraph_viewer'))
  assert.equal((await resumed.call('/viewer')).data.bound, true, 'Persistent viewer cookie survives a new session')
  assert.equal((await stranger.call('/viewer')).data.bound, false)
  assert.equal((await stranger.call('/viewer/preferences')).status, 404)
  assert.equal((await viewer.call('/viewer/sync', 'POST')).status, 202)
  assert.equal((await viewer.call('/viewer/sync', 'POST')).status, 409)
  let state
  for (let i = 0; i < 90; i++) {
    state = (await viewer.call('/viewer')).data
    if (state.syncStatus !== 'RUNNING') break
    if (i % 5 === 0) console.log(`Syncing public verification profile: ${state.processed}/${state.remoteTotal}`)
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  assert.equal(state.syncStatus, 'SUCCEEDED', state.error)
  const completed = (await viewer.call('/viewer/collections?status=2&pageSize=7')).data
  assert(completed.items.length <= 7)
  assert(completed.items.every(i => i.status === 2 && i.personalRating >= 0 && i.personalRating <= 10))
  const report = (await viewer.call('/viewer/preferences')).data
  assert.equal(report.summary.completed, completed.total)
  assert.equal(report.summary.rated + report.summary.unrated, report.summary.completed)
  assert.equal(report.statuses.reduce((n, b) => n + b.count, 0), report.summary.total)
  const requestCollections = async query => {
    const response = await viewer.call('/viewer/collections?' + new URLSearchParams({ status: '2', ...query }))
    assert.equal(response.status, 200, response.message)
    return response.data
  }
  const unrated = await requestCollections({ rating: 'UNRATED' })
  assert.equal(unrated.total, report.summary.unrated)
  assert(unrated.items.every(c => c.personalRating === 0))
  assert.equal((await requestCollections({ rating: 'HIGH' })).total, report.summary.highRated)
  assert.equal((await requestCollections({ year: 'UNKNOWN' })).total, report.quality.missingYear)
  assert.equal((await requestCollections({ format: 'UNKNOWN' })).total, report.quality.missingFormat)
  assert.equal((await requestCollections({ missingTags: 'true' })).total, report.quality.missingTags)
  let evidenceChecks = 5
  for (const tag of report.tags) {
    const evidence = await requestCollections({ tag: tag.name, tagSource: 'COMMUNITY' })
    assert.equal(evidence.total, tag.count, `Tag evidence count: ${tag.name}`)
    assert(evidence.items.every(c => c.communityTags.includes(tag.name)))
    if (evidence.items.length) {
      const first = evidence.items[0]
      const combined = await requestCollections({ tag: tag.name, keyword: first.title, rating: first.personalRating ? String(first.personalRating) : 'UNRATED' })
      assert(combined.items.some(c => c.subjectId === first.subjectId))
    }
    evidenceChecks++
  }
  for (const bucket of report.years) { assert.equal((await requestCollections({ year: bucket.label === '未知' ? 'UNKNOWN' : bucket.label })).total, bucket.count); evidenceChecks++ }
  for (const bucket of report.formats) { assert.equal((await requestCollections({ format: bucket.label === '未标注' ? 'UNKNOWN' : bucket.label })).total, bucket.count); evidenceChecks++ }
  assert.equal((await viewer.call('/viewer/collections?status=99')).status, 400)
  assert.equal((await viewer.call('/viewer/collections?page=0')).status, 400)
  assert.equal((await viewer.call('/viewer/preferences?tagSource=invalid')).status, 400)
  const empty = (await viewer.call('/viewer/collections?keyword=definitely-not-a-real-title-39182')).data
  assert.equal(empty.total, 0)
  const personal = (await viewer.call('/viewer/preferences?tagSource=PERSONAL')).data
  assert.equal(personal.tagSource, 'PERSONAL')
  for (const tag of personal.tags) { assert.equal((await requestCollections({ tag: tag.name, tagSource: 'PERSONAL' })).total, tag.count); evidenceChecks++ }
  const ranked = (await viewer.call('/viewer/preferences?tagSort=RATING')).data
  assert.equal(ranked.tagSort, 'RATING')
  assert.equal((await viewer.call('/viewer/collections?rating=11')).status, 400)
  assert.equal((await viewer.call('/viewer/collections?year=bad')).status, 400)
  assert.equal((await viewer.call('/viewer/collections?missingTags=true&tag=x')).status, 400)
  assert([401,403].includes((await viewer.call('/admin/stats')).status))
  assert([401,403].includes((await viewer.call('/auth/login', 'POST', { username: 'not-used', password: 'not-used' })).status))
  const result = { checkedAt: new Date().toISOString(), source: 'Bangumi public example profile sai; local verification binding deleted afterwards', total: report.summary.total, completed: report.summary.completed, rated: report.summary.rated, unrated: report.summary.unrated, averageRating: report.summary.averageRating, tagCount: report.tags.length, evidenceChecks, statisticalEvidenceConsistent: true, isolation: true, persistentAssociation: true, csrf: true, duplicateSyncRejected: true, legacyLoginDisabled: true }
  writeFileSync(new URL('../data/samples/viewer-verification.json', import.meta.url), JSON.stringify(result, null, 2) + '\n')
  console.log(`Verified public collection sync: ${result.total} anime, ${result.completed} completed, ${result.rated} rated; statistical consistency, isolation, session persistence, CSRF and disabled legacy login passed.`)
} finally {
  if (bound) { assert.equal((await viewer.call('/viewer/binding', 'DELETE')).status, 200); assert.equal((await resumed.call('/viewer')).data.bound, false); assert.equal((await resumed.call('/viewer/preferences')).status, 404); console.log('Temporary verification binding and collections removed.') }
}
