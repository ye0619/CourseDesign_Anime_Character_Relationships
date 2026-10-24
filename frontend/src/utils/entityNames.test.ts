import { describe, expect, it } from 'vitest'
import { matchesEntityName } from './entityNames'
import type { CharacterNode } from '../types/characterGraph'
const node: CharacterNode = { id: 'character:bangumi:125172', label: '结束乐队', originalName: '結束バンド', aliases: ['纽带乐队', 'Kessoku Band'], role: '主角', group: '', description: '' }
describe('entity name search', () => {
  it('finds the same unchanged entity by preferred name, old Chinese name and original', () => {
    for (const query of ['结束乐队', '纽带乐队', '結束バンド', 'kessoku', 'Ｋｅｓｓｏｋｕ']) expect(matchesEntityName(node, query)).toBe(true)
    expect(node.id).toBe('character:bangumi:125172')
  })
  it('preserves role search and older nodes without aliases', () => {
    expect(matchesEntityName(node, '监督', ['监督'])).toBe(true)
    expect(matchesEntityName({ ...node, aliases: undefined }, '纽带乐队')).toBe(false)
    expect(matchesEntityName(node, '不存在')).toBe(false)
    expect(matchesEntityName(node, ' ')).toBe(true)
  })
})
