export interface LocalEntity {
  id: string; kind: 'CHARACTER' | 'ORGANIZATION'; name: string; imageUrl: string; aliases: string[]
  gender: string; description: string; notes: string; organizationType: string
}
export interface LocalLink {
  id: string; source: string; target: string; relationType: string; label: string; description: string
  evidence: string; spoiler: boolean; directed: boolean; notes: string
}
export interface LocalGraphDocument { version: number; schemaVersion: 1; workUid: string; nodes: LocalEntity[]; edges: LocalLink[] }
