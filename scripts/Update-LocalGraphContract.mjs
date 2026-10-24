import { readFileSync,writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require=createRequire(new URL('../frontend/package.json',import.meta.url));const {parse,stringify}=require('yaml')
const path=new URL('../docs/接口契约.yaml',import.meta.url),api=parse(readFileSync(path,'utf8')),schemas=api.components.schemas
const str={type:'string'},num={type:'integer'},boolean={type:'boolean'},nullable={type:['number','null']}
const ref=name=>({$ref:'#/components/schemas/'+name}),arr=items=>({type:'array',items}),obj=properties=>({type:'object',properties,required:Object.keys(properties),additionalProperties:false})
const workList=arr(ref('ViewingRecord'))
schemas.ProfileScores=obj({ratedCount:num,averageRating:nullable,distribution:{type:'object',additionalProperties:num}})
schemas.ProfileVoiceRole=obj({subjectId:num,title:str,personalRating:num,characterUid:str,characterName:str,imageUrl:{type:['string','null']}})
schemas.ProfileVoice=obj({uid:str,name:str,imageUrl:{type:['string','null']},workCount:num,characterCount:num,scores:ref('ProfileScores'),roles:arr(ref('ProfileVoiceRole'))})
schemas.ProfileCompany=obj({uid:str,name:str,imageUrl:{type:['string','null']},completed:num,allCount:num,scores:ref('ProfileScores'),statuses:{type:'object',additionalProperties:num},representatives:workList,works:workList})
schemas.ProfileGroup=obj({id:str,label:str,count:num,works:workList})
schemas.ProfileBehavior=obj({status:num,label:str,count:num,share:{type:'number'},scores:ref('ProfileScores'),tagged:num,companyCovered:num,tags:arr(ref('ProfileGroup')),formats:arr(ref('ProfileGroup')),companies:arr(ref('ProfileGroup')),works:workList})
for(const [name,schema] of [['voices','ProfileVoice'],['companies','ProfileCompany'],['behaviors','ProfileBehavior']])schemas.ViewingPersonnelReport.properties[name]={anyOf:[arr(ref(schema)),{type:'null'}]}
schemas.ViewingPersonnelReport.description='viewing-report/4: personnel and voice roles use completed works; companies show completed ratings plus all statuses; behavior analysis uses all synced states. Explicit organization type and animation-production duty required. Unrated works are excluded from means. v1-v3 remain readable.'
const text=max=>({type:['string','null'],maxLength:max})
schemas.LocalGraphEntity=obj({id:{type:'string',pattern:'^local:entity:[0-9a-f-]{36}$'},kind:{type:'string',enum:['CHARACTER','ORGANIZATION']},name:{type:'string',minLength:1,maxLength:100},imageUrl:text(720000),aliases:{...arr({type:'string',minLength:1,maxLength:100}),maxItems:20},gender:text(40),description:text(4000),notes:text(2000),organizationType:text(60)})
schemas.LocalGraphLink=obj({id:{type:'string',pattern:'^local:link:[0-9a-f-]{36}$'},source:str,target:str,relationType:{type:'string',description:'Existing relation code; custom labels use OTHER, organization membership uses MEMBER.'},label:{type:'string',minLength:1,maxLength:60},description:text(2000),evidence:text(2000),spoiler:boolean,directed:boolean,notes:text(2000)})
schemas.LocalGraphDocument=obj({version:{type:'integer',minimum:0},schemaVersion:{type:'integer',const:1},workUid:{type:'string',pattern:'^anime:bangumi:[1-9][0-9]*$'},nodes:{...arr(ref('LocalGraphEntity')),maxItems:300},edges:{...arr(ref('LocalGraphLink')),maxItems:800}})
schemas.LocalGraphDocument.description='Current service shared local extension; no browser-private or per-account ownership. UUID identities, independent labels, atomic replace with optimistic version; every write retains a pre-change backup (latest20). Images PNG/JPEG/WebP data URLs up to512KB each; aggregate8MB. Membership is directed character->local organization. No official edits.'
schemas.LocalGraphBackup=obj({id:str,createdAt:{type:'string',format:'date-time'}})
const response=schema=>({'200':{description:'成功',content:{'application/json':{schema:obj({code:str,message:str,data:schema,requestId:str})}}}})
const parameters=[{name:'id',in:'path',required:true,schema:{type:'integer',minimum:1,maximum:2147483647}}]
const operation=(id,summary,schema,extra={})=>({operationId:id,summary,'x-stage':'S18','x-implemented':true,security:[],parameters:structuredClone(parameters),responses:response(schema),...extra})
const route='/explore/anime/{id}/local-graph'
api.paths[route]={get:operation('get_local_graph','读取本作本地扩展；无数据返回版本0空文档',ref('LocalGraphDocument')),put:operation('replace_local_graph','原子保存本地实体、组织成员和关系；本机+CSRF，无需注册',ref('LocalGraphDocument'),{requestBody:{required:true,content:{'application/json':{schema:ref('LocalGraphDocument')}}},description:'Uses current version, 409 on concurrent writes. Rejects self/duplicate/foreign-work links and invalid images. Restore by submitting current version with a backup nodes/edges payload. Automatic pre-change backups remain isolated from official caches.'})}
for(const status of ['400','403','409','503'])api.paths[route].put.responses[status]={description:({'400':'字段、图片、范围或重复关系校验失败','403':'缺少CSRF令牌','409':'版本冲突，需要重新读取','503':'数据库不可用'})[status]}
api.paths[route+'/backups']={get:operation('list_local_graph_backups','列出本作最近20份修改前自动备份',arr(ref('LocalGraphBackup')))}
api.paths[route+'/backups/{backup}']={get:operation('get_local_graph_backup','读取历史备份；恢复需携带最新版本PUT',ref('LocalGraphDocument'))}
api.paths[route+'/backups/{backup}'].get.parameters.push({name:'backup',in:'path',required:true,schema:{type:'string'}})
api.info.version='0.20.0';api.info.description=api.info.description.replaceAll(' 本地图谱独立持久化、备份恢复和画像v4扩展。','')+' 本地图谱独立持久化、备份恢复和画像v4扩展。'
writeFileSync(path,stringify(api,{lineWidth:120}))
