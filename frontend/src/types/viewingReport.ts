import type { ViewingRecord } from './viewer'
export interface ReportPerson {
  uid: string; name: string; role: string; providerType: number | null
  workCount: number; ratedCount: number; averageRating: number | null; adjustedRating: number | null
  works: ViewingRecord[]; roles?: string[]
}
export interface ReportSection {
  collectionStatus: 'COMPLETE' | 'PARTIAL' | 'NOT_FETCHED' | 'NOT_VERIFIED'
  freshness: 'FRESH' | 'STALE' | 'NOT_VERIFIED'; fetchedAt: string | null; records: number
}
export interface WorkCoverage {
  subjectId: number; source: 'NEO4J_LOCAL' | 'SAVED_CREDITS' | 'PUBLIC_SNAPSHOT'; version: string | null
  staff: ReportSection; cast: ReportSection
}
interface SectionCounts {
  collection: Record<ReportSection['collectionStatus'], number>
  freshness: Record<ReportSection['freshness'], number>
}
export interface ReportScope {
  completed: number; rated: number; metadataConsidered: number; metadataCovered: number
  personnel: ReportPerson[]; overall?: ReportPerson[]; personnelTotal?: number; overallTotal?: number
  coverageSummary?: { staff: SectionCounts; cast: SectionCounts }
}
export interface ViewingReport extends ReportScope {
  version: string; generatedAt: string; methodology: string[]
  coverage?: WorkCoverage[]
  voices?: ReportVoice[]; companies?: ReportCompany[]; behaviors?: ReportBehavior[]
}
export interface ReportResult {
  report: ViewingReport | null
  task: { status: string; processed: number; total: number; error: string | null } | null
  collectionTruncated: boolean
}

export interface ReportScores { ratedCount: number; averageRating: number | null; distribution: Record<string, number> }
export interface ReportVoice {
  uid: string; name: string; imageUrl: string; workCount: number; characterCount: number; scores: ReportScores
  roles: { subjectId: number; title: string; personalRating: number; characterUid: string; characterName: string; imageUrl: string }[]
}
export interface ReportCompany {
  uid: string; name: string; imageUrl: string; completed: number; allCount: number; scores: ReportScores
  credits?: { subjectId:number; role:string; sourceUrl:string; revision:string }[]
  statuses: Record<string, number>; representatives: ViewingRecord[]; works: ViewingRecord[]
}
export interface ReportGroup { id: string; label: string; count: number; works: ViewingRecord[] }
export interface ReportBehavior {
  status: number; label: string; count: number; share: number; scores: ReportScores; tagged: number; companyCovered: number
  tags: ReportGroup[]; formats: ReportGroup[]; companies: ReportGroup[]; works: ViewingRecord[]
}
