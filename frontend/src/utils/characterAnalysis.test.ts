import { describe, expect, it } from 'vitest'
import { characterMetrics, shortestCharacterPath } from './characterAnalysis'
import type { CharacterNode, CharacterEdge } from '../types/characterGraph'
const nodes: CharacterNode[] = ['a', 'b', 'c', 'd'].map(id => ({ id, label: id, role: '', group: '', description: '' }))
const edge = (id: string, source: string, target: string, directed = true): CharacterEdge => ({ id, source, target, directed, relationType: 'TEACHER', label: '老师', description: '', evidence: '', sourceName: '' })
const edges = [edge('ab', 'a', 'b'), edge('ab2', 'a', 'b'), edge('bc', 'b', 'c', false), edge('missing', 'a', 'absent')]
describe('character analysis within visible data', () => {
  it('counts unique neighbours and separates isolates without treating components as communities', () => {
    const result = characterMetrics(nodes, edges)
    expect(result.connected).toBe(3); expect(result.components).toBe(2)
    expect(result.ranking[0]).toMatchObject({ node: { id: 'b' }, degree: 2, centrality: 2 / 3 })
  })
  it('finds a shortest path with its original edge identities and obeys arrows', () => {
    expect(shortestCharacterPath(nodes, edges, 'a', 'c')).toEqual({ nodeIds: ['a', 'b', 'c'], edgeIds: ['ab', 'bc'] })
    expect(shortestCharacterPath(nodes, edges, 'c', 'a')).toBeNull()
    expect(shortestCharacterPath(nodes, edges, 'c', 'a', false)?.nodeIds).toEqual(['c', 'b', 'a'])
  })
  it('does not invent paths through filtered edges, missing nodes or isolates', () => {
    expect(shortestCharacterPath(nodes, edges.slice(0, 1), 'a', 'c')).toBeNull()
    expect(shortestCharacterPath(nodes, edges, 'a', 'd')).toBeNull()
    expect(shortestCharacterPath(nodes, edges, 'missing', 'a')).toBeNull()
    expect(shortestCharacterPath(nodes, edges, 'a', 'a')).toEqual({ nodeIds: ['a'], edgeIds: [] })
  })
})
