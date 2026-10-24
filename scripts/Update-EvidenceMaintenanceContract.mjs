import {readFileSync,writeFileSync} from 'node:fs'
import {createRequire} from 'node:module'
const require=createRequire(new URL('../frontend/package.json',import.meta.url)),{parseDocument}=require('yaml')
const file=new URL('../docs/接口契约.yaml',import.meta.url),doc=parseDocument(readFileSync(file,'utf8'))
const str={type:'string'},num={type:'integer'},ref=name=>({$ref:`#/components/schemas/${name}`}),array=items=>({type:'array',items}),nullable=type=>({type:[type,'null']}),object=properties=>({type:'object',properties,required:Object.keys(properties),additionalProperties:false})
const schemas={
 EvidenceImportChange:object({assertionUid:str,evidenceUid:str,source:str,sourceLabel:str,target:str,targetLabel:str,relation:str,scope:{type:'string',enum:['GLOBAL','SUBJECT_EXPLICIT']},subjectIds:array(num),sourceUrl:{type:'string',format:'uri'},sourceRevision:str,sourceField:str,rawRelation:str,action:{type:'string',enum:['ADD_ASSERTION','ADD_EVIDENCE','REUSE']}}),
 EvidenceImportPreview:object({previewId:{type:'string',format:'uuid'},batchId:str,packageHash:str,baseHash:str,provider:str,sourceVersion:str,licenseLabel:str,expiresAt:{type:'string',format:'date-time'},canApply:{type:'boolean'},blockers:array(str),changes:array(ref('EvidenceImportChange'))}),
 EvidenceImportBatch:object({uid:str,packageHash:str,worldUid:str,status:{type:'string',enum:['APPLIED','DISABLED']},createdBy:num,createdAt:{type:'string',format:'date-time'},provider:str,sourceVersion:str,evidenceCount:num,disabledAt:nullable('string'),disabledBy:nullable('integer'),disableReason:nullable('string')}),
 EvidenceImportCommit:object({previewId:{type:'string',format:'uuid',maxLength:36}}),
 EvidenceMaintenanceReason:object({reason:{type:'string',minLength:1,maxLength:500}}),
 EvidenceImportInputFact:object({source:str,target:str,relationType:str,ended:{type:'boolean'},spoiler:{type:'boolean'},scopeType:{type:'string',enum:['GLOBAL','SUBJECT_EXPLICIT']},subjectIds:{type:'array',items:{type:'integer',minimum:1,maximum:2147483647},maxItems:10},reviewStatus:{type:'string',const:'REVIEWED'},sourceUrl:{type:'string',format:'uri',maxLength:1000},sourceRevision:{type:'string',minLength:1,maxLength:200},sourceField:{type:'string',minLength:1,maxLength:200},rawRelation:{type:'string',minLength:1,maxLength:2000},sourceName:{type:'string',minLength:1,maxLength:200},description:{type:'string',minLength:1,maxLength:2000},attribution:{type:'string',minLength:1,maxLength:500},matchMethod:{type:'string',enum:['MANUAL','SOURCE_ID']}}),
 EvidenceImportPackage:object({schemaVersion:{type:'integer',const:1},batchId:{type:'string',pattern:'^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'},worldUid:str,provider:{type:'string',enum:['BANGUMI','MOEGIRL','ACDB','PROJECT']},sourceVersion:{type:'string',minLength:1,maxLength:100},licenseLabel:{type:'string',minLength:1,maxLength:200},retrievedAt:{type:'string',format:'date-time'},facts:{type:'array',items:ref('EvidenceImportInputFact'),minItems:1,maxItems:200}}),
}
for(const [name,schema]of Object.entries(schemas))doc.setIn(['components','schemas',name],schema)
for(const field of ['licenseLabel','sourceField','rawRelation','matchMethod'])doc.setIn(['components','schemas','RelationEvidence','properties',field],str)
doc.setIn(['components','schemas','UniverseEdge','properties','subjectIds'],array(num))
const pathParam=name=>({name,in:'path',required:true,schema:str})
function operation(path,method,name,summary,data,parameters=[],body){
 const op={operationId:name,summary,description:'默认维护开关关闭时不可访问；启用后仍须ADMIN Session及CSRF。预览仅存当前进程10分钟，最多32个，绑定维护账号；提交与批次证据同事务，状态变化返回409。','x-stage':'S21','x-implemented':true,security:[{session:[]}],parameters,responses:{'200':{description:'操作结果',headers:{'Cache-Control':{schema:{type:'string',const:'no-store'}}},content:{'application/json':{schema:object({code:{type:'string',const:'OK'},message:str,data,requestId:str})}}},...Object.fromEntries(['400','401','403','404','409','413','503'].map(code=>[code,{$ref:'#/components/responses/Error'}]))}}
 if(body)op.requestBody={required:true,content:{'application/json':{schema:ref(body)}}}
 doc.setIn(['paths',path,method],op)
}
operation('/admin/evidence-imports/preview','post','previewReviewedEvidence','预览最多512KB/200条已审核关系证据',ref('EvidenceImportPreview'))
doc.setIn(['paths','/admin/evidence-imports/preview','post','requestBody'],{required:true,content:{'multipart/form-data':{schema:object({file:{type:'string',format:'binary',description:'UTF-8 JSON EvidenceImportPackage，最多512KB'}})}}})
operation('/admin/evidence-imports','post','commitReviewedEvidence','提交当前维护账号的有效预览；同批同内容幂等',ref('EvidenceImportBatch'),[],'EvidenceImportCommit')
operation('/admin/evidence-imports','get','listEvidenceBatches','读取最近的证据维护批次',array(ref('EvidenceImportBatch')),[{name:'limit',in:'query',schema:{type:'integer',minimum:1,maximum:100,default:30}}])
operation('/admin/evidence-imports/{batchId}/disable','post','disableEvidenceBatch','停用批次；共享的其他有效证明保留',ref('EvidenceImportBatch'),[pathParam('batchId')],'EvidenceMaintenanceReason')
operation('/admin/relationship-assertions/{uid}/revoke','post','revokeRelationshipAssertion','撤回整个关系断言，刷新与重新导入不能恢复',object({uid:str,active:{type:'boolean',const:false}}),[pathParam('uid')],'EvidenceMaintenanceReason')
const params=doc.getIn(['paths','/universes/{uid}/analysis','get','parameters']).toJSON()
if(!params.some(p=>p.name==='version'))params.push({name:'version',in:'query',description:'指定图谱版本；不一致返回409，避免统计与画布混用版本',schema:str})
doc.setIn(['paths','/universes/{uid}/analysis','get','parameters'],params)
doc.setIn(['info','version'],'0.12.0')
writeFileSync(file,doc.toString({lineWidth:0,aliasDuplicateObjects:false}))
console.log('Evidence maintenance contract updated to 0.12.0')
