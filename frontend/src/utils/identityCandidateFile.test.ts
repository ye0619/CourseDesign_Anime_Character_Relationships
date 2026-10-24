import {describe,it,expect} from 'vitest'
import {parseIdentityCandidateFile} from './identityCandidateFile'
import file from '../../../data/samples/identity-title-candidates-2026-10-22.json'
const packageOf=(row:unknown)=>JSON.stringify({schemaVersion:1,candidates:[row]})
describe('provisional candidate file boundary',()=>{
  it('accepts the real 26-row metadata export without silently promoting evidence',()=>{const rows=parseIdentityCandidateFile(JSON.stringify(file));expect(rows).toHaveLength(26);expect(rows.every(r=>r.matchMethod==='TITLE_ONLY')).toBe(true)})
  it('rejects privilege fields, manual flags, source spoofing, duplicate IDs and oversized files',()=>{
    const row=file.candidates[0]!
    for(const changed of [{...row,actorId:7},{...row,status:'REVIEWED'},{...row,matchMethod:'MANUAL'},{...row,sourceUrl:'https://zh.moegirl.org.cn.evil.test/x'},{...row,canonicalUid:'person:bangumi:87968'},{...row,evidenceText:''}])expect(()=>parseIdentityCandidateFile(packageOf(changed))).toThrow()
    expect(()=>parseIdentityCandidateFile(JSON.stringify({schemaVersion:1,candidates:[row,row]}))).toThrow('重复')
    expect(()=>parseIdentityCandidateFile('x'.repeat(512*1024+1))).toThrow('512')
    expect(()=>parseIdentityCandidateFile(JSON.stringify({schemaVersion:1,candidates:Array(31).fill(row)}))).toThrow('1–30')
  })
})
