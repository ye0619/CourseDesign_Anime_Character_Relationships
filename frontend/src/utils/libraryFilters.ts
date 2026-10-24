export interface LibraryFilters { status: string; keyword: string; sort: string; tagSource: string; tag: string; year: string; format: string; rating: string; missingTags: boolean }
export const defaultLibraryFilters = (): LibraryFilters => ({ status: '2', keyword: '', sort: 'updated', tagSource: 'COMMUNITY', tag: '', year: '', format: '', rating: '', missingTags: false })
const value = (input: unknown, fallback = '') => typeof input === 'string' ? input : Array.isArray(input) && typeof input[0] === 'string' ? input[0] : fallback
export function readLibraryQuery(query: Record<string, unknown>) {
  const filters = defaultLibraryFilters()
  for (const key of ['status', 'keyword', 'sort', 'tagSource', 'tag', 'year', 'format', 'rating'] as const) filters[key] = value(query[key], filters[key])
  if (filters.status === 'all') filters.status = ''
  filters.missingTags = value(query.missingTags) === 'true'
  const rawPage = value(query.page, '1'), parsed = /^\d+$/.test(rawPage) ? Number(rawPage) : 1
  return { filters, page: Number.isSafeInteger(parsed) && parsed >= 1 && parsed <= 100000 ? parsed : 1 }
}
export function libraryQuery(filters: LibraryFilters, page = 1): Record<string, string> {
  const query: Record<string, string> = {}, defaults = defaultLibraryFilters()
  for (const key of ['status', 'keyword', 'sort', 'tagSource', 'tag', 'year', 'format', 'rating'] as const) {
    const text = filters[key].trim()
    if (key === 'status' && !text) query.status = 'all'
    else if (text !== defaults[key] && text) query[key] = text
  }
  if (filters.missingTags) query.missingTags = 'true'
  if (page > 1) query.page = String(page)
  return query
}
export function libraryRequest(filters: LibraryFilters, page = 1) {
  const params = new URLSearchParams({ page: String(page), pageSize: '20', sort: filters.sort, tagSource: filters.tagSource })
  for (const key of ['status', 'keyword', 'tag', 'year', 'format', 'rating'] as const) if (filters[key].trim()) params.set(key, filters[key].trim())
  if (filters.missingTags) params.set('missingTags', 'true')
  return params
}
export function evidenceLink(filters: Partial<LibraryFilters>) { return { path: '/library', query: libraryQuery({ ...defaultLibraryFilters(), ...filters }) } }
