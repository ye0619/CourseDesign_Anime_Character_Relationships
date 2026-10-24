import type { Entity } from './catalog'
import type { CreditEntity, CreditPage, CreditPosition, CastCredit } from './credits'
export interface MetadataSection { kind: string; pages: CreditPage[]; total: number; loadedRows: number; complete: boolean; nextPage: number | null; error: string | null }
export interface MetadataSnapshot { subjectId: number; cast: MetadataSection; staff: MetadataSection; job: { id: string; status: string; processed: number; error: string | null } | null }
export interface CompareSide { subjectId: number; entity: Partial<Entity>; error: string | null; metadata: MetadataSnapshot }
export interface Comparison { a: CompareSide; b: CompareSide; groups: { kind: string; items: {entity: CreditEntity; rolesA: CreditPosition[]; rolesB: CreditPosition[]; castA: CastCredit[]; castB: CastCredit[]}[]; complete: boolean; truncated: boolean; notice: string }[]; sharedTags: string[]; tagSimilarity: number | null; methodology: string }
