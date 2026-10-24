import { describe, expect, it } from 'vitest'
import { defaultLibraryFilters, libraryQuery, libraryRequest, readLibraryQuery, evidenceLink } from './libraryFilters'
describe('library evidence links', () => {
  it('preserves all-status, Unicode tags, rating and page across reload', () => {
    const filters = { ...defaultLibraryFilters(), status: '', tag: '科幻 / 宇宙', tagSource: 'PERSONAL', rating: 'HIGH', year: '2020', format: 'TV' }
    expect(readLibraryQuery(libraryQuery(filters, 3))).toEqual({ filters, page: 3 })
    const request = libraryRequest(filters, 3)
    expect(request.has('status')).toBe(false)
    expect(request.get('tag')).toBe('科幻 / 宇宙')
    expect(request.get('rating')).toBe('HIGH')
  })
  it('opens statistical evidence within completed works and keeps the correct tag source', () => {
    const link = evidenceLink({ tag: '日常', tagSource: 'PERSONAL' })
    expect(readLibraryQuery(link.query).filters).toMatchObject({ status: '2', tag: '日常', tagSource: 'PERSONAL' })
    expect(libraryRequest(readLibraryQuery(link.query).filters).get('status')).toBe('2')
  })
  it('preserves missing-data queries and normalizes invalid page values', () => {
    const filters = { ...defaultLibraryFilters(), missingTags: true, year: 'UNKNOWN' }
    expect(readLibraryQuery(libraryQuery(filters)).filters).toEqual(filters)
    expect(readLibraryQuery({ page: '-1' }).page).toBe(1)
    expect(readLibraryQuery({ page: '1e9' }).page).toBe(1)
    expect(readLibraryQuery({ page: ['2', '3'] }).page).toBe(2)
  })
})
