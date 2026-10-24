import type { CollectionReturnValue, Core, Position } from 'cytoscape'
import type { CharacterEdge, CharacterNode } from '../types/characterGraph'
import { characterLayoutOptions } from './characterLayout'

export interface LayoutPoint extends Position { diameter: number }
export interface LayoutQuality {
  reason: 'valid' | 'small' | 'nonfinite' | 'coincident' | 'linear'
  axisRatio: number
  minorSpan: number
}

/** Measure each connected component, including diagonal lines. Translation invariant. */
export function measureLayout(points: LayoutPoint[]): LayoutQuality {
  if (points.some(p => !Number.isFinite(p.x) || !Number.isFinite(p.y))) return { reason: 'nonfinite', axisRatio: 0, minorSpan: 0 }
  if (points.length < 4) return { reason: 'small', axisRatio: 1, minorSpan: 0 }
  const n = points.length, cx = points.reduce((s, p) => s + p.x, 0) / n, cy = points.reduce((s, p) => s + p.y, 0) / n
  let xx = 0, yy = 0, xy = 0
  for (const p of points) { const x = p.x - cx, y = p.y - cy; xx += x * x / n; yy += y * y / n; xy += x * y / n }
  const major = (xx + yy + Math.hypot(xx - yy, 2 * xy)) / 2
  if (major < 1e-6) return { reason: 'coincident', axisRatio: 0, minorSpan: 0 }
  const ratio = Math.max(0, xx * yy - xy * xy) / (major * major)
  const angle = Math.atan2(2 * xy, xx - yy) / 2
  const projected = points.map(p => -(p.x - cx) * Math.sin(angle) + (p.y - cy) * Math.cos(angle))
  const minorSpan = Math.max(...projected) - Math.min(...projected)
  const diameters = points.map(p => p.diameter).sort((a, b) => a - b)
  const typical = diameters[Math.floor(n / 2)] || 54
  return { reason: ratio < .01 && minorSpan < typical * 1.5 ? 'linear' : 'valid', axisRatio: ratio, minorSpan }
}

export function topologyFingerprint(nodes: CharacterNode[], edges: CharacterEdge[]): string {
  const ids = new Set(nodes.map(n => n.id))
  return JSON.stringify([Array.from(ids).sort(), edges.filter(e => ids.has(e.source) && ids.has(e.target))
    .map(e => [e.id, e.source, e.target, e.directed]).sort((a, b) => String(a[0]).localeCompare(String(b[0])))])
}

export function componentQuality(elements: CollectionReturnValue): LayoutQuality {
  return measureLayout(elements.nodes().map(n => ({ ...n.position(), diameter: Math.max(n.width(), n.height()) })))
}

function seedCircle(elements: CollectionReturnValue) {
  const nodes = elements.nodes().map(n => n).sort((a, b) => a.id().localeCompare(b.id()))
  const finite = nodes.filter(n => Number.isFinite(n.position('x')) && Number.isFinite(n.position('y')))
  const center = { x: finite.length ? finite.reduce((s, n) => s + n.position('x'), 0) / finite.length : 0,
    y: finite.length ? finite.reduce((s, n) => s + n.position('y'), 0) / finite.length : 0 }
  const diameter = Math.max(80, ...nodes.map(n => Math.max(n.width(), n.height()) + 50))
  const radius = Math.max(160, nodes.length * diameter / (2 * Math.PI))
  nodes.forEach((node, i) => node.position({ x: center.x + radius * Math.cos(-Math.PI / 2 + i * 2 * Math.PI / nodes.length),
    y: center.y + radius * Math.sin(-Math.PI / 2 + i * 2 * Math.PI / nodes.length) }))
}

export interface LayoutRepair { ids: string[]; before: LayoutQuality; after: LayoutQuality; recovery: 'retry' | 'circle' }
/** One bounded retry per bad component. User-arranged components are left alone. */
export function repairLayout(cy: Core, manual = new Set<string>(), retry = true): LayoutRepair[] {
  const result: LayoutRepair[] = []
  for (const elements of cy.elements().components()) {
    const before = componentQuality(elements)
    if (before.reason === 'valid' || before.reason === 'small') continue
    if (before.reason !== 'nonfinite' && elements.nodes().some(n => manual.has(n.data('originalId')))) continue
    seedCircle(elements)
    let recovery: LayoutRepair['recovery'] = 'circle'
    if (retry) {
      try {
        elements.layout({ ...characterLayoutOptions('network', elements.nodes().length), fit: false, randomize: false, quality: 'proof', tile: false } as unknown as Parameters<Core['layout']>[0]).run()
        const check = componentQuality(elements)
        if (check.reason === 'valid' || check.reason === 'small') recovery = 'retry'
        else seedCircle(elements)
      } catch { seedCircle(elements) }
    }
    result.push({ ids: elements.nodes().map(n => n.data('originalId') as string), before, after: componentQuality(elements), recovery })
  }
  return result
}
