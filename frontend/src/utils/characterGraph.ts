import type { CharacterEdge, CharacterNode, RelationType } from '../types/characterGraph'
import dictionary from '../../../backend/src/main/resources/fixtures/character-relation-types.json'
import type { RelationDefinition } from '../types/characterGraph'

export const relationDefinitions = dictionary as RelationDefinition[]
export const relationStyles = Object.fromEntries(relationDefinitions.map(item => [item.code, { ...item, tint: `${item.color}16` }])) as Record<RelationType, RelationDefinition & { tint: string }>

export const relationTypes = Object.keys(relationStyles) as RelationType[]
export const characterTypeLabel = (type?: number | null) => ({ 1: '角色', 2: '机体', 3: '舰船', 4: '组织' })[type as 1 | 2 | 3 | 4] || '角色'
export function relationshipText(edge: CharacterEdge, outgoing: boolean) {
  if (edge.dataSource === 'LOCAL') {
    if (!edge.directed) return edge.label
    if (edge.relationType === 'MEMBER') return outgoing ? `所属组织 · ${edge.label}` : `成员身份 · ${edge.label}`
    return outgoing ? `是对方的${edge.label}` : `对方是其${edge.label}`
  }
  if (edge.relationType === 'MENTOR') return outgoing ? '学生' : '老师'
  const definition = relationStyles[edge.relationType]
  if (!edge.directed) return edge.label || definition.label
  if (!outgoing) return definition.label
  if (definition.inverseCode) return relationStyles[definition.inverseCode].label
  return ({ MEMBER: '所属组织', FOUNDER: '创立的组织', PILOT: '驾驶的机体', CRUSH: '单恋对象' } as Partial<Record<RelationType, string>>)[edge.relationType] || definition.label
}

export function filterCharacterGraph(
  nodes: CharacterNode[], edges: CharacterEdge[], types: RelationType[], focusId: string | null,
) {
  const allowed = new Set(types)
  const ids = new Set(nodes.map(node => node.id))
  const visibleEdges = edges.filter(edge => allowed.has(edge.relationType)
    && ids.has(edge.source) && ids.has(edge.target)
    && (!focusId || edge.source === focusId || edge.target === focusId))
  if (!focusId) return { nodes, edges: visibleEdges }
  const nearbyIds = new Set([focusId, ...visibleEdges.flatMap(edge => [edge.source, edge.target])])
  return { nodes: nodes.filter(node => nearbyIds.has(node.id)), edges: visibleEdges }
}

export function relatedCharacters(nodes: CharacterNode[], edges: CharacterEdge[], selectedId: string) {
  const index = new Map(nodes.map(node => [node.id, node]))
  return edges.filter(edge => edge.source === selectedId || edge.target === selectedId)
    .flatMap(edge => {
      const other = index.get(edge.source === selectedId ? edge.target : edge.source)
      return other ? [{ edge, character: other, outgoing: edge.source === selectedId }] : []
    })
}
