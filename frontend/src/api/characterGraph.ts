import { get } from './client'
import type { CharacterGraph, GraphCatalog, GraphDataSource } from '../types/characterGraph'

export function getGraphWorks(dataSource: GraphDataSource, signal?: AbortSignal) {
  return get<GraphCatalog>(`/character-graph/works?${new URLSearchParams({ dataSource })}`, signal)
}

export function getCharacterGraph(dataSource: GraphDataSource, animeUid: string, signal?: AbortSignal) {
  return get<CharacterGraph>(`/character-graph?${new URLSearchParams({ dataSource, animeUid, maxNodes: '150' })}`, signal)
}
