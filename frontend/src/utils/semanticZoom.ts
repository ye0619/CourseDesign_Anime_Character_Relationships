import type { Position } from 'cytoscape'
import type { CharacterEdge } from '../types/characterGraph'

export type DetailLevel = 'overview' | 'middle' | 'detail'
/** Relative to a stable overview camera; hysteresis avoids wheel-boundary flicker. */
export function detailLevel(zoom: number, overview: number, previous: DetailLevel): DetailLevel {
  const ratio = overview > 0 && Number.isFinite(zoom / overview) ? zoom / overview : 1
  if (previous === 'detail' && ratio >= 2.16) return 'detail'
  if (ratio >= 2.64) return 'detail'
  if (previous !== 'overview' && ratio >= 1.08) return 'middle'
  return ratio >= 1.32 ? 'middle' : 'overview'
}
export interface EdgeProjection { id: string; members: string[]; label: string; primary: boolean }
/** Inputs have already passed spoiler/scope filters. Never merge opposing arrows. */
export function projectRelations(edges: CharacterEdge[], level: DetailLevel, priority = new Set<string>()): EdgeProjection[] {
  const groups = new Map<string, CharacterEdge[]>()
  for (const edge of edges) {
    const pair = edge.directed ? [edge.source, edge.target] : [edge.source, edge.target].sort()
    const key = level === 'detail' || priority.has(edge.id) ? JSON.stringify(['edge', edge.id]) :
      JSON.stringify([pair, edge.directed, !!edge.ended, edge.dataSource || 'BANGUMI', level === 'middle' ? edge.relationType : null])
    const group = groups.get(key)
    if (group) group.push(edge); else groups.set(key, [edge])
  }
  return Array.from(groups.values(), group => {
    const first = group[0]!
    const status = first.ended ? ' · 已结束' : ''
    const sources = new Set(group.map(e => e.sourceUrl || e.sourceName).filter(Boolean)).size
    const label = group.length > 1 ? `${first.label}${status} · ${group.length}条` : `${first.label}${status}${level === 'detail' && sources ? ` · ${sources}来源` : ''}`
    return { id: first.id, members: group.map(e => e.id), label, primary: priority.has(first.id) || !first.ended }
  })
}
export interface ScreenBox { x: number; y: number; width: number; height: number }
export interface ScreenLabel { id: string; position: Position; text: string; priority: number; width?: number; height?: number; candidates?: Position[] }
export function boxesOverlap(a: ScreenBox, b: ScreenBox, gap = 8): boolean {
  return Math.abs(a.x - b.x) < (a.width + b.width) / 2 + gap && Math.abs(a.y - b.y) < (a.height + b.height) / 2 + gap
}
/** Positions, measurements and obstacles use CSS pixels, never device pixels.
 * Priority changes ordering, never grants permission to cover a character. */
export function placeLabels(labels: ScreenLabel[], budget: number, obstacles: ScreenBox[] = [], viewport?: ScreenBox): Map<string, Position> {
  const accepted: ScreenBox[] = [...obstacles]
  const visible = new Map<string, Position>()
  for (const item of [...labels].sort((a, b) => b.priority - a.priority || a.id.localeCompare(b.id))) {
    if (visible.size >= budget && item.priority < 2) continue
    for (const point of [item.position, ...(item.candidates || [])]) {
      const box = { x: point.x, y: point.y, width: item.width ?? Math.min(260, item.text.length * 11 + 12), height: item.height ?? 20 }
      if (![box.x, box.y, box.width, box.height].every(Number.isFinite)) continue
      if (viewport && (box.x - box.width / 2 < 0 || box.y - box.height / 2 < 0 || box.x + box.width / 2 > viewport.width || box.y + box.height / 2 > viewport.height)) continue
      if (accepted.some(b => boxesOverlap(b, box))) continue
      accepted.push(box); visible.set(item.id, point); break
    }
  }
  return visible
}
export function nonOverlappingLabels(labels: ScreenLabel[], budget: number): Set<string> { return new Set(placeLabels(labels, budget).keys()) }
