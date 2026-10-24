import { describe, expect, it } from 'vitest'
import cytoscape from 'cytoscape'
import { characterElements, nodeKey } from './characterLayout'
import { componentQuality, measureLayout, repairLayout, topologyFingerprint } from './layoutQuality'
import type { CharacterEdge, CharacterNode } from '../types/characterGraph'

const nodes: CharacterNode[] = Array.from({ length: 9 }, (_, i) => ({ id: `c${i}`, label: `角色${i}`, role: '主角', group: '', description: '' }))
const edges: CharacterEdge[] = nodes.slice(1).map(n => ({ id: n.id, source: 'c0', target: n.id, relationType: 'FRIEND', label: '朋友', directed: false, description: '', evidence: '', sourceName: '' }))
describe('layout quality and progressive topology', () => {
  it('detects horizontal/diagonal lines, coincidence and nonfinite positions without treating small graphs as degenerate', () => {
    const line = nodes.map((_, i) => ({ x: i * 120, y: 29.5, diameter: 54 }))
    expect(measureLayout(line).reason).toBe('linear')
    expect(measureLayout(line.map(p => ({ ...p, y: p.x + 500 }))).reason).toBe('linear')
    expect(measureLayout(line.map(p => ({ ...p, x: 0, y: 0 }))).reason).toBe('coincident')
    expect(measureLayout([{ x: NaN, y: 0, diameter: 54 }]).reason).toBe('nonfinite')
    expect(measureLayout(line.slice(0, 3)).reason).toBe('small')
    expect(measureLayout(nodes.map((_, i) => ({ x: (i % 3) * 150, y: Math.floor(i / 3) * 150, diameter: 54 }))).reason).toBe('valid')
  })
  it('recognizes late edges on identical nodes, ignoring ordering and name-only updates', () => {
    expect(topologyFingerprint(nodes, [])).not.toBe(topologyFingerprint(nodes, edges))
    expect(topologyFingerprint(nodes, edges)).toBe(topologyFingerprint([...nodes].reverse().map(n => ({ ...n, label: '改名' })), [...edges].reverse()))
    expect(topologyFingerprint(nodes, edges)).not.toBe(topologyFingerprint(nodes, edges.map(e => ({ ...e, target: 'c1' }))))
  })
  it('repairs components separately while preserving all edges and finite manual coordinates', () => {
    const cy = cytoscape({ headless: true, styleEnabled: true, elements: characterElements(nodes, edges.slice(0, 3)) })
    try {
      cy.nodes().positions((_, i) => ({ x: i * 120, y: 29.5 }))
      const manual = cy.getElementById(nodeKey('c8')).position()
      const changes = repairLayout(cy, new Set(['c8']), false)
      expect(changes).toHaveLength(1)
      expect(changes[0]?.before.reason).toBe('linear')
      expect(changes[0]?.recovery).toBe('circle')
      expect(componentQuality(cy.elements().components().find(c => c.nodes().length === 4)! ).reason).toBe('valid')
      expect(cy.edges()).toHaveLength(3)
      expect(cy.getElementById(nodeKey('c8')).position()).toEqual(manual)
    } finally { cy.destroy() }
  })
  it('leaves an intentionally dragged connected component alone', () => {
    const cy = cytoscape({ headless: true, elements: characterElements(nodes, edges) })
    try {
      cy.nodes().positions((_, i) => ({ x: i * 120, y: 0 }))
      const before = cy.nodes().map(n => ({ ...n.position() }))
      expect(repairLayout(cy, new Set(['c2']), false)).toEqual([])
      expect(cy.nodes().map(n => n.position())).toEqual(before)
    } finally { cy.destroy() }
  })
})
