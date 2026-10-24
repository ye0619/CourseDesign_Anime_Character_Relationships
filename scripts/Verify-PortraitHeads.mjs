import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import ts from '../frontend/node_modules/typescript/lib/typescript.js'

const root=new URL('../',import.meta.url),read=path=>readFileSync(new URL(path,root),'utf8')
const audit=JSON.parse(read('data/samples/10.21-portrait-head-audit.json'))
const settings=JSON.parse(read('frontend/src/data/reviewedPortraits.json'))
const source=read('frontend/src/utils/portraitFraming.ts')
// Execute the production function after TypeScript erasure; do not duplicate crop logic.
const executable=ts.transpileModule(source.replace("import reviewed from '../data/reviewedPortraits.json'",'const reviewed='+JSON.stringify(settings)),{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const {portraitFrame,imageFingerprint}=await import('data:text/javascript;base64,'+Buffer.from(executable).toString('base64'))
assert(audit.samples.length>=30);assert.equal(new Set(audit.samples.map(s=>s.uid)).size,audit.samples.length)
for(const kind of ['person','animal','multiple-people','landscape','logo','incomplete-head'])assert(audit.samples.some(s=>s.kind===kind))
const rows=audit.samples.map(s=>{
  const setting=settings.find(v=>v.entityUid===s.uid)
  assert.equal(setting?.imageFingerprint,imageFingerprint(s.imageUrl,s.dimensions))
  const radius=frame=>s.headBoxes.flatMap(b=>[b.x,b.x+b.width].flatMap(x=>[b.y,b.y+b.height].map(y=>Math.hypot((100-frame.width)*frame.x/100+x/s.dimensions.width*frame.width-50,(100-frame.height)*frame.y/100+y/s.dimensions.height*frame.height-50))))
  for(const b of s.headBoxes){assert(b.x>=0&&b.y>=0&&b.width>0&&b.height>0);assert(b.x+b.width<=s.dimensions.width&&b.y+b.height<=s.dimensions.height)}
  const next=portraitFrame(s.uid,s.imageUrl,s.dimensions,'auto',s.characterType)
  const before=portraitFrame(s.uid,s.imageUrl,s.dimensions,'auto',s.characterType,s.previousSetting?[s.previousSetting]:[])
  const afterMaxRadius=s.headBoxes.length?Math.max(...radius(next)):null
  assert(afterMaxRadius===null||afterMaxRadius<=46.000001,s.uid+' clips a marked head corner')
  return {uid:s.uid,heads:s.headBoxes.length,beforeMaxRadius:s.headBoxes.length?Math.max(...radius(before)):null,afterMaxRadius,full:next.full}
})
const sha256=bytes=>createHash('sha256').update(bytes).digest('hex')
const comparisonBoards=Array.from({length:Math.ceil(rows.length/6)},(_,i)=>'10.21-portraits/head-audit-'+String(i+1).padStart(2,'0')+'.png')
const boards=comparisonBoards.map(path=>{const bytes=readFileSync(new URL('data/samples/'+path,root));assert.equal(bytes.subarray(1,4).toString(),'PNG');return {path,sha256:sha256(bytes)}})
const result={checkedAt:new Date().toISOString(),passed:true,method:'Production crop geometry checked against Codex visual source-pixel annotations. PNG boards were drawn from actual public images in the browser; this script does not fetch source images.',limitations:audit.limitations,samples:rows.length,markedHeads:rows.reduce((n,r)=>n+r.heads,0),beforeSamplesWithCornerOutsideCircle:rows.filter(r=>r.beforeMaxRadius>50).length,afterSamplesWithCornerOutsideCircle:rows.filter(r=>r.afterMaxRadius>50).length,maskRadius:50,headTargetRadius:44,sourceHash:sha256(source),settingsHash:sha256(JSON.stringify(settings)),comparisonBoards:boards,rows}
writeFileSync(new URL('data/samples/10.21-portrait-head-acceptance.json',root),JSON.stringify(result,null,2)+'\n')
console.log(JSON.stringify({samples:result.samples,heads:result.markedHeads,clippedBefore:result.beforeSamplesWithCornerOutsideCircle,clippedAfter:result.afterSamplesWithCornerOutsideCircle,boards:boards.length,passed:true}))
