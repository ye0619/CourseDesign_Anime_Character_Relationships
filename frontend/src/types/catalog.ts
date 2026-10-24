import type { NameAssertion } from './characterGraph'
export type Kind = 'ANIME' | 'PERSON' | 'CHARACTER'
export interface Entity { sourceOriginal?:Entity; portraitSettings?:Record<string,import('./characterEdit').CharacterCrop>; gender?:string; notes?:string; libraryStatus?: 'ARCHIVED' | 'ONLINE_ONLY' | 'UNCHECKED'; nameNotice?:string;nameAssertions?:NameAssertion[]; uid: string; type: Kind; name: string; nameCn?: string; summary?: string; imageUrl?: string; year?: number; format?: string; airDate?: string; source: string; sourceId: string; active: boolean; aliases?: string[]; manualFields?: string[]; onlineAvailable?: boolean; localAvailable?: boolean; localChecked?: boolean; localUid?: string; score?: number; rank?: number; episodes?: number; subjectUrl?: string }
export interface Page<T> { nameNotice?:string; items: T[]; page: number; pageSize: number; total: number }
export interface Related { entity: Entity; relation?: Record<string, unknown>; character?: Entity; roles?: string[]; workCount?: number; rolesA?: string[]; rolesB?: string[]; castA?: { uid: string; name: string }[]; castB?: { uid: string; name: string }[] }
export interface GraphEdge { uid: string; source: string; target: string; type: string; properties?: Record<string, unknown> }
export interface Graph { nodes: Entity[]; edges: GraphEdge[]; truncated: boolean }
export const title = (entity: Entity) => entity.nameCn || entity.name
export const collection = (type: Kind) => ({ ANIME: 'anime', PERSON: 'people', CHARACTER: 'characters' })[type]
export const entityLink = (entity: Entity) => entity.type === 'ANIME' && entity.source === 'bangumi' ? `/explore/anime/${entity.sourceId}` : `/${collection(entity.type)}/${encodeURIComponent(entity.uid)}`
export const kindLabel = (type: Kind) => ({ ANIME: '作品', PERSON: '人员', CHARACTER: '角色' })[type]
