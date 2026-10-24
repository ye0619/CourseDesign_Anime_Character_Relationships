import {readFileSync,writeFileSync} from 'node:fs'
import {createRequire} from 'node:module'
const require=createRequire(new URL('../frontend/package.json',import.meta.url)),{parseDocument}=require('yaml')
const file=new URL('../docs/接口契约.yaml',import.meta.url),doc=parseDocument(readFileSync(file,'utf8'))
const str={type:'string'},integer={type:'integer'},ref=name=>({$ref:`#/components/schemas/${name}`}),arr=items=>({type:'array',items}),obj=(properties,required=Object.keys(properties))=>({type:'object',properties,required,additionalProperties:false})
const input={name:{type:'string',minLength:1,maxLength:200},locale:{type:'string',enum:['zh-Hans','zh-Hant','ja','en','und']},evidenceText:{type:'string',minLength:1,maxLength:1000}}
const schemas={IdentityNameInput:obj(input),IdentityNameReplacement:obj({expectedVersion:{type:'integer',minimum:0},reason:{type:'string',minLength:1,maxLength:500},names:{type:'array',items:ref('IdentityNameInput'),maxItems:20}}),StoredIdentityName:obj({...input,identityId:integer,ordinal:integer,searchKey:str,canonicalUid:str,sourceUrl:{type:'string',format:'uri'},sourceRevision:str,licenseLabel:str,reviewVersion:integer,reviewedAt:{type:'string',format:'date-time'},provider:str,providerId:str}),IdentityNamesDetail:obj({identity:ref('ReviewedIdentity'),names:arr(ref('StoredIdentityName')),activeForSearch:{type:'boolean',description:'只有当前审核版本绑定的名称参与检索；空列表也可能为true'}})}
for(const [name,value] of Object.entries(schemas))doc.setIn(['components','schemas',name],value)
const path='/admin/external-identities/{id}/names',id={name:'id',in:'path',required:true,schema:{type:'integer',minimum:1}}
for(const method of ['get','put']){
  const operation={operationId:method==='get'?'readIdentityNames':'replaceIdentityNames',summary:method==='get'?'读取身份别名及是否参与检索':'按身份版本替换已审核检索别名',description:'维护默认关闭；启用后须ADMIN Session，写入须CSRF，操作者取自会话。仅REVIEWED身份可保存，最多20个名称且每项有依据；空列表显式清除。版本冲突409。名称、身份版本、历史与操作日志同一MySQL事务。撤回、重开或改绑后旧名称不会自动复活。',security:[{session:[]}],'x-stage':'S23','x-implemented':true,parameters:[id],responses:{200:{description:'当前身份与名称',headers:{'Cache-Control':{schema:{type:'string',const:'no-store'}}},content:{'application/json':{schema:obj({code:{type:'string',const:'OK'},message:str,data:ref('IdentityNamesDetail'),requestId:str})}}},...Object.fromEntries(['400','401','403','404','409','503'].map(code=>[code,{$ref:'#/components/responses/Error'}]))}}
  if(method==='put')operation.requestBody={required:true,content:{'application/json':{schema:ref('IdentityNameReplacement')}}}
  doc.setIn(['paths',path,method],operation)
}
for(const name of ['CharacterGraphNode','UniverseNode','Entity']){
  doc.setIn(['components','schemas',name,'properties','aliases'],arr(str))
  doc.setIn(['components','schemas',name,'properties','nameAssertions'],arr(ref('NameAssertion')))
}
doc.setIn(['components','schemas','UniverseNode','properties','originalName'],str)
doc.setIn(['components','schemas','NameAssertion','properties','provider'],{type:'string',enum:['BANGUMI','PROJECT_POLICY','MOEGIRL','ACDB','WIKIDATA','PROJECT']})
for(const [key,value] of Object.entries({evidenceText:str,licenseLabel:str,identityVersion:integer,providerId:str}))doc.setIn(['components','schemas','NameAssertion','properties',key],value)
for(const name of ['Entity','EntityPage','CharacterRelationGraph','UniverseNetwork','OnlineNetwork','OnlineCharacterRelations','CreditPage'])doc.setIn(['components','schemas',name,'properties','nameNotice'],{type:'string',description:'审核别名读取失败时保留原来源资料，并明确提示不完整'})
for(const path of ['/characters','/people','/anime']){
  const description=doc.getIn(['paths',path,'get','description'])??''
  if(!description.includes('已审核外站别名'))doc.setIn(['paths',path,'get','description'],description+' 名称检索包括当前版本的已审核外站别名；同名结果按稳定实体编号分别分页。')
}
doc.setIn(['info','version'],'0.15.0');writeFileSync(file,String(doc));console.log('Reviewed names contract updated to 0.15.0')
