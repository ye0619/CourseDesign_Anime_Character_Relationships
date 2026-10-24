import { describe, expect, it } from 'vitest'
import { filterCharacterGraph, relatedCharacters } from './characterGraph'
import type { CharacterEdge, CharacterNode } from '../types/characterGraph'

const nodes: CharacterNode[] = ['a', 'b', 'c'].map(id => ({ id, label: id, group: '', role: '', description: '' }))
const edge = (id: string, source: string, target: string, relationType: CharacterEdge['relationType']): CharacterEdge => ({
  id, source, target, relationType, label: '', description: '', evidence: '', sourceName: '', directed: relationType === 'MENTOR',
})
const edges = [edge('ab-friend', 'a', 'b', 'FRIEND'), edge('ab-team', 'a', 'b', 'TEAMMATE'), edge('cb', 'c', 'b', 'MENTOR')]

describe('character graph filtering', () => {
  it('supports hiding all relationship types without discarding characters', () => {
    expect(filterCharacterGraph(nodes, edges, [], null)).toEqual({ nodes, edges: [] })
  })

  it('keeps parallel relationships and preserves mentor direction', () => {
    const visible = filterCharacterGraph(nodes, edges, ['FRIEND', 'TEAMMATE', 'MENTOR'], 'b')
    expect(visible.edges).toHaveLength(3)
    const mentor = relatedCharacters(nodes, visible.edges, 'b').find(item => item.edge.id === 'cb')
    expect(mentor).toMatchObject({ character: { id: 'c' }, outgoing: false })
  })

  it('focuses only one-hop neighbours and excludes orphan edges', () => {
    const visible = filterCharacterGraph(nodes, [...edges, edge('orphan', 'a', 'missing', 'FRIEND')], ['FRIEND'], 'a')
    expect(visible.nodes.map(node => node.id)).toEqual(['a', 'b'])
    expect(visible.edges.map(item => item.id)).toEqual(['ab-friend'])
  })
})
