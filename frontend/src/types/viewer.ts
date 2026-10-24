export interface ViewerProfile { id: number; username: string; nickname: string; avatar: string | null }
export interface ViewerState { bound: boolean; profile: ViewerProfile | null; mode: 'PUBLIC' | 'TOKEN' | null; verified: boolean; requiresReauthorization: boolean; syncStatus: 'IDLE' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'INTERRUPTED'; processed: number; remoteTotal: number; truncated: boolean; error: string | null; lastSyncedAt: string | null }
export interface ViewingRecord { subjectId: number; status: number; personalRating: number; title: string; originalName: string; imageUrl: string; releaseDate: string; episodes: number; watchedEpisodes: number; communityScore: number; updatedAt: string; personalTags: string[]; communityTags: string[] }
export interface Bucket { label: string; count: number }
export interface ViewingPage { items: ViewingRecord[]; page: number; pageSize: number; total: number; facets: { years: string[]; formats: string[]; tags: string[] } }
export interface PreferenceTag { name: string; count: number; ratedCount: number; averageRating: number | null; adjustedRating: number | null; share: number }
export interface PreferenceReport {
  summary: { total: number; completed: number; rated: number; unrated: number; averageRating: number | null; medianRating: number | null; highRated: number; dropped: number; tagged: number; tagCoverage: number }
  statuses: Bucket[]; ratings: Bucket[]; years: Bucket[]; formats: Bucket[]; tags: PreferenceTag[]; favorites: ViewingRecord[]; insights: string[]; tagSource: 'COMMUNITY' | 'PERSONAL'; methodology: string[]
  quality: { ratingCoverage: number; missingTags: number; missingYear: number; missingFormat: number }; tagSort: 'FREQUENCY' | 'RATING'; totalTags: number
}
