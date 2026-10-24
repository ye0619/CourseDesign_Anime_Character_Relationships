import assert from 'node:assert/strict'
import { writeFileSync } from 'node:fs'

const base='http://127.0.0.1:5173'
const request=path=>fetch(base+path,{signal:AbortSignal.timeout(12000)})
const read=async path=>{const r=await request(path);assert.equal(r.status,200);return (await r.json()).data}
const ready=await read('/api/v1/health/ready')
assert.equal(ready.status,'READY')
const page=await request('/universes')
assert.equal(page.status,200);assert.match(await page.text(),/<title>映间/)
const worlds=[]
for(const [uid,nodes,edges] of [['universe:project:bocchi',33,50],['universe:project:titan-tv',61,46]]){
  const path='/api/v1/universes/'+encodeURIComponent(uid)
  const network=await read(path+'/network')
  assert.equal(network.nodes.length,nodes);assert.equal(network.edges.length,edges)
  assert.match(network.version,/\|evidence:\d+$/)
  const analysis=await read(path+'/analysis?version='+encodeURIComponent(network.version))
  assert.equal(analysis.version,network.version);assert.equal(analysis.nodeCount,nodes)
  const stale=await request(path+'/analysis?version=outdated-version')
  assert.equal(stale.status,409);assert.equal((await stale.json()).code,'WORLD_CHANGED')
  worlds.push({uid,nodes,edges,version:network.version,analysisVersionMatches:true,staleAnalysisStatus:409})
}
const maintenance=await request('/api/v1/admin/evidence-imports')
assert.equal(maintenance.status,401)
const result={checkedAt:new Date().toISOString(),passed:true,source:'Read-only checks through the running frontend proxy after application reload',ready:ready.status,worlds,anonymousMaintenanceStatus:maintenance.status,realSourceFactsChanged:false}
writeFileSync(new URL('../data/samples/evidence-maintenance-runtime.json',import.meta.url),JSON.stringify(result,null,2)+'\n')
console.log(JSON.stringify(result))
