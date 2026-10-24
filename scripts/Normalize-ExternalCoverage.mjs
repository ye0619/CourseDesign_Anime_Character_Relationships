import {readFileSync,writeFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import {resolve} from 'node:path'

export function normalizeCoverage(baseline,response,rights){
  if(!Array.isArray(baseline.works)||baseline.works.length!==3||baseline.works.some(w=>w.samples?.length!==10))throw new Error('Expected a frozen 3 × 10 baseline')
  const query=response.httpStatus===200&&!response.body?.error?response.body?.query:undefined
  const pages=query?.pages?Object.values(query.pages):[]
  const redirects=new Map([...(query?.normalized??[]),...(query?.redirects??[])].map(r=>[r.from,r.to]))
  const license=rights.httpStatus===200&&!rights.body?.error?rights.body?.query?.rightsinfo:undefined
  const works=baseline.works.map(work=>{
    const samples=work.samples.map(sample=>{
      let title=sample.label;const seen=new Set()
      while(redirects.has(title)){if(seen.has(title))throw new Error('Redirect cycle');seen.add(title);title=redirects.get(title)}
      const page=pages.find(p=>p.title===title)
      const found=page&&Number.isSafeInteger(page.pageid)&&page.pageid>0&&!('missing' in page)&&Number.isSafeInteger(page.lastrevid)&&page.lastrevid>0
      const missing=page&&'missing' in page
      return {...sample,status:found?'TITLE_CANDIDATE':missing?'MISSING':'UNAVAILABLE',...(found?{pageTitle:page.title,pageId:page.pageid,revision:page.lastrevid,sourceUrl:`https://zh.moegirl.org.cn/index.php?curid=${page.pageid}&oldid=${page.lastrevid}`,redirected:title!==sample.label}:{}),checkedAt:response.checkedAt}
    })
    return {id:work.id,title:work.title,rosterCount:work.rosterCount,sampleCount:10,titleCandidates:samples.filter(s=>s.status==='TITLE_CANDIDATE').length,missing:samples.filter(s=>s.status==='MISSING').length,unavailable:samples.filter(s=>s.status==='UNAVAILABLE').length,samples}
  })
  const candidates=[]
  for(const work of works)for(const sample of work.samples)if(sample.status==='TITLE_CANDIDATE'){
    if(!license?.text||!license?.url)throw new Error('Rights metadata required before candidate export')
    candidates.push({provider:'MOEGIRL',providerId:String(sample.pageId),entityType:'CHARACTER',canonicalUid:sample.canonicalUid,sourceUrl:sample.sourceUrl,sourceRevision:String(sample.revision),licenseLabel:`萌娘百科；${license.text}`,evidenceText:`仅标题元信息，尚未核对原文。抽样作品：${work.title}；本地姓名：${sample.label}；本地原名：${sample.originalName}；来源标题：${sample.pageTitle}；页面编号：${sample.pageId}；修订：${sample.revision}。${sample.redirected?'存在标题重定向。':''}须人工核对原名、作品归属与同名歧义，补充引用依据后另行审核。`,reason:'10.22固定顺序覆盖抽样；只登记候选，不自动认可身份',matchMethod:'TITLE_ONLY'})
  }
  const keys=candidates.map(c=>JSON.stringify([c.provider,c.entityType,c.providerId]))
  if(new Set(keys).size!==keys.length)throw new Error('Duplicate candidate source IDs require manual disambiguation')
  return {report:{schemaVersion:1,checkedAt:response.checkedAt,samplingRule:baseline.samplingRule,provider:'MOEGIRL',metadataRequest:{url:response.url,httpStatus:response.httpStatus},rights:license??null,scope:'Page info only, no article body or image copied. Title candidates are not reviewed identities.',sampleCount:30,titleCandidates:candidates.length,newReviewedIdentities:0,works},candidateFile:{schemaVersion:1,candidates}}
}

if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  const read=name=>JSON.parse(readFileSync(new URL(`../data/samples/${name}`,import.meta.url),'utf8'))
  const {report,candidateFile}=normalizeCoverage(read('external-coverage-baseline-2026-10-22.json'),read('moegirl-info-2026-10-22.json'),read('moegirl-rights-2026-10-22.json'))
  for(const [name,value] of [['external-coverage-2026-10-22.json',report],['identity-title-candidates-2026-10-22.json',candidateFile]])writeFileSync(new URL(`../data/samples/${name}`,import.meta.url),JSON.stringify(value,null,2)+'\n')
  console.log(JSON.stringify({sampleCount:report.sampleCount,titleCandidates:report.titleCandidates,newReviewedIdentities:0,works:report.works.map(({title,titleCandidates,missing,unavailable})=>({title,titleCandidates,missing,unavailable}))}))
}
