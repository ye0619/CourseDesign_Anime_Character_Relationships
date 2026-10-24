import type { CharacterEdge } from '../types/characterGraph'
import type { OnlineNode } from '../types/onlineNetwork'

// Both reciprocal records may describe the same relationship. Keep the most
// restrictive spoiler flag and ended flag, rather than exposing a hidden edge.
export function mergeOnlineEdges(previous: CharacterEdge[], incoming: CharacterEdge[]): CharacterEdge[] {
  const edges = new Map(previous.map(edge => [edge.id, edge]))
  for (const edge of incoming) {
    const old = edges.get(edge.id)
    edges.set(edge.id, old ? { ...old, spoiler: old.spoiler || edge.spoiler, ended: old.ended || edge.ended, description: old.description || edge.description } : edge)
  }
  return [...edges.values()]
}
export function scopeOnlineEdges(nodes: OnlineNode[], edges: CharacterEdge[]) {
  const ids = new Set(nodes.map(node => node.id))
  return edges.filter(edge => ids.has(edge.source) && ids.has(edge.target))
}
