import type { Core, ElementDefinition, LayoutOptions, NodeSingular, Position } from 'cytoscape'
import type { CharacterEdge, CharacterNode } from '../types/characterGraph'
import { safePortraitUrl } from './portraitFraming'
import { relationStyles } from './characterGraph'

export type CharacterLayout = 'network' | 'circular' | 'roster'
export const nodeKey = (id: string) => `node:${id}`
export const edgeKey = (id: string) => `edge:${id}`
export const roleColors: Record<string, string> = { 主角: '#5a8879', 配角: '#94abb9', 闲角: '#b6af97', 客串: '#b79cae' }

// Bounded force layouts can leave a few colliding bodies in dense graphs.
// Resolve those using local numbers, then commit one positions update.
export function separateCharacterBodies(cy: Core) {
  const points = cy.nodes().map(node => ({ id: node.id(), ...node.position(), radius: Math.max(node.width(), node.height()) / 2 + 16 }))
  for (let pass = 0; pass < 64; pass++) {
    let changed = false
    for (let i = 0; i < points.length; i++) for (let j = i + 1; j < points.length; j++) {
      const a = points[i]!, b = points[j]!, distance = Math.hypot(b.x - a.x, b.y - a.y), minimum = a.radius + b.radius
      if (distance >= minimum) continue
      const dx = distance > .001 ? (b.x - a.x) / distance : Math.cos(i + j), dy = distance > .001 ? (b.y - a.y) / distance : Math.sin(i + j)
      const shift = (minimum - distance) / 2 + .5
      a.x -= dx * shift; a.y -= dy * shift; b.x += dx * shift; b.y += dy * shift; changed = true
    }
    if (!changed) break
  }
  const positions = new Map(points.map(point => [point.id, { x: point.x, y: point.y }]))
  cy.nodes().positions(node => positions.get(node.id())!)
}

// Compact the independent networks after fCoSE without changing any internal
// relative positions. Bounding boxes include captions, so groups cannot overlap.
export function packCharacterComponents(cy: Core, aspect: number) {
  const groups = cy.elements().components().map(elements => ({ elements, box: elements.boundingBox({ includeLabels: true, includeOverlays: false }) }))
    .sort((a, b) => b.box.h - a.box.h || b.elements.nodes().length - a.elements.nodes().length)
  if (groups.length < 2) return
  const gap = 60, area = groups.reduce((sum, g) => sum + (g.box.w + gap) * (g.box.h + gap), 0)
  const width = Math.max(...groups.map(g => g.box.w), Math.sqrt(area * Math.max(.45, Math.min(2, aspect))))
  let x = 0, y = 0, rowHeight = 0
  cy.batch(() => {
    for (const group of groups) {
      const { box, elements } = group
      if (x > 0 && x + box.w > width) { x = 0; y += rowHeight + gap; rowHeight = 0 }
      const dx = x - box.x1, dy = y - box.y1
      elements.nodes().positions(node => ({ x: node.position('x') + dx, y: node.position('y') + dy }))
      x += box.w + gap; rowHeight = Math.max(rowHeight, box.h)
    }
  })
}

export function characterElements(nodes: CharacterNode[], edges: CharacterEdge[], saved = new Map<string, Position>()): ElementDefinition[] {
  const ids = new Set(nodes.map(n => n.id))
  const valid = edges.filter(e => ids.has(e.source) && ids.has(e.target))
  const neighbors = new Map(nodes.map(n => [n.id, new Set<string>()]))
  for (const e of valid) if (e.source !== e.target) { neighbors.get(e.source)!.add(e.target); neighbors.get(e.target)!.add(e.source) }
  const columns = Math.max(1, Math.ceil(Math.sqrt(nodes.length)))
  return [
    ...nodes.map((n, i): ElementDefinition => ({
      group: 'nodes', data: {
        id: nodeKey(n.id), originalId: n.id, label: n.label, role: n.role, image: safePortraitUrl(n.imageUrl), local: n.dataSource === 'LOCAL', organization: n.characterType === 4,
        degree: neighbors.get(n.id)!.size, size: Math.min(62, 34 + Math.sqrt(neighbors.get(n.id)!.size) * 6),
        color: roleColors[n.role] || '#a6b5aa', shape: n.characterType === 2 || n.characterType === 3 ? 'diamond' : 'round-rectangle',
      }, position: saved.get(n.id) || { x: (i % columns) * 160, y: Math.floor(i / columns) * 160 },
    })),
    ...valid.map((e): ElementDefinition => ({ group: 'edges', data: {
      id: edgeKey(e.id), originalId: e.id, source: nodeKey(e.source), target: nodeKey(e.target),
      label: `${e.label || relationStyles[e.relationType].label}${e.ended ? ' · 已结束' : ''}`,
      color: relationStyles[e.relationType].color, arrow: e.directed ? 'triangle' : 'none', line: e.dataSource === 'LOCAL' ? 'dashed' : e.ended ? 'dotted' : 'solid',
    } })),
  ]
}

export function characterLayoutOptions(mode: CharacterLayout, nodeCount = 0): LayoutOptions {
  const common = { animate: false, fit: true, padding: 45, nodeDimensionsIncludeLabels: true }
  if (mode === 'roster') return { ...common, name: 'grid', avoidOverlap: true, spacingFactor: 1.3, sort: (a: NodeSingular, b: NodeSingular) => b.data('degree') - a.data('degree') || a.id().localeCompare(b.id()) }
  if (mode === 'circular') return { ...common, name: 'circle', avoidOverlap: true, spacingFactor: 1.25, sort: (a: NodeSingular, b: NodeSingular) => b.data('degree') - a.data('degree') || a.id().localeCompare(b.id()) }
  // Spectral initialization avoids grid symmetry around high-degree hubs.
  return { ...common, name: 'fcose', quality: nodeCount > 100 ? 'default' : 'proof', randomize: true, nodeSeparation: 100, nodeRepulsion: 18000, idealEdgeLength: 200,
    edgeElasticity: .25, numIter: nodeCount > 100 ? 1000 : 2500, tile: true, packComponents: false,
    tilingPaddingVertical: 65, tilingPaddingHorizontal: 65, componentSeparation: 100,
  } as LayoutOptions
}
