import { writeFileSync, mkdirSync } from 'node:fs'
const base='http://127.0.0.1:8080/api/v1'
const samples=Number(process.argv[2]||20)
if(!Number.isInteger(samples)||samples<5||samples>100)throw new Error('Samples must be 5—100')
const scenarios=[
  ['anime-list','/anime?pageSize=20',1000],
  ['anime-detail','/anime/anime:bangumi:12',1000],
  ['staff','/anime/anime:bangumi:12/staff',1000],
  ['neighbors','/graph/neighbors?centerUid=anime:bangumi:12&maxNodes=100',2000],
  ['common','/analysis/common?animeA=anime:bangumi:12&animeB=anime:bangumi:253',2000],
  ['ranking','/analysis/people/ranking?pageSize=20',2000],
  ['paths','/analysis/paths?fromUid=anime:bangumi:12&toUid=anime:bangumi:253&maxDepth=4&limit=5',3000],
]
const results=[]
for(const [name,path,target]of scenarios){
  const elapsed=[];let failures=0
  for(let i=-2;i<samples;i++){
    const start=performance.now();const response=await fetch(base+path);const body=await response.json();const ms=performance.now()-start
    if(i>=0){elapsed.push(ms);if(!response.ok||body.code!=='OK')failures++}
  }
  elapsed.sort((a,b)=>a-b);const p95=Math.round(elapsed[Math.ceil(samples*.95)-1]);const result={name,samples,p50Ms:Math.round(elapsed[Math.floor(samples*.5)]),p95Ms:p95,targetMs:target,failures,passed:failures===0&&p95<=target};results.push(result);console.log(JSON.stringify(result))
}
mkdirSync('.local',{recursive:true});writeFileSync('.local/performance.json',JSON.stringify({checkedAt:new Date().toISOString(),environment:'Local Windows; sequential, 2 warmups per scenario; not a concurrency load test',results},null,2)+'\n')
if(results.some(r=>!r.passed))process.exitCode=1
