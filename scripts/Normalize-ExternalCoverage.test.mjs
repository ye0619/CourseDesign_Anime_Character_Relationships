import {test} from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {normalizeCoverage} from './Normalize-ExternalCoverage.mjs'
const read=name=>JSON.parse(readFileSync(new URL(`../data/samples/${name}`,import.meta.url),'utf8'))
const baseline=read('external-coverage-baseline-2026-10-22.json'),info=read('moegirl-info-2026-10-22.json'),rights=read('moegirl-rights-2026-10-22.json')
test('fixed 30 samples yield 26 provisional candidates, one redirect and four explicit missing pages',()=>{
  const {report,candidateFile}=normalizeCoverage(baseline,info,rights)
  assert.deepEqual(report.works.map(w=>w.titleCandidates),[8,9,9]);assert.equal(report.newReviewedIdentities,0)
  assert.equal(candidateFile.candidates.length,26);assert.ok(candidateFile.candidates.every(c=>c.matchMethod==='TITLE_ONLY'))
  assert.equal(report.works.flatMap(w=>w.samples).filter(s=>s.redirected).length,1)
  assert.equal(report.works.flatMap(w=>w.samples).filter(s=>s.status==='MISSING').length,4)
})
test('HTTP failures and API errors are unavailable, never missing or success',()=>{
  for(const response of [{...info,httpStatus:503},{...info,body:{error:{code:'action-notallowed'}}}]){
    const {report,candidateFile}=normalizeCoverage(baseline,response,rights)
    assert.equal(report.titleCandidates,0);assert.equal(candidateFile.candidates.length,0)
    assert.ok(report.works.every(w=>w.unavailable===10&&w.missing===0))
  }
})
test('missing rights, duplicate pages and redirect cycles prevent export',()=>{
  assert.throws(()=>normalizeCoverage(baseline,info,{httpStatus:503}),/Rights metadata/)
  const cycle=structuredClone(info);cycle.body.query.redirects=[{from:'后藤一里',to:'x'},{from:'x',to:'后藤一里'}]
  assert.throws(()=>normalizeCoverage(baseline,cycle,rights),/cycle/)
  const duplicate=structuredClone(baseline);duplicate.works[0].samples[1]=duplicate.works[0].samples[0]
  assert.throws(()=>normalizeCoverage(duplicate,info,rights),/Duplicate/)
})
