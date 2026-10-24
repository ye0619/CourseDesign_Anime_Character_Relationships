import { describe, expect, it } from 'vitest'
import { boxesOverlap, detailLevel, nonOverlappingLabels, placeLabels, projectRelations } from './semanticZoom'
import type { CharacterEdge } from '../types/characterGraph'
const edge = (id: string, source: string, target: string, directed = true): CharacterEdge => ({ id, source, target, directed, relationType: 'TEACHER', label: '老师', description: '', evidence: '', sourceName: '公开样本' })
describe('semantic zoom', () => {
  it('moves captions to bounded alternatives and never lets selected labels cover bodies', () => {
    const obstacle = { x: 60, y: 50, width: 60, height: 60 }
    const placed = placeLabels([
      { id: 'selected', text: '选中关系', position: { x: 60, y: 50 }, candidates: [{ x: 160, y: 50 }], width: 60, height: 20, priority: 5 },
      { id: 'blocked', text: '另一条选中关系', position: { x: 60, y: 50 }, priority: 5 },
      { id: 'outside', text: '外侧', position: { x: -10, y: 50 }, priority: 2 },
    ], 10, [obstacle], { x: 0, y: 0, width: 240, height: 140 })
    expect([...placed.keys()]).toEqual(['selected'])
    expect(boxesOverlap({ ...placed.get('selected')!, width: 60, height: 20 }, obstacle)).toBe(false)
  })
  it('uses the overview reference and hysteresis on both boundaries', () => {
    expect(detailLevel(.3, .3, 'overview')).toBe('overview')
    expect(detailLevel(.4, .3, 'overview')).toBe('middle')
    expect(detailLevel(.36, .3, 'middle')).toBe('middle')
    expect(detailLevel(.32, .3, 'middle')).toBe('overview')
    expect(detailLevel(.8, .3, 'middle')).toBe('detail')
    expect(detailLevel(.7, .3, 'detail')).toBe('detail')
    expect(detailLevel(.64, .3, 'detail')).toBe('middle')
  })
  it('preserves directions and ended states while exposing every original relation at detail', () => {
    const edges = [edge('a', 'x', 'y'), edge('b', 'x', 'y'), edge('c', 'y', 'x'), { ...edge('d', 'x', 'y'), ended: true }]
    const overview = projectRelations(edges, 'overview')
    expect(overview.map(g => g.members)).toEqual([['a', 'b'], ['c'], ['d']])
    expect(overview[0]?.label).toContain('2条')
    expect(projectRelations(edges, 'detail')).toHaveLength(4)
    expect(projectRelations(edges, 'overview', new Set(['b'])).find(g => g.id === 'b')?.members).toEqual(['b'])
  })
  it('does not include a spoiler removed upstream in a summary or count', () => {
    const edges = [edge('public', 'x', 'y'), { ...edge('spoiler', 'x', 'y'), spoiler: true }]
    expect(projectRelations(edges.filter(e => !e.spoiler), 'overview')[0]?.members).toEqual(['public'])
    expect(projectRelations(edges.filter(e => !e.spoiler), 'overview')[0]?.label).not.toContain('2条')
  })
  it('avoids colliding labels and prioritizes selection over ordinary captions', () => {
    expect([...nonOverlappingLabels([
      { id: 'a', text: '老师', position: { x: 50, y: 50 }, priority: 0 },
      { id: 'b', text: '朋友', position: { x: 55, y: 51 }, priority: 2 },
      { id: 'c', text: '同学', position: { x: 250, y: 50 }, priority: 0 },
    ], 2)]).toEqual(['b', 'c'])
  })
})
