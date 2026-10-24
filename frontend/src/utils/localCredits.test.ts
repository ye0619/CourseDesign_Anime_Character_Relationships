import { describe, expect, it } from 'vitest'
import { localCreditPage } from './localCredits'
import { mergeCredits } from './subjectCredits'
import type { Entity, Page, Related } from '../types/catalog'
const person: Entity = { uid:'person:bangumi:1',type:'PERSON',source:'bangumi',sourceId:'1',name:'人员',active:true }
const character: Entity = { ...person,uid:'character:bangumi:2',type:'CHARACTER',sourceId:'2',name:'角色' }
const page = (items: Related[], index = 1, total = items.length): Page<Related> => ({items,page:index,pageSize:60,total})
describe('saved credits adapter', () => {
  it('merges one person with multiple actual jobs without guessing missing fields', () => {
    const result=localCreditPage(page([{entity:person,relation:{roleName:'监督'}},{entity:person,relation:{roleName:'脚本',episodeScope:'1'}}]),'anime:bangumi:3','STAFF')
    const merged=mergeCredits([result])
    expect(merged.people).toHaveLength(1)
    expect(merged.people[0]?.positions.map(job=>job.label)).toEqual(['监督','脚本'])
    expect(merged.people[0]?.person.providerType).toBeNull()
    expect(merged.people[0]?.positions[0]?.episodeScope).toBeNull()
    expect(result.source.provider).toBe('NEO4J')
    expect(result.source.fetchedAt).toBe('')
  })
  it('preserves saved cast evidence and reports a missing character instead of fabricating it', () => {
    const result=localCreditPage(page([{entity:person,character,relation:{uid:'voice:1',language:'zh',versionLabel:'译配'}},{entity:person}]),'anime:bangumi:3','CAST')
    expect(result.cast).toHaveLength(1)
    expect(result.cast[0]).toMatchObject({id:'voice:1',characterUid:character.uid,language:'zh',version:'译配',sourceRelation:null,episodeScope:null})
    expect(result.coverage.omittedCredits).toBe(1)
    expect(mergeCredits([result]).complete).toBe(false)
  })
  it('keeps paging relative to local relation rows, with explicit completion', () => {
    const first=localCreditPage(page(Array.from({length:60},()=>({entity:person,relation:{roleName:'监督'}})),1,61),'anime:bangumi:3','STAFF')
    const last=localCreditPage(page([{entity:person,relation:{roleName:'脚本'}}],2,61),'anime:bangumi:3','STAFF')
    expect(first.coverage.nextPage).toBe(2)
    expect(last.coverage.nextPage).toBeNull()
    expect(mergeCredits([last]).complete).toBe(false)
    expect(mergeCredits([first,last]).complete).toBe(true)
  })
})
