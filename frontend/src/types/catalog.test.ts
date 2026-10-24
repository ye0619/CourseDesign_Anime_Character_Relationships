import { describe, expect, it } from 'vitest'
import { entityLink, type Entity } from './catalog'
describe('online and local anime links', () => {
  it('opens online details for remote results and uses the same page for saved works', () => {
    const entity: Entity = { uid: 'anime:bangumi:12', type: 'ANIME', name: '测试', source: 'bangumi', sourceId: '12', active: true }
    expect(entityLink(entity)).toBe('/explore/anime/12')
    expect(entityLink({ ...entity, onlineAvailable: true })).toBe('/explore/anime/12')
  })
})
