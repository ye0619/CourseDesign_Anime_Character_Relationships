import { describe, expect, it } from 'vitest'
import { mergeOnlineEdges, scopeOnlineEdges } from './onlineNetwork'
import { focusedPositions } from './focusNetwork'
import type { CharacterEdge } from '../types/characterGraph'
import type { OnlineNode } from '../types/onlineNetwork'
const node = (id: string): OnlineNode => ({ id, label: id, role: '主角', group: '', description: '', category: 'CHARACTER', roles: [], sourceUrl: '' })
const edge = (id: string, source: string, target: string): CharacterEdge => ({ id, source, target, relationType: 'TEACHER', label: '老师', directed: true, description: '', evidence: '', sourceName: '' })
describe('online graph projection and focus', () => {
  it('merges reciprocal duplicates conservatively and excludes relations outside the current work', () => {
    const merged = mergeOnlineEdges([edge('e', 'a', 'b')], [{ ...edge('e', 'a', 'b'), spoiler: true }, edge('other-work', 'a', 'foreign')])
    expect(merged).toHaveLength(2); expect(merged[0]!.spoiler).toBe(true)
    expect(scopeOnlineEdges([node('a'), node('b')], merged).map(e => e.id)).toEqual(['e'])
  })
  it('places any selected node at the exact center and retains every real edge without adding star edges', () => {
    const nodes = ['a', 'b', 'c', 'd', 'isolated'].map(node), edges = [edge('1', 'a', 'b'), edge('2', 'b', 'c'), edge('3', 'c', 'd')]
    const before = JSON.stringify(edges)
    for (const selected of ['a', 'c', 'isolated']) {
      const positions = focusedPositions(nodes, edges, selected)
      expect(positions.size).toBe(5); expect(positions.get(selected)).toEqual({ x: 0, y: 0 })
      const values = [...positions.values()]
      values.forEach((a, i) => values.slice(i + 1).forEach(b => expect(Math.hypot(a.x - b.x, a.y - b.y)).toBeGreaterThan(140)))
    }
    expect(JSON.stringify(edges)).toBe(before)
  })
})
