export interface RelationEvidence {
  uid: string
  provider: string
  sourceUrl: string
  sourceName: string
  sourceRevision: string
  attribution: string
  description: string
  sourceField?: string
  rawRelation?: string
  licenseLabel?: string
  matchMethod?: string
}
