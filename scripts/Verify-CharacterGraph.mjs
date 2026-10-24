import { readFileSync, writeFileSync } from 'node:fs'
import assert from 'node:assert/strict'
const base = process.env.VERIFY_BASE_URL || 'http://127.0.0.1:8080'
const data = JSON.parse(readFileSync(new URL('../data/normalized/bangumi-character-enrichment.json', import.meta.url), 'utf8'))
async function graph(work, limit = 200) {
  const response = await fetch(`${base}/api/v1/character-graph?${new URLSearchParams({ dataSource: 'NEO4J', animeUid: work, maxNodes: String(limit) })}`)
  assert.equal(response.status, 200)
  return (await response.json()).data
}
const original = JSON.parse(readFileSync(new URL('../data/normalized/bangumi-sample.json', import.meta.url), 'utf8'))
const works = original.entities.filter(e => e.type === 'Anime').map(e => `anime:bangumi:${e.sourceId}`)
const checks = []
for (const work of works) {
  const g = await graph(work)
  const ids = new Set(g.nodes.map(n => n.id)), expected = data.relations.filter(e => e.properties.animeUid === work && ids.has(e.fromUid) && ids.has(e.toUid))
  assert.equal(g.edges.length, Math.min(400, expected.length), `Relation count after repeat import: ${work}`)
  assert.equal(new Set(g.edges.map(e => e.id)).size, g.edges.length)
  assert.equal(g.relationTypes.filter(t => !t.legacy).length, 28)
  for (const edge of g.edges) {
    assert(ids.has(edge.source) && ids.has(edge.target), 'No orphan edges')
    assert.equal(typeof edge.spoiler, 'boolean')
    assert.equal(typeof edge.ended, 'boolean')
    assert.match(edge.sourceUrl, /^https:\/\/bgm\.tv\/character\/\d+$/)
    assert.equal(edge.directed, g.relationTypes.find(t => t.code === edge.relationType).directed)
  }
  for (const node of g.nodes) {
    const expectedNode = data.entities.find(n => `character:bangumi:${n.sourceId}` === node.id)
    if (expectedNode?.nameCnSource) {
      assert.equal(node.label, expectedNode.nameCn)
      assert.equal(node.originalName, expectedNode.name)
      assert.equal(node.nameCnSource, expectedNode.nameCnSource)
    }
  }
  checks.push({ work, nodes: g.nodes.length, edges: g.edges.length, truncated: g.meta.truncated })
}
const frieren = await graph('anime:bangumi:400602')
const has = (from, to, type) => frieren.edges.some(e => e.source === `character:bangumi:${from}` && e.target === `character:bangumi:${to}` && e.relationType === type)
assert(has('89180', '86246', 'TEACHER'), 'Flamme teaches Frieren')
assert(has('86246', '86247', 'TEACHER'), 'Frieren teaches Fern')
assert(has('86247', '89182', 'CRUSH'), 'Fern admires Stark')
assert.equal(frieren.nodes.find(n => n.id === 'character:bangumi:86246').label, '芙莉莲')
assert.equal(frieren.nodes.find(n => n.id === 'character:bangumi:86247').label, '菲伦')
const limited = await graph('anime:bangumi:400602', 3)
assert.equal(limited.nodes.length, 3)
assert.equal(limited.meta.truncated, true)
assert(limited.edges.every(e => limited.nodes.some(n => n.id === e.source) && limited.nodes.some(n => n.id === e.target)))
const demo = await (await fetch(`${base}/api/v1/character-graph?dataSource=DEMO&animeUid=anime:fixture:starlit`)).json()
assert.equal(demo.data.edges.length, 13)
assert.equal(demo.data.meta.demo, true)
const report = { checkedAt: new Date().toISOString(), works: checks, sourceCharacters: data.entities.length, chineseNames: data.entities.filter(n => n.nameCnSource).length, sourceRelations: data.relations.length, frieren: { nodes: frieren.nodes.length, chineseNames: frieren.nodes.filter(n => n.nameCnSource).length, edges: frieren.edges.length, spoilers: frieren.edges.filter(e => e.spoiler).length }, directionAndLimitsPassed: true, demoCompatible: true }
writeFileSync(new URL('../data/samples/character-graph-verification.json', import.meta.url), JSON.stringify(report, null, 2) + '\n')
console.log(`Character graph verified: ${checks.length} works, ${report.chineseNames} Chinese names, ${report.sourceRelations} relations; direction, repeat import, scope, flags, limits and demo compatible.`)
