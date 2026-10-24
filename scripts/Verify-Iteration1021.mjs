import assert from 'node:assert/strict'
import {writeFileSync,mkdirSync} from 'node:fs'
const base=process.env.VERIFY_BASE_URL||'http://127.0.0.1:8080'
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms))
class Browser {
  cookies=new Map()
  async call(path,method='GET',body,csrf=true){const headers={Accept:'application/json'};if(method!=='GET'&&csrf){const token=(await this.call('/session/csrf')).data;headers[token.headerName]=token.token}headers.Cookie=[...this.cookies].map(([k,v])=>`${k}=${v}`).join('; ');if(body!==undefined)headers['Content-Type']='application/json';const response=await fetch(base+'/api/v1'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body)});for(const cookie of response.headers.getSetCookie()){const [name,...values]=cookie.split(';')[0].split('=');if(/Max-Age=0/i.test(cookie))this.cookies.delete(name);else this.cookies.set(name,values.join('='))}return {status:response.status,...await response.json()}}
}
const viewer=new Browser(),stranger=new Browser();let bound=false
const evidence={checkedAt:new Date().toISOString(),source:'Isolated temporary Bangumi public example sai; binding deleted in finally',checks:[]}
function checked(name){evidence.checks.push(name);console.log(name)}
try{
  assert.equal((await viewer.call('/viewer/report')).status,404);assert.equal((await viewer.call('/viewer/recommendations')).status,404)
  assert.equal((await viewer.call('/explore/anime/328609/metadata/jobs','POST',undefined,false)).status,403);checked('Private read isolation and metadata CSRF')
  const compared=(await viewer.call('/explore/compare?subjectA=328609&subjectB=214265')).data
  const swapped=(await viewer.call('/explore/compare?subjectA=214265&subjectB=328609')).data
  assert.deepEqual(compared.groups.map(g=>g.items.map(i=>i.entity.id)),swapped.groups.map(g=>g.items.map(i=>i.entity.id)))
  evidence.comparison=compared.groups.map(g=>({kind:g.kind,count:g.items.length,complete:g.complete}));checked('Stable online comparison symmetry')
  assert.equal((await viewer.call('/viewer/bind','POST',{username:'sai'})).status,200);bound=true
  assert.equal((await viewer.call('/viewer/report/jobs?supplement=false','POST')).status,202)
  for(let i=0;i<20;i++){const r=(await viewer.call('/viewer/report')).data;if(r.task&&!['PENDING','RUNNING'].includes(r.task.status)){assert.equal(r.report.completed,0);break}await delay(300)}checked('Empty report persistence')
  assert.equal((await viewer.call('/viewer/sync','POST')).status,202)
  let state
  for(let i=0;i<90;i++){state=(await viewer.call('/viewer')).data;if(state.syncStatus!=='RUNNING')break;await delay(800)}assert.equal(state.syncStatus,'SUCCEEDED',state.error)
  const all=[];for(let page=1;page<=50;page++){const result=(await viewer.call(`/viewer/collections?status=&pageSize=100&page=${page}`)).data;all.push(...result.items);if(page*100>=result.total)break}
  assert.equal((await viewer.call('/viewer/report/jobs?supplement=false','POST')).status,202)
  let report
  for(let i=0;i<30;i++){report=(await viewer.call('/viewer/report')).data;if(report.task&&!['PENDING','RUNNING'].includes(report.task.status))break;await delay(300)}
  assert.equal(report.report.completed,all.filter(c=>c.status===2).length);assert.equal((await stranger.call('/viewer/report')).status,404);evidence.report={completed:report.report.completed,metadataCovered:report.report.metadataCovered,personnel:report.report.personnel.length};checked('Public sample cached report and stranger isolation')
  mkdirSync(new URL('../.local/',import.meta.url),{recursive:true});writeFileSync(new URL('../.local/10.21-evaluation-library.json',import.meta.url),JSON.stringify(all));
  assert.equal((await viewer.call('/viewer/recommendation-jobs','POST')).status,202)
  let recommendations
  for(let i=0;i<140;i++){recommendations=(await viewer.call('/viewer/recommendations')).data;if(recommendations.candidateState.status!=='RUNNING')break;await delay(800)}
  assert.notEqual(recommendations.candidateState.status,'RUNNING','Candidate job exceeded bounded wait')
  assert(recommendations.result.candidateCount>0,recommendations.candidateState.error)
  const ids=new Set(all.map(c=>c.subjectId));assert(recommendations.result.items.every(i=>!ids.has(Number(i.entity.sourceId))))
  const item=recommendations.result.items[0];assert(item)
  assert.equal((await viewer.call('/viewer/recommendation-feedback','POST',{subjectId:Number(item.entity.sourceId),action:'NOT_INTERESTED'})).status,200)
  const after=(await viewer.call('/viewer/recommendations')).data;assert(!after.result.items.some(i=>i.entity.uid===item.entity.uid))
  assert.equal((await viewer.call('/viewer/recommendation-feedback','POST',{subjectId:Number(item.entity.sourceId),action:'RESTORE'})).status,200)
  assert(!(await viewer.call('/viewer/recommendations')).data.excluded.includes(Number(item.entity.sourceId)))
  const strangerCommunity=(await stranger.call('/community/overview')).data;assert.equal(strangerCommunity.size,recommendations.result.candidateCount)
  evidence.recommendations={candidates:recommendations.result.candidateCount,returned:recommendations.result.items.length,mode:recommendations.result.mode,positiveWorks:recommendations.result.positiveWorks};checked('Real candidate pool, collection exclusion, feedback and undo')
  // Evaluation candidate features are public; only the intentionally public example is written privately.
  for(const algorithm of ['POPULAR','CONTENT','HYBRID']){const r=await viewer.call('/viewer/recommendations?algorithm='+algorithm);assert.equal(r.status,200)}
  const publicSamples=await fetch(base+'/api/v1/community/overview').then(r=>r.json());assert(publicSamples.data.subjects.length>0)
  const world=(await stranger.call('/universes/universe%3Aproject%3Abocchi/network')).data
  const strict=(await stranger.call('/universes/universe%3Aproject%3Abocchi/network?strict=true')).data
  assert.equal(strict.edges.length,0);assert(world.nodes.length>0);const second=world.edges.flatMap(e=>e.evidences).filter(e=>e.provider==='MOEGIRL');assert(second.length>0)
  const analysis=await stranger.call('/universes/universe%3Aproject%3Abocchi/analysis');assert.equal(analysis.status,200);assert.equal(analysis.data.nodeCount,world.nodes.length)
  evidence.world={nodes:world.nodes.length,edges:world.edges.length,secondSourceEvidence:second.length};checked('World global-vs-subject scope, second-source evidence and analysis')
  // Only aggregate evidence is published. No cookies or token values are serialized.
  evidence.passed=true
}finally{
  if(bound){await viewer.call('/viewer/binding','DELETE');assert.equal((await viewer.call('/viewer')).data.bound,false);assert.equal((await viewer.call('/viewer/report')).status,404);assert.equal((await viewer.call('/viewer/recommendations')).status,404);checked('Temporary binding cleanup and personal result invalidation')}
  mkdirSync(new URL('../data/samples/',import.meta.url),{recursive:true});writeFileSync(new URL('../data/samples/10.21-runtime-acceptance.json',import.meta.url),JSON.stringify(evidence,null,2)+'\n')
}
