import { identityProviders, type IdentityCandidate } from '../types/identityReview'

export const candidateFileLimit=512*1024
const limits={provider:24,providerId:150,entityType:24,canonicalUid:200,sourceUrl:1000,sourceRevision:100,licenseLabel:200,evidenceText:2000,reason:500,matchMethod:24} as const
const sourceHosts:Record<string,string[]>={BANGUMI:['bgm.tv','bangumi.tv','chii.in'],MOEGIRL:['zh.moegirl.org.cn','mzh.moegirl.org.cn','zh.moegirl.org'],ACDB:['animecharactersdatabase.com','www.animecharactersdatabase.com'],WIKIDATA:['wikidata.org','www.wikidata.org']}
function object(value:unknown):value is Record<string,unknown>{return typeof value==='object'&&value!==null&&!Array.isArray(value)}

/** Files can register provisional title candidates only; manual decisions stay in the review form. */
export function parseIdentityCandidateFile(text:string):IdentityCandidate[]{
  if(new TextEncoder().encode(text).length>candidateFileLimit)throw new Error('候选文件不能超过512 KiB')
  const data:unknown=JSON.parse(text)
  if(!object(data)||data.schemaVersion!==1||Object.keys(data).some(k=>!['schemaVersion','candidates'].includes(k))||!Array.isArray(data.candidates)||data.candidates.length<1||data.candidates.length>30)throw new Error('须为版本1的候选清单，每份1–30条')
  const keys=new Set<string>()
  return data.candidates.map((value:unknown,index:number)=>{
    const fail=()=>new Error(`第${index+1}条候选格式不正确；仅接受完整的标题候选资料`)
    if(!object(value)||Object.keys(value).length!==Object.keys(limits).length||Object.keys(value).some(k=>!(k in limits)))throw fail()
    for(const [key,max] of Object.entries(limits)){const field=value[key];if(typeof field!=='string'||!field.trim()||field.length>max||/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(field)||field!==field.trim())throw fail()}
    const row=value as unknown as IdentityCandidate
    if(!identityProviders.some(p=>p===row.provider)||!['CHARACTER','PERSON','ANIME'].includes(row.entityType)||row.matchMethod!=='TITLE_ONLY'||!new RegExp(`^${row.entityType.toLowerCase()}:[a-z0-9_-]+:[A-Za-z0-9_-]{1,100}$`).test(row.canonicalUid))throw fail()
    if(/[\u0000-\u001f\u007f]/.test(row.providerId)||(['BANGUMI','ACDB'].includes(row.provider)&&!(/^[1-9][0-9]{0,12}$/).test(row.providerId))||(row.provider==='WIKIDATA'&&!(/^Q[1-9][0-9]{0,12}$/).test(row.providerId)))throw fail()
    let url:URL;try{url=new URL(row.sourceUrl)}catch{throw fail()}
    if(url.protocol!=='https:'||url.username||url.password||url.port||(sourceHosts[row.provider]&&!sourceHosts[row.provider]!.includes(url.hostname)))throw fail()
    const key=JSON.stringify([row.provider,row.entityType,row.providerId])
    if(keys.has(key))throw new Error(`第${index+1}条与文件内已有来源编号重复`)
    keys.add(key)
    return {...row}
  })
}
