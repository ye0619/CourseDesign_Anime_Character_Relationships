export interface ConnectionEndpoint { characterUid: string; subjectId: number }
export interface ConnectionCandidate extends ConnectionEndpoint { name: string; imageUrl: string; workTitle: string; names: string[]; hasVoice: boolean; matchedName: string; portraitSettings?:Record<string,import('./characterEdit').CharacterCrop> }
export interface ConnectionQuery {
  from: ConnectionEndpoint; to: ConnectionEndpoint; maxHops: number; limit: number; spoilers: boolean
  storyDirection: 'CONNECTIVITY' | 'STRICT'; language: string | null; includeUnknownLanguage: boolean
  version: string | null; includeManualRelations: boolean; requireStoryRelation: boolean; graphRevision?: string; includeEnded: boolean
}
export interface ConnectionSubject { subjectId: number; title: string; castStatus: string; storyStatus: string; characters: number; voiceFacts: number; storyFacts: number }
export interface ConnectionEvidence { provider: string; externalId: string; url: string | null; sourceField: string; revision: string; retrievedAt: string | null; reviewStatus: string; text: string }
export interface ConnectionFact { uid: string; kind: 'VOICE' | 'STORY'; source: string; target: string; subjectId: number; semantic: string; directed: boolean; spoiler: boolean; ended: boolean; manual: boolean; language: string | null; version: string | null; episodeScope: string | null; scope: string; evidence: ConnectionEvidence[] }
export interface ConnectionStep { fact: ConnectionFact; traversalSource: string; traversalTarget: string; reversed: boolean }
export interface ConnectionPath { signature: string; nodes: { uid: string; kind: 'CHARACTER' | 'VOICE_ACTOR'; name: string; imageUrl: string; subjectId: number | null; portraitSettings?:Record<string,import('./characterEdit').CharacterCrop> }[]; edges: ConnectionStep[]; hopCount: number; voiceEdgeCount: number; storyEdgeCount: number; crossWorkCount: number }
export interface ConnectionResult { queryId: string; status: string; shortestPhase: string; morePhase: string; query: ConnectionQuery; graphRevision: string; paths: ConnectionPath[]; returnedPaths: number; foundPathCount: number; exactPathCount: number | null; searchExhausted: boolean; shortestProven: boolean; truncated: boolean; stopReason: string; elapsedMillis: number; scope: ConnectionSubject[] }
export interface ConnectionCoverage { status: string; graphRevision?: string; builtAt?: string; subjects: ConnectionSubject[]; characters: number; voiceActors: number; voiceFacts: number; storyFacts: number }
export interface ConnectionBuild { id: string; status: string; processed: number; total: number; error: string | null; revision: string | null }
