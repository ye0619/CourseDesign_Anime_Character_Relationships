import type { NameAssertion } from './characterGraph'
export type CreditKind = 'CAST' | 'STAFF'
export interface CreditEntity {
  id: string; label: string; originalName: string | null; imageUrl: string; category: 'PERSON' | 'CHARACTER'
  providerType: number | null; aliases: string[]; nameAssertions: NameAssertion[]; namePolicyVersion: string; sourceUrl: string
}
export interface CreditPosition { code: string; label: string; episodeScope: string | null; description: string | null }
export interface CastCredit {
  id: string; subjectUid: string; personUid: string; characterUid: string; sourceRelation: number | null
  sourceRelationLabel: string; performanceType: 'VOICE' | 'LIVE_ACTION' | 'UNKNOWN'; language: string | null
  languageSource: string | null; version: string | null; episodeScope: string | null; description: string | null; sourceField: string
}
export interface CreditPage {
  nameNotice?:string
  subjectUid: string; kind: CreditKind; people: CreditEntity[]; characters: CreditEntity[]
  staff: { personUid: string; positions: CreditPosition[] }[]; cast: CastCredit[]
  coverage: { page: number; pageSize: number; total: number; returnedRows: number; parsedRows: number; omittedRows: number
    omittedCredits: number; unknownCastRows: number; nextPage: number | null; exhausted: boolean; capped: boolean; completeness: 'COMPLETE' | 'PARTIAL' }
  source: { provider: string; sourceUrl: string; sourceField: string; adapterVersion: string; fetchedAt: string | null; cached: boolean; stale: boolean; namePolicyVersion: string }
  snapshotVersion: string
}
