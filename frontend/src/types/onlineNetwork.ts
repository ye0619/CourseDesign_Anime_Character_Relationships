import type { CharacterNode, CharacterEdge } from './characterGraph'

export type NetworkKind = 'CHARACTERS' | 'STAFF'
export interface OnlineNode extends CharacterNode {
  category: 'CHARACTER' | 'PERSON' | 'POSITION'
  roles: string[]
  sourceUrl: string
}
export interface OnlineMeta { fetchedAt: string; cached: boolean; stale: boolean; truncated: boolean; notice: string }
export interface OnlineNetwork { nameNotice?:string; subjectId: number; kind: NetworkKind; nodes: OnlineNode[]; edges: CharacterEdge[]; page: number; pageSize: number; total: number; meta: OnlineMeta }
export interface OnlineRelations { nameNotice?:string; characterId: string; nodes: OnlineNode[]; edges: CharacterEdge[]; meta: OnlineMeta }
