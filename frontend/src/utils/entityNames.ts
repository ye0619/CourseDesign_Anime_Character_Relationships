import type { CharacterNode } from '../types/characterGraph'

const normalized = (value: string) => value.normalize('NFKC').toLocaleLowerCase().trim()
/** Search normalization never changes or merges entity IDs. */
export function matchesEntityName(node: CharacterNode, query: string, extra: string[] = []): boolean {
  const needle = normalized(query)
  return [node.label, node.originalName || '', ...(node.aliases || []), ...extra].some(name => normalized(name).includes(needle))
}
