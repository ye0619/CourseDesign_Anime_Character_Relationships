import {readFileSync,writeFileSync} from 'node:fs'
import {createRequire} from 'node:module'
const require=createRequire(new URL('../frontend/package.json',import.meta.url)),{parseDocument}=require('yaml')
const file=new URL('../docs/接口契约.yaml',import.meta.url),doc=parseDocument(readFileSync(file,'utf8'))
const str={type:'string'},int={type:'integer'},bool={type:'boolean'},num={type:'number'},nullable=t=>({type:[t,'null']}),ref=n=>({$ref:`#/components/schemas/${n}`}),arr=x=>({type:'array',items:typeof x==='string'?ref(x):x}),obj=p=>({type:'object',properties:p,required:Object.keys(p),additionalProperties:false}),maybe=n=>({anyOf:[ref(n),{type:'null'}]})
const schemas={
 MetadataSection:obj({kind:str,pages:arr('CreditPage'),total:int,loadedRows:int,complete:bool,nextPage:nullable('integer'),error:nullable('string')}),
 MetadataJob:obj({id:str,status:{type:'string',enum:['PENDING','RUNNING','PARTIAL','COMPLETE','FAILED']},processed:int,error:nullable('string')}),
 MetadataSnapshot:obj({subjectId:int,cast:ref('MetadataSection'),staff:ref('MetadataSection'),job:maybe('MetadataJob')}),
 OnlineCompareSide:obj({subjectId:int,entity:{type:'object',description:'来源失败可为空；正常为Entity及标签/评分人数。',additionalProperties:true},error:nullable('string'),metadata:ref('MetadataSnapshot')}),
 OnlineCompareItem:obj({entity:ref('CreditEntity'),rolesA:arr('CreditPosition'),rolesB:arr('CreditPosition'),castA:arr('CastCredit'),castB:arr('CastCredit')}),
 OnlineCompareGroup:obj({kind:{type:'string',enum:['STAFF','VOICE','CHARACTER']},items:arr('OnlineCompareItem'),complete:bool,truncated:bool,notice:str}),
 OnlineComparison:obj({a:ref('OnlineCompareSide'),b:ref('OnlineCompareSide'),groups:arr('OnlineCompareGroup'),sharedTags:arr(str),tagSimilarity:nullable('number'),methodology:str}),
 ViewingPersonStat:obj({uid:str,name:str,role:str,providerType:nullable('integer'),workCount:int,ratedCount:int,averageRating:nullable('number'),adjustedRating:nullable('number'),works:arr('ViewingRecord')}),
 ViewingPersonnelReport:obj({version:str,generatedAt:{type:'string',format:'date-time'},completed:int,rated:int,metadataConsidered:int,metadataCovered:int,personnel:arr('ViewingPersonStat'),methodology:arr(str)}),
 ViewingReportTask:obj({status:{type:'string',enum:['PENDING','RUNNING','PARTIAL','COMPLETE','FAILED','CANCELLED']},processed:int,total:int,error:nullable('string')}),
 ViewingReportResult:obj({report:maybe('ViewingPersonnelReport'),task:maybe('ViewingReportTask'),collectionTruncated:bool}),
 CandidateState:obj({status:str,collected:int,error:nullable('string'),updatedAt:nullable('string'),samplingRule:str}),
 RecommendationReason:obj({text:str,evidenceSubjectIds:arr(int)}),
 RecommendationItem:obj({entity:ref('Entity'),score:num,contentScore:num,communityScore:num,diversityScore:num,reasons:arr('RecommendationReason')}),
 RecommendationRun:obj({modelVersion:str,mode:str,positiveWorks:int,candidateCount:int,items:arr('RecommendationItem'),methodology:arr(str)}),
 RecommendationResult:obj({result:ref('RecommendationRun'),candidateState:ref('CandidateState'),excluded:arr(int)}),
 RecommendationFeedback:obj({subjectId:{type:'integer',minimum:1,maximum:2147483647},action:{type:'string',enum:['NOT_INTERESTED','RESTORE']}}),
 WorldSubject:obj({id:int,title:str,order:int,sourceUrl:str}),
 Universe:obj({uid:str,title:str,summary:str,seriesUid:str,subjects:arr('WorldSubject')}),
 UniverseTask:obj({status:{type:'string',enum:['PENDING','RUNNING','PARTIAL','FAILED']},processed:int,error:nullable('string')}),
 UniverseNode:obj({id:str,label:str,imageUrl:str,characterType:int,subjects:arr(int),role:str,group:str,description:str}),
 RelationEvidence:obj({uid:str,active:bool,provider:str,sourceUrl:str,sourceName:str,sourceRevision:str,description:str,attribution:str,retrievedAt:str}),
 UniverseEdge:obj({uid:str,id:str,active:bool,scopeType:{type:'string',enum:['GLOBAL','SUBJECT_EXPLICIT','EPISODE_EXPLICIT']},relationType:str,label:str,directed:bool,ended:bool,spoiler:bool,source:str,target:str,evidence:str,sourceName:str,sourceUrl:str,description:str,evidences:arr('RelationEvidence')}),
 UniverseNetwork:obj({world:ref('Universe'),nodes:arr('UniverseNode'),edges:arr('UniverseEdge'),truncated:bool,notice:str,version:str,task:maybe('UniverseTask')}),
 UniverseAnalysis:obj({version:str,nodeCount:int,edgeCount:int,ranking:arr(obj({uid:str,label:str,degree:int})),relationTypes:{type:'object',additionalProperties:int},factions:arr(obj({uid:str,label:str,count:int})),path:arr(str),methodology:str}),
 ExternalIdentity:obj({provider:str,provider_id:str,entity_type:str,canonical_uid:str,status:{type:'string',enum:['CANDIDATE','REVIEWED','REVOKED']},source_url:str,source_revision:nullable('string'),match_method:str,license_label:str}),
 CommunityOverview:obj({state:ref('CandidateState'),size:int,tags:{type:'object',additionalProperties:int},formats:{type:'object',additionalProperties:int},years:{type:'object',additionalProperties:int},subjects:arr(obj({id:str,name:str,fetchedAt:str})),notice:str}),
}
for(const [name,schema] of Object.entries(schemas))doc.setIn(['components','schemas',name],schema)
doc.setIn(['components','schemas','Entity','properties','tags'],arr(str));doc.setIn(['components','schemas','Entity','properties','ratingCount'],int)
const pathParam=(name='id')=>({name,in:'path',required:true,schema:name==='id'?{type:'integer',minimum:1,maximum:2147483647}:str})
const query=(name,schema=str)=>({name,in:'query',schema})
const filters=[query('subjects',{type:'array',items:int,maxItems:10}),query('spoilers',{type:'boolean',default:false}),query('relation'),query('faction'),query('strict',{type:'boolean',default:false})]
function operation(path,method,summary,data,parameters=[],body=null,status='200',privateRead=false){const op={summary,operationId:`${method}_1021_${path.replace(/[^a-zA-Z0-9]+/g,'_')}`,'x-stage':'S20','x-implemented':true,security:[],parameters,responses:{[status]:{description:'成功；结合范围、覆盖率和任务状态使用',...(privateRead?{headers:{'Cache-Control':{schema:{type:'string',const:'no-store'}}}}:{}),content:{'application/json':{schema:obj({code:{type:'string',const:'OK'},message:str,data:typeof data==='string'?ref(data):data,requestId:str})}}},...Object.fromEntries(['400','403','404','409','429','502','503'].map(n=>[n,{$ref:'#/components/responses/Error'}]))}};if(method!=='get')op.description='保留现有CSRF机制；显式有界任务，不携带或保存私人访问令牌。';if(body)op.requestBody={required:true,content:{'application/json':{schema:ref(body)}}};doc.setIn(['paths',path,method],op)}
operation('/explore/anime/{id}/metadata','get','读取已保存的公共演职员快照，不启动远端采集','MetadataSnapshot',[pathParam()])
operation('/explore/anime/{id}/metadata/jobs','post','有界补充公共分页，单轮最多20页、90秒；失败保留旧页','MetadataSnapshot',[pathParam(),query('refresh',{type:'boolean',default:false})],null,'202')
operation('/explore/compare','get','在线作品共同项；部分资料仅给已知交集','OnlineComparison',['subjectA','subjectB'].map(name=>({...query(name,{type:'integer',minimum:1,maximum:2147483647}),required:true})))
operation('/viewer/report','get','读取当前账号和片单版本的人员报告','ViewingReportResult',[],null,'200',true)
operation('/viewer/report/jobs','post','生成已有资料报告，或补充最多100部待处理作品；单轮最多120秒','ViewingReportResult',[query('supplement',{type:'boolean',default:true})],null,'202',true)
operation('/viewer/report/jobs/current','delete','取消当前关联的报告补充；共享公共下载可在预算内完成','ViewingReportResult',[],null,'200',true)
operation('/viewer/recommendations','get','可解释内容推荐、社区基线与降级说明','RecommendationResult',[query('offset',{type:'integer',minimum:0,maximum:50,default:0}),query('algorithm',{type:'string',enum:['POPULAR','CONTENT','HYBRID'],default:'HYBRID'})],null,'200',true)
operation('/viewer/recommendation-feedback','post','当前关联的不感兴趣与撤销反馈',{type:'null'},[],'RecommendationFeedback','200',true)
operation('/viewer/recommendation-jobs','post','显式更新最多300部公共动画候选，需已关联账号','CandidateState',[],null,'202')
operation('/community/overview','get','公开候选样本统计；不以一次快照推断趋势','CommunityOverview')
operation('/universes','get','已核验世界观索引',arr('Universe'))
operation('/universes/{uid}','get','世界观及已核验作品成员','Universe',[pathParam('uid')])
operation('/universes/{uid}/network','get','有界世界观关系图；全局关系不冒充本季剧情','UniverseNetwork',[pathParam('uid'),...filters])
operation('/series/{uid}/network','get','已配置系列的有界角色子图','UniverseNetwork',[pathParam('uid'),...filters])
operation('/universes/{uid}/analysis','get','当前子图的去重连接度、关系分布与有限路径','UniverseAnalysis',[pathParam('uid'),...filters,query('fromUid'),query('toUid'),query('maxDepth',{type:'integer',minimum:1,maximum:6,default:4})])
operation('/universes/{uid}/jobs','post','显式补充世界观样本，每作首60角色、最多30个关系请求、90秒','UniverseTask',[pathParam('uid')],null,'202')
operation('/entities/{uid}/identities','get','跨来源身份映射与审核状态；候选不得直接合并',arr('ExternalIdentity'),[pathParam('uid')])
doc.setIn(['info','version'],'0.11.0')
writeFileSync(file,doc.toString({lineWidth:0,aliasDuplicateObjects:false}))
console.log('Updated 10.21 contract to 0.11.0')
