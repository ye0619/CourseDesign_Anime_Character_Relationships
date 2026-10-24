import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const ref = (type, sourceId) => ({ type, sourceId })
const relation = (type, from, to, properties = {}) => ({ type, from, to, properties })
const entities = [
  ...['A', 'B', 'C', 'D'].map((name, i) => ({ type: 'Anime', sourceId: `a${i + 1}`, name: `测试动画${name}`, year: 2020 + i, summary: '人工合成验收样本，不代表真实作品。' })),
  ...['甲', '乙', '丙', '丁', '戊'].map((name, i) => ({ type: 'Person', sourceId: `p${i + 1}`, name: `测试人员${name}` })),
  ...['X', 'Y', 'X'].map((name, i) => ({ type: 'Character', sourceId: `c${i + 1}`, name: `测试角色${name}` })),
]
const relations = [
  ...[['p1', 'a1', 'DIRECTOR', '监督'], ['p1', 'a1', 'SCRIPT', '脚本'], ['p1', 'a2', 'DIRECTOR', '监督'], ['p2', 'a2', 'SCRIPT', '脚本'], ['p2', 'a3', 'SCRIPT', '脚本']].map(([p, a, roleCode, roleName]) => relation('PARTICIPATED_IN', ref('Person', p), ref('Anime', a), { roleCode, roleName })),
  ...[['c1', 'a1'], ['c1', 'a2'], ['c2', 'a2'], ['c2', 'a3'], ['c3', 'a4']].map(([c, a]) => relation('APPEARS_IN', ref('Character', c), ref('Anime', a))),
  ...[['p3', 'c1', 'a1'], ['p3', 'c2', 'a2'], ['p4', 'c1', 'a2'], ['p5', 'c1', null]].map(([p, c, a]) => relation('VOICED', ref('Person', p), ref('Character', c), a ? { animeUid: `anime:fixture:${a}`, scopeKnown: true } : { scopeKnown: false })),
]
const destination = resolve(root, 'data/fixtures')
mkdirSync(destination, { recursive: true })
const save = (name, entities, relations) => writeFileSync(resolve(destination, name), JSON.stringify({ schemaVersion: 1, source: 'fixture', entities, relations }, null, 2) + '\n')
save('acceptance.json', entities, relations)
const graph = JSON.parse(readFileSync(resolve(root, 'backend/src/main/resources/fixtures/character-relations.json'), 'utf8'))
const graphEntities = [], graphRelations = []
for (const { work, nodes, edges } of graph.works) {
  graphEntities.push({ type: 'Anime', sourceId: work.uid.split(':').at(-1), name: work.title, summary: '人工构造的角色关系演示作品。' })
  for (const node of nodes) {
    graphEntities.push({ type: 'Character', sourceId: node.id.split(':').at(-1), name: node.label, groupName: node.group, role: node.role, summary: node.description })
    graphRelations.push({ type: 'APPEARS_IN', fromUid: node.id, toUid: work.uid, properties: {} })
  }
  for (const edge of edges) graphRelations.push({ type: 'CHARACTER_RELATION', fromUid: edge.source, toUid: edge.target, properties: { animeUid: work.uid, relationCode: edge.relationType, label: edge.label, description: edge.description, evidence: edge.evidence, sourceName: edge.sourceName } })
}
save('character-graph.json', graphEntities, graphRelations)
console.log(`Generated acceptance fixture: 12 entities / 14 relations; character fixture: ${graphEntities.length} entities / ${graphRelations.length} relations.`)
