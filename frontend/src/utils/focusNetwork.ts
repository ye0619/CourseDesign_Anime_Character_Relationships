import type { CharacterEdge, CharacterNode } from '../types/characterGraph'
import type { Position } from 'cytoscape'

/** Concentric distance bands around the chosen node; never synthesizes edges. */
export function focusedPositions(nodes: CharacterNode[], edges: CharacterEdge[], selected: string): Map<string, Position> {
  const adjacency = new Map(nodes.map(node => [node.id, new Set<string>()]))
  if (!adjacency.has(selected)) return new Map()
  for (const edge of edges) {
    if (adjacency.has(edge.source) && adjacency.has(edge.target) && edge.source !== edge.target) {
      adjacency.get(edge.source)!.add(edge.target); adjacency.get(edge.target)!.add(edge.source)
    }
  }
  const depth = new Map([[selected, 0]]), queue = [selected]
  for (let i = 0; i < queue.length; i++) for (const neighbor of adjacency.get(queue[i]!)!) {
    if (!depth.has(neighbor)) { depth.set(neighbor, depth.get(queue[i]!)! + 1); queue.push(neighbor) }
  }
  const last = Math.max(...depth.values()) + 1
  const bands = new Map<number, CharacterNode[]>()
  for (const node of nodes) {
    if (node.id === selected) continue
    const band = depth.get(node.id) ?? last
    bands.set(band, [...(bands.get(band) || []), node])
  }
  const positions = new Map<string, Position>([[selected, { x: 0, y: 0 }]])
  let radius = 0
  for (const [band, members] of [...bands].sort(([a], [b]) => a - b)) {
    radius = Math.max(radius + 190, members.length * 145 / (2 * Math.PI))
    // Place nearby branches in a deterministic order, keeping labels spaced.
    members.sort((a, b) => a.id.localeCompare(b.id))
    members.forEach((node, index) => {
      const angle = -Math.PI / 2 + index * 2 * Math.PI / members.length + (band % 2 ? 0 : .12)
      positions.set(node.id, { x: radius * Math.cos(angle), y: radius * Math.sin(angle) })
    })
  }
  return positions
}
