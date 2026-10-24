export interface LocalEntity {
  id: string; kind: 'CHARACTER' | 'ORGANIZATION'; name: string; imageUrl: string; aliases: string[]
  portraits?: Record<string,import('./characterEdit').CharacterCrop>; names?: LocalName[]; preferredNameId?: string | null
  gender: string; description: string; notes: string; organizationType: string
}
export interface LocalLink {
  id: string; source: string; target: string; relationType: string; label: string; description: string
  evidence: string; spoiler: boolean; directed: boolean; notes: string
}
export interface LocalGraphDocument { version: number; schemaVersion: 1 | 2; workUid: string; nodes: LocalEntity[]; edges: LocalLink[] }

export interface LocalName { id: string; text: string; locale: string; kind: 'PRIMARY' | 'ORIGINAL' | 'ALIAS'; source: string; reviewStatus: string }
