import type { CharacterGraph, CharacterNode, CharacterEdge, RelationType } from '../types/characterGraph'
import type { LocalGraphDocument } from '../types/localGraph'
export function mergeLocalGraph(official: CharacterGraph, local?: LocalGraphDocument): CharacterGraph {
  const nodes: CharacterNode[] = official.nodes.map(node => ({ ...node, dataSource: 'BANGUMI' as const }))
  const edges: CharacterEdge[] = official.edges.map(edge => ({ ...edge, dataSource: 'BANGUMI' as const }))
  if (!local) return { ...official, nodes, edges }
  for (const node of local.nodes) nodes.push({ id: node.id, label: node.name, originalName: node.aliases[0] || '', aliases: [...node.aliases,...(node.names?.map(n=>n.text)||[])],
    group: node.organizationType || '', role: node.kind === 'ORGANIZATION' ? '组织' : '本地角色', description: node.description,
    imageUrl: node.imageUrl, portraitSettings:node.portraits,portraitSubjectUid:local.workUid, characterType: node.kind === 'ORGANIZATION' ? 4 : 1, dataSource: 'LOCAL', notes: node.notes, gender: node.gender, organizationType: node.organizationType })
  const ids = new Set(nodes.map(node => node.id))
  for (const edge of local.edges) for (const id of [edge.source,edge.target]) if (!ids.has(id) && /^character:bangumi:[1-9]\d*$/.test(id)) {
    nodes.push({id,label:`Bangumi角色 #${id.split(':').at(-1)}`,group:'',role:'资料待补全',description:'已保存关系中的官方编号引用；姓名与头像暂不可用。',dataSource:'BANGUMI',characterType:1});ids.add(id)
  }
  for (const link of local.edges) if (ids.has(link.source) && ids.has(link.target)) edges.push({ ...link, relationType: link.relationType as RelationType,
    sourceName: '本地添加', evidence: link.evidence || '本地手动声明（未提供外部证据）', ended: false, dataSource: 'LOCAL' })
  return { ...official, nodes, edges, work: { ...official.work, characterCount: nodes.length, relationCount: edges.length },
    meta: { ...official.meta, notice: official.meta.notice + ' 本地补充资料由当前服务实例共享；不上传 Bangumi。' } }
}
/** Collapse an organization's exclusive members, leaving shared/externally connected people visible. */
export function collapsedMemberIds(graph: CharacterGraph, collapsed: Set<string>): Set<string> {
  const hidden = new Set<string>()
  for (const node of graph.nodes) {
    if (node.characterType === 4) continue
    const memberships = graph.edges.filter(edge => edge.relationType === 'MEMBER' && edge.source === node.id && graph.nodes.some(target => target.id === edge.target && target.dataSource === 'LOCAL' && target.characterType === 4))
    if (memberships.length && memberships.every(edge => collapsed.has(edge.target)) && !graph.edges.some(edge => edge.relationType !== 'MEMBER' && (edge.source === node.id || edge.target === node.id))) hidden.add(node.id)
  }
  return hidden
}
