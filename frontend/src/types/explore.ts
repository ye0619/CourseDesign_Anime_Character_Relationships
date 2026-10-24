import type { Entity, Page } from './catalog'
export interface ExploreMeta { onlineStatus: 'OK' | 'ERROR' | 'STALE' | 'SKIPPED'; localStatus: 'OK' | 'ERROR' | 'SKIPPED'; fetchedAt: string | null; cached: boolean; stale: boolean; notice: string }
export interface ExploreResult { online: Page<Entity>; local: Page<Entity>; meta: ExploreMeta }
export interface ExploreDetail { entity: Entity; localEntity: Entity | null; meta: ExploreMeta; dataSource: 'LOCAL' | 'ONLINE' }
