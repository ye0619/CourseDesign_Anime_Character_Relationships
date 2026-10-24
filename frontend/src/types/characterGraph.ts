export type GraphDataSource = 'DEMO' | 'NEO4J'
export type RelationType = 'FORM' | 'FRIEND' | 'LOVER' | 'CRUSH' | 'RIVAL' | 'FAMILY' | 'MEMBER' | 'FOUNDER' | 'MASTER' | 'PET' | 'STUDENT' | 'TEACHER' | 'PILOT' | 'SERVANT' | 'SPOUSE' | 'SUBORDINATE' | 'SUPERIOR' | 'ADAPTATION' | 'PROTOTYPE' | 'FORMER_PARTNER' | 'COLLEAGUE' | 'RELATIVE' | 'CLASSMATE' | 'FELLOW_DISCIPLE' | 'EVOLUTION' | 'PREDECESSOR' | 'OWNER' | 'OTHER' | 'TEAMMATE' | 'MENTOR'
export interface RelationDefinition { code: RelationType; label: string; bangumiId: number | null; group: string; groupLabel: string; directed: boolean; inverseCode: RelationType | null; color: string; legacy: boolean }

export interface GraphWork {
  uid: string
  title: string
  subtitle: string
  characterCount: number
  relationCount: number
}

export interface CharacterNode {
  id: string
  label: string
  group: string
  role: string
  description: string
  originalName?: string | null
  imageUrl?: string | null
  characterType?: number | null
  dataSource?: 'LOCAL' | 'BANGUMI'
  notes?: string
  gender?: string
  organizationType?: string
  nameCnSource?: string | null
  aliases?: string[]
  namePolicyVersion?: string
  nameAssertions?: NameAssertion[]
}

export interface NameAssertion {
  name: string
  locale: string
  kind: 'ORIGINAL' | 'SOURCE_CHINESE' | 'ALIAS' | 'REVIEWED_ALIAS' | 'PROJECT_PREFERRED'
  provider: 'BANGUMI' | 'PROJECT_POLICY' | 'MOEGIRL' | 'ACDB' | 'WIKIDATA' | 'PROJECT'
  sourceUrl: string
  sourceRevision: string | null
  retrievedAt: string | null
  reviewStatus: 'SOURCE' | 'REVIEWED'
  evidenceText?: string
  licenseLabel?: string
  identityVersion?: number
  providerId?: string
  reviewedOn: string | null
}

export interface CharacterEdge {
  id: string
  source: string
  target: string
  relationType: RelationType
  label: string
  description: string
  evidence: string
  sourceName: string
  directed: boolean
  spoiler?: boolean
  ended?: boolean
  sourceUrl?: string | null
  dataSource?: 'LOCAL' | 'BANGUMI'
  notes?: string
  sourceRelationId?: number | null
  archiveRevision?: string | null
}

export interface GraphMeta {
  dataSource: GraphDataSource | 'ONLINE'
  demo: boolean
  truncated: boolean
  notice: string
  maxNodes: number
  maxEdges: number
  relationStatus?: string | null
  archiveRevision?: string | null
}

export interface CharacterGraph {
  work: GraphWork
  nodes: CharacterNode[]
  edges: CharacterEdge[]
  relationTypes: RelationDefinition[]
  meta: GraphMeta
}

export interface GraphCatalog {
  items: GraphWork[]
  meta: GraphMeta
}
