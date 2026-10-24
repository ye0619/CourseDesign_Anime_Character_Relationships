import type { CharacterEdge, CharacterNode } from '../types/characterGraph'
export interface CharacterPath { nodeIds: string[]; edgeIds: string[] }

export function characterMetrics(nodes: CharacterNode[], edges: CharacterEdge[]) {
  const adjacent = new Map(nodes.map(n => [n.id, new Set<string>()]))
  for (const edge of edges) if (edge.source !== edge.target && adjacent.has(edge.source) && adjacent.has(edge.target)) { adjacent.get(edge.source)!.add(edge.target); adjacent.get(edge.target)!.add(edge.source) }
  let components = 0; const visited = new Set<string>()
  for (const node of nodes) if (!visited.has(node.id)) {
    components++; const queue = [node.id]; visited.add(node.id)
    for (let i = 0; i < queue.length; i++) for (const other of adjacent.get(queue[i]!)!) if (!visited.has(other)) { visited.add(other); queue.push(other) }
  }
  const ranking = nodes.map(node => ({ node, degree: adjacent.get(node.id)!.size, centrality: nodes.length < 2 ? 0 : adjacent.get(node.id)!.size / (nodes.length - 1) }))
    .sort((a, b) => b.degree - a.degree || a.node.id.localeCompare(b.node.id))
  return { components, connected: ranking.filter(n => n.degree > 0).length, ranking }
}

export function shortestCharacterPath(nodes: CharacterNode[], edges: CharacterEdge[], from: string, to: string, directed = true, maxDepth = 6): CharacterPath | null {
  const adjacent = new Map(nodes.map(n => [n.id, [] as { to: string; edge: string }[]]))
  if (!adjacent.has(from) || !adjacent.has(to)) return null
  for (const e of [...edges].sort((a, b) => a.id.localeCompare(b.id))) if (adjacent.has(e.source) && adjacent.has(e.target)) {
    adjacent.get(e.source)!.push({ to: e.target, edge: e.id })
    if (!directed || !e.directed) adjacent.get(e.target)!.push({ to: e.source, edge: e.id })
  }
  const previous = new Map<string, { from: string; edge: string }>(), queue = [from], visited = new Set([from]), depth = new Map([[from,0]])
  for (let i = 0; i < queue.length && !visited.has(to); i++) {
    const currentDepth=depth.get(queue[i]!)!
    if(currentDepth>=Math.max(1,Math.min(6,maxDepth)))continue
    for (const next of adjacent.get(queue[i]!)!) if (!visited.has(next.to)) { visited.add(next.to);depth.set(next.to,currentDepth+1); previous.set(next.to, { from: queue[i]!, edge: next.edge }); queue.push(next.to) }
  }
  if (!visited.has(to)) return null
  const nodeIds = [to], edgeIds: string[] = []; let current = to
  while (current !== from) { const step = previous.get(current)!; edgeIds.unshift(step.edge); nodeIds.unshift(step.from); current = step.from }
  return { nodeIds, edgeIds }
}
