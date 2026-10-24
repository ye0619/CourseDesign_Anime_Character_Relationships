import {readFileSync,writeFileSync} from 'node:fs'
const base='http://127.0.0.1:5173/api/v1'
const file=JSON.parse(readFileSync(new URL('../data/samples/identity-title-candidates-2026-10-22.json',import.meta.url),'utf8'))
const results=[]
const ready=await fetch(`${base}/health/ready`,{signal:AbortSignal.timeout(8000)})
if(!ready.ok||(await ready.json()).data.status!=='READY')throw new Error('Application not ready')
const denied=await fetch(`${base}/admin/external-identities`,{signal:AbortSignal.timeout(8000)})
if(denied.status!==401)throw new Error('Default maintenance boundary changed')
for(const candidate of file.candidates){
  const response=await fetch(`${base}/characters/${encodeURIComponent(candidate.canonicalUid)}`,{signal:AbortSignal.timeout(8000)})
  const json=await response.json()
  results.push({uid:candidate.canonicalUid,status:response.status,available:response.ok&&json.data?.uid===candidate.canonicalUid,...(response.ok?{name:json.data.nameCn??json.data.name}: {})})
}
const result={checkedAt:new Date().toISOString(),passed:results.every(r=>r.available||r.status===404),allTargetsAvailable:results.every(r=>r.available),scope:'Read-only local target existence, not external identity verification',maintenanceDefaultDisabled:true,anonymousStatus:denied.status,targetCount:results.length,availableTargets:results.filter(r=>r.available).length,realIdentityWrites:0,results}
writeFileSync(new URL('../data/samples/external-coverage-runtime.json',import.meta.url),JSON.stringify(result,null,2)+'\n')
const readyUids=new Set(results.filter(r=>r.available).map(r=>r.uid))
writeFileSync(new URL('../data/samples/identity-title-candidates-ready-2026-10-22.json',import.meta.url),JSON.stringify({schemaVersion:1,candidates:file.candidates.filter(c=>readyUids.has(c.canonicalUid))},null,2)+'\n')
console.log(JSON.stringify({passed:result.passed,targetCount:result.targetCount,availableTargets:result.availableTargets,realIdentityWrites:0,anonymousStatus:denied.status}))
if(!result.passed)process.exitCode=1
