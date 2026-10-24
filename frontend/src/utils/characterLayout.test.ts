import { describe, it, expect } from 'vitest'
import cytoscape from 'cytoscape'
import fcose from 'cytoscape-fcose'
import { characterElements, characterLayoutOptions, packCharacterComponents, separateCharacterBodies, nodeKey, edgeKey } from './characterLayout'
import { relationDefinitions, relationshipText } from './characterGraph'
import type { CharacterNode, CharacterEdge } from '../types/characterGraph'

cytoscape.use(fcose)
const nodes: CharacterNode[] = Array.from({ length: 54 }, (_, i) => ({ id: `c${i}`, label: `中文角色${i}`, role: i < 6 ? '主角' : '配角', group: '', description: '' }))
const edge = (id: string, source: string, target: string): CharacterEdge => ({ id, source, target, relationType: 'TEACHER', label: '老师', directed: true, description: '', evidence: '', sourceName: '' })

describe('character graph engine and layout', () => {
  it('separates 240 dense graph bodies including initially coincident nodes without losing edges', () => {
    const large = Array.from({ length: 240 }, (_, i) => ({ ...nodes[i % nodes.length]!, id: `large${i}` }))
    const edges = Array.from({ length: 400 }, (_, i) => edge(`dense${i}`, large[i % 240]!.id, large[(i + 17) % 240]!.id))
    const cy = cytoscape({ headless: true, styleEnabled: true, elements: characterElements(large, edges), style: [{ selector: 'node', style: { width: 82, height: 82 } }] })
    try {
      cy.layout(characterLayoutOptions('network', 240)).run()
      cy.nodes().slice(0, 4).positions(() => ({ x: 0, y: 0 }))
      separateCharacterBodies(cy)
      cy.nodes().forEach((a, i) => { cy.nodes().slice(i + 1).forEach(b => { expect(Math.hypot(a.position('x') - b.position('x'), a.position('y') - b.position('y'))).toBeGreaterThan(82) }) })
      expect(cy.edges().length).toBe(400)
    } finally { cy.destroy() }
  })
  it('retains 28 official types and inverse relationship meaning', () => {
    const official = relationDefinitions.filter(d => !d.legacy)
    expect(official).toHaveLength(28)
    expect(new Set(official.map(d => d.bangumiId)).size).toBe(28)
    expect(relationshipText(edge('e', 'a', 'b'), true)).toBe('学生')
    expect(relationshipText(edge('e', 'a', 'b'), false)).toBe('老师')
  })
  it('keeps parallel/reverse edges, directions and ended states without phantom endpoints', () => {
    const elements = characterElements(nodes.slice(0, 3), [edge('c0', 'c0', 'c1'), { ...edge('back', 'c1', 'c0'), ended: true }, edge('bad', 'c0', 'missing')])
    expect(new Set(elements.map(e => e.data.id)).size).toBe(5)
    expect(elements.find(e => e.data.id === edgeKey('c0'))?.data).toMatchObject({ source: nodeKey('c0'), target: nodeKey('c1'), arrow: 'triangle' })
    expect(elements.find(e => e.data.id === edgeKey('back'))?.data).toMatchObject({ source: nodeKey('c1'), target: nodeKey('c0'), line: 'dotted' })
    expect(elements.find(e => e.data.id === nodeKey('c0'))?.data.degree).toBe(1)
    expect(elements.find(e => e.data.id === nodeKey('c2'))?.data.degree).toBe(0)
  })
  it('runs actual fCoSE for a 54-character hub without overlapping node bodies', () => {
    const cy = cytoscape({ headless: true, styleEnabled: true, elements: characterElements(nodes, nodes.slice(1).map(n => edge(n.id, 'c0', n.id))), style: [{ selector: 'node', style: { width: 'data(size)', height: 'data(size)', label: 'data(label)', 'font-size': 13 } }] })
    try {
      cy.layout(characterLayoutOptions('network')).run()
      expect(cy.nodes()).toHaveLength(54)
      cy.nodes().forEach(a => {
        expect(Number.isFinite(a.position('x')) && Number.isFinite(a.position('y'))).toBe(true)
        cy.nodes().forEach(b => { if (a.id() !== b.id()) expect(Math.hypot(a.position('x') - b.position('x'), a.position('y') - b.position('y'))).toBeGreaterThan((a.width() + b.width()) / 2) })
      })
    } finally { cy.destroy() }
  })
  it('supports isolated rosters and reuses dragged positions on data changes', () => {
    const saved = new Map([['c1', { x: 123, y: 456 }]])
    expect(characterElements(nodes, [], saved).find(e => e.data.id === nodeKey('c1'))?.position).toEqual({ x: 123, y: 456 })
    for (const mode of ['network', 'circular', 'roster'] as const) {
      const cy = cytoscape({ headless: true, styleEnabled: true, elements: characterElements(nodes.slice(0, 9), []) })
      try { cy.layout(characterLayoutOptions(mode)).run(); cy.nodes().forEach(n => { expect(Number.isFinite(n.position('x')) && Number.isFinite(n.position('y'))).toBe(true) }) }
      finally { cy.destroy() }
    }
  })
  it('packs separate components without overlap or changing within-group distances', () => {
    const cy = cytoscape({ headless: true, styleEnabled: true, elements: characterElements(nodes.slice(0, 9), [edge('a', 'c0', 'c1'), edge('b', 'c2', 'c3')]) })
    try {
      cy.layout(characterLayoutOptions('network')).run()
      const distance = () => Math.hypot(cy.getElementById(nodeKey('c0')).position('x') - cy.getElementById(nodeKey('c1')).position('x'), cy.getElementById(nodeKey('c0')).position('y') - cy.getElementById(nodeKey('c1')).position('y'))
      const before = distance(); packCharacterComponents(cy, .6)
      expect(distance()).toBeCloseTo(before)
      const boxes = cy.elements().components().map(c => c.boundingBox({ includeLabels: true, includeOverlays: false }))
      boxes.forEach((a, i) => boxes.slice(i + 1).forEach(b => expect(a.x2 <= b.x1 || b.x2 <= a.x1 || a.y2 <= b.y1 || b.y2 <= a.y1).toBe(true)))
    } finally { cy.destroy() }
  })
})
