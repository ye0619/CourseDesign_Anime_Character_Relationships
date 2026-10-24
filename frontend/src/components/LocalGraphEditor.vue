<script setup lang="ts">
import { computed, reactive, shallowRef, watch } from 'vue'
import PortraitCropEditor from './PortraitCropEditor.vue'
import type { CharacterCrop } from '../types/characterEdit'
import CharacterFormFields from './CharacterFormFields.vue'
import { legacyNames,multilingualDisplay } from '../utils/multilingualNames'
import { get, mutate } from '../api/client'
import type { CharacterGraph } from '../types/characterGraph'
import type { LocalEntity, LocalGraphDocument, LocalLink } from '../types/localGraph'
import { relationDefinitions } from '../utils/characterGraph'
const props = defineProps<{ subjectId: string; document: LocalGraphDocument; graph: CharacterGraph; selectedNodeId?: string | null; selectedEdgeId?: string | null }>()
const emit = defineEmits<{ saved: [document: LocalGraphDocument]; reload: [] }>()
const open = shallowRef(false), tab = shallowRef<'ENTITY' | 'LINK' | 'BACKUP'>('ENTITY'), busy = shallowRef(false), error = shallowRef(''), message = shallowRef('')
const entity = reactive<LocalEntity>(newEntity()), link = reactive<LocalLink>(newLink())
const backups = shallowRef<{id:string;createdAt:string}[]>([])
const endpoint = computed(() => `/explore/anime/${props.subjectId}/local-graph`)
const characters = computed(() => props.graph.nodes.filter(node => node.characterType !== 4))
const organizations = computed(() => props.document.nodes.filter(node => node.kind === 'ORGANIZATION'))
const types = computed(() => relationDefinitions.filter(type => !type.legacy && type.code !== 'MEMBER'))
const sameName = computed(() => props.graph.nodes.filter(node => node.label.trim() === entity.name.trim() && node.id !== entity.id))
const selectedLocalNode = computed(() => props.document.nodes.find(node => node.id === props.selectedNodeId))
const selectedLocalLink = computed(() => props.document.edges.find(edge => edge.id === props.selectedEdgeId))
const isMember = computed(() => link.relationType === 'MEMBER')
function newEntity(): LocalEntity { return { id: '', kind: 'CHARACTER', name: '', imageUrl: '', aliases: [], gender: '', description: '', notes: '', organizationType: '', names:[{id:'primary',text:'',locale:'und',kind:'PRIMARY',source:'LOCAL',reviewStatus:'MANUAL'}], preferredNameId:null } }
function newLink(): LocalLink { return { id: '', source: '', target: '', relationType: 'FRIEND', label: '朋友', description: '', evidence: '', spoiler: false, directed: false, notes: '' } }
const localCrop=computed<CharacterCrop|null>({get:()=>entity.portraits?.[props.document.workUid]||null,set:value=>{entity.portraits=value?{[props.document.workUid]:value}:{}}})
const aliasText = computed({get: () => entity.aliases.join('、'), set: (value:string) => {entity.aliases = value.split(/[、,，\n]/).map(text => text.trim()).filter(Boolean)}})
function addEntity(kind: LocalEntity['kind']) { Object.assign(entity,newEntity(),{kind}); tab.value='ENTITY';open.value=true;error.value='' }
function editEntity(node: LocalEntity) { Object.assign(entity,structuredClone(node),{names:node.names?structuredClone(node.names):legacyNames(node.name,node.aliases),preferredNameId:node.preferredNameId??null});tab.value='ENTITY';open.value=true;error.value='' }
function editLink(edge: LocalLink) { Object.assign(link,structuredClone(edge));tab.value='LINK';open.value=true;error.value='' }
function addLink() { Object.assign(link,newLink(),{source:props.selectedNodeId && characters.value.some(node=>node.id===props.selectedNodeId)?props.selectedNodeId:''});tab.value='LINK';open.value=true;error.value='' }
function updateType() { const type=relationDefinitions.find(type=>type.code===link.relationType);link.label=type?.label || '成员';link.directed=link.relationType==='MEMBER'||!!type?.directed;link.target='' }
watch(()=>link.label,label=>{if(['父亲','母亲','父','母','老师','教师','导师','学生','徒弟','师父','师傅','上司','下属','儿子','女儿'].includes(label.trim()))link.directed=true})
watch(()=>props.subjectId,()=>{open.value=false;error.value='';message.value='';backups.value=[]})
async function save(next:LocalGraphDocument) {
  if(busy.value)return
  const owner=endpoint.value;busy.value=true;error.value='';message.value=''
  try {const result=await mutate<LocalGraphDocument>(owner,'PUT',next);if(owner===endpoint.value){emit('saved',result);message.value='已保存到本地图数据库';return true}}
  catch(cause){if(owner===endpoint.value)error.value=cause instanceof Error?cause.message:'保存失败'}finally{if(owner===endpoint.value)busy.value=false}
  return false
}
async function saveEntity() {
  const value=JSON.parse(JSON.stringify({...entity,aliases:[...entity.aliases],id:entity.id || 'local:entity:'+crypto.randomUUID(),names:entity.names?.map(n=>({...n})),preferredNameId:entity.preferredNameId,name:entity.names?.length?multilingualDisplay(entity.names,entity.preferredNameId):entity.name.trim()})) as LocalEntity
  const nodes=props.document.nodes.filter(node=>node.id!==value.id).concat(value)
  if(await save({...props.document,schemaVersion:2,nodes}))Object.assign(entity,newEntity())
}
async function saveLink() {
  const value=structuredClone({...link,id:link.id || 'local:link:'+crypto.randomUUID(),label:link.label.trim()})
  if(await save({...props.document,edges:props.document.edges.filter(edge=>edge.id!==value.id).concat(value)}))Object.assign(link,newLink())
}
async function removeEntity(node:LocalEntity) {
  const count=props.document.edges.filter(edge=>edge.source===node.id||edge.target===node.id).length
  if(!window.confirm(`删除“${node.name}”？同时移除 ${count} 条本地关联。可从自动备份恢复。`))return
  await save({...props.document,nodes:props.document.nodes.filter(item=>item.id!==node.id),edges:props.document.edges.filter(edge=>edge.source!==node.id&&edge.target!==node.id)})
}
async function removeLink(edge:LocalLink) {if(window.confirm(`删除本地关系“${edge.label}”？`))await save({...props.document,edges:props.document.edges.filter(item=>item.id!==edge.id)})}
async function upload(event:Event) {
  const file=(event.target as HTMLInputElement).files?.[0];if(!file)return
  if(file.size>512*1024||!['image/png','image/jpeg','image/webp'].includes(file.type)){error.value='请选择512KB以内的 PNG、JPEG 或 WebP 图片';return}
  const owner=endpoint.value, draftId=entity.id
  const reader=new FileReader();reader.onload=()=>{if(owner===endpoint.value && entity.id===draftId)entity.imageUrl=String(reader.result)};reader.onerror=()=>{error.value='图片读取失败'};reader.readAsDataURL(file)
}
function download(document:LocalGraphDocument) {
  const url=URL.createObjectURL(new Blob([JSON.stringify(document,null,2)],{type:'application/json'})),a=window.document.createElement('a');a.href=url;a.download=`映间-本地图谱-${props.subjectId}-v${document.version}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
}
async function importFile(event:Event) {
  const file=(event.target as HTMLInputElement).files?.[0];if(!file)return
  try {if(file.size>8*1024*1024)throw new Error('备份不得超过8MB');const next=JSON.parse(await file.text()) as LocalGraphDocument
    if(next.workUid!==props.document.workUid||![1,2].includes(next.schemaVersion)||!Array.isArray(next.nodes)||!Array.isArray(next.edges))throw new Error('请选择当前作品的本地图谱备份')
    if(window.confirm(`将当前本地图谱替换为备份中的 ${next.nodes.length} 个实体、${next.edges.length} 条关系？当前版本会先自动备份。`))await save({...next,schemaVersion:2,version:props.document.version})
  }catch(cause){error.value=cause instanceof Error?cause.message:'备份格式错误'}finally{(event.target as HTMLInputElement).value=''}
}
async function loadBackups() {tab.value='BACKUP';open.value=true;error.value='';try{backups.value=await get(endpoint.value+'/backups')}catch(cause){error.value=cause instanceof Error?cause.message:'备份读取失败'}}
async function restore(id:string) {try{const backup=await get<LocalGraphDocument>(endpoint.value+'/backups/'+id);if(window.confirm(`恢复该版本的 ${backup.nodes.length} 个实体、${backup.edges.length} 条关系？`))await save({...backup,schemaVersion:2,version:props.document.version})}catch(cause){error.value=cause instanceof Error?cause.message:'恢复失败'}}
const nameOf = (id:string) => props.graph.nodes.find(node=>node.id===id)?.label || id
</script>
<template>
  <section class="local-editor" aria-label="本地图谱编辑">
    <div class="editor-actions"><strong>补充这个世界</strong><button :disabled="busy" @click="addEntity('CHARACTER')">添加角色</button><button :disabled="busy" @click="addEntity('ORGANIZATION')">添加组织</button><button :disabled="busy" @click="addLink">添加关系</button><button v-if="selectedLocalNode" @click="editEntity(selectedLocalNode)">编辑选中实体</button><button v-if="selectedLocalLink" @click="editLink(selectedLocalLink)">编辑选中关系</button><button @click="download(document)">导出备份</button><button @click="loadBackups">恢复 / 管理</button><button v-if="open" @click="open=false">收起编辑器</button></div>
    <p class="boundary">本地添加 · 当前服务实例共享，其他使用本服务的人也可查看；不上传 Bangumi。官方资料保持独立。版本 {{ document.version }}。</p>
    <p v-if="error" class="feedback error" role="alert">{{ error }} <button :disabled="busy" @click="emit('reload')">重新读取</button></p><p v-if="message" role="status">{{ message }}</p>
    <div v-if="open" class="editor-body"><nav class="editor-tabs"><button :aria-pressed="tab==='ENTITY'" @click="tab='ENTITY'">角色 / 组织</button><button :aria-pressed="tab==='LINK'" @click="tab='LINK'">人物关系 / 成员</button><button :aria-pressed="tab==='BACKUP'" @click="loadBackups">备份与恢复</button></nav>
      <form v-if="tab==='ENTITY'" class="editor-form" @submit.prevent="saveEntity"><h3>{{ entity.id ? '编辑本地实体' : '添加本地实体' }}</h3><div class="field-pair"><label>实体类型<select v-model="entity.kind" :disabled="!!entity.id"><option value="CHARACTER">角色</option><option value="ORGANIZATION">作品内组织</option></select></label><label>所属作品<input :value="graph.work.title" disabled></label></div><label v-if="entity.kind==='ORGANIZATION'">组织类型<input v-model="entity.organizationType" maxlength="60" placeholder="例如社团、乐队、公会"></label><CharacterFormFields v-model="entity" :hide-gender="entity.kind==='ORGANIZATION'"/><PortraitCropEditor v-if="entity.imageUrl" v-model="localCrop" :uid="entity.id||'draft'" :url="entity.imageUrl"/><div class="editor-actions"><button :disabled="busy" type="submit">{{ busy ? '正在保存…' : '保存实体' }}</button><button type="button" @click="Object.assign(entity,newEntity())">清空表单</button></div></form>
      <form v-else-if="tab==='LINK'" class="editor-form" @submit.prevent="saveLink"><h3>{{ link.id ? '编辑本地关系' : '添加本地关系' }}</h3><label>关系类型<select v-model="link.relationType" @change="updateType"><option v-for="type in types" :key="type.code" :value="type.code">{{ type.label }}</option><option value="MEMBER">组织成员</option></select></label><div class="field-pair"><label>起点角色<select v-model="link.source" required><option value="">选择角色</option><option v-for="node in characters" :key="node.id" :value="node.id">{{ node.label }}{{ node.dataSource==='LOCAL'?'（本地）':'' }}</option></select></label><label>{{ isMember?'所属组织':'终点角色' }}<select v-model="link.target" required><option value="">请选择</option><template v-if="isMember"><option v-for="node in organizations" :key="node.id" :value="node.id">{{ node.name }}</option></template><template v-else><option v-for="node in characters" :key="node.id" :value="node.id" :disabled="node.id===link.source">{{ node.label }}{{ node.dataSource==='LOCAL'?'（本地）':'' }}</option></template></select></label></div><label>{{ isMember?'组织身份（如队长、成员）':'关系名称 / 自定义名称' }}<input v-model="link.label" required maxlength="60" list="common-relations"><datalist id="common-relations"><option v-for="name in ['朋友','家人','兄弟姐妹','师徒','恋人','同学','同事','队友','敌人','父亲','老师','其他']" :key="name">{{ name }}</option></datalist></label><label class="inline-field"><input v-model="link.directed" type="checkbox" :disabled="isMember">有方向（取消为双向 / 无向）</label><p v-if="link.directed" class="boundary">{{ nameOf(link.source) }} 是 {{ nameOf(link.target) }} 的“{{ link.label }}”。请核对父亲、老师等身份的方向。</p><label>关系说明<textarea v-model="link.description" rows="2" maxlength="2000"></textarea></label><label>来源 / 证据<textarea v-model="link.evidence" rows="2" maxlength="2000" placeholder="例如第几集、原作章节或资料链接；不填写将标为未提供外部证据"></textarea></label><label class="inline-field"><input v-model="link.spoiler" type="checkbox">涉及剧透（默认隐藏）</label><label>备注<textarea v-model="link.notes" rows="2" maxlength="2000"></textarea></label><button :disabled="busy" type="submit">{{ busy ? '正在保存…' : '保存关系' }}</button></form>
      <div v-else class="backup-panel"><h3>本地扩展备份</h3><p>每次修改前自动备份，保留本作最近20份。仅恢复本地扩展，不改变官方资料。</p><label>导入本作 JSON 备份（最大8MB）<input type="file" accept="application/json,.json" :disabled="busy" @change="importFile"></label><p v-if="!backups.length">暂无自动备份。</p><div v-for="backup in backups" :key="backup.id" class="editor-actions"><span>{{ new Date(backup.createdAt).toLocaleString('zh-CN') }}</span><button :disabled="busy" @click="restore(backup.id)">恢复此备份</button></div></div>
      <details class="local-inventory"><summary>管理本地实体（{{ document.nodes.length }}）与关系（{{ document.edges.length }}）</summary><div v-for="node in document.nodes" :key="node.id" class="inventory-row"><span>{{ node.name }} · {{ node.kind==='ORGANIZATION'?'组织':'角色' }}</span><button :disabled="busy" @click="editEntity(node)">编辑</button><button :disabled="busy" @click="removeEntity(node)">删除</button></div><div v-for="edge in document.edges" :key="edge.id" class="inventory-row"><span>{{ nameOf(edge.source) }} {{ edge.directed?'→':'—' }} {{ nameOf(edge.target) }} · {{ edge.label }}</span><button :disabled="busy" @click="editLink(edge)">编辑</button><button :disabled="busy" @click="removeLink(edge)">删除 / 移除成员</button></div><p v-if="!document.nodes.length&&!document.edges.length">暂无本地扩展，从添加角色或关系开始。</p></details>
    </div>
  </section>
</template>
<style scoped>
.local-editor{padding:18px 20px;border-bottom:1px solid #dce5df;background:#f2f7f3;color:#40594f}.editor-actions{display:flex;flex-wrap:wrap;align-items:center;gap:8px}.editor-actions strong{margin-right:auto;font-size:15px}.local-editor button{font-size:12px;min-height:36px}.boundary{font-size:11px;line-height:1.8;color:#647d70;margin:12px 0 0}.editor-body{margin-top:18px;max-width:100%}.editor-tabs{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:18px}.editor-tabs [aria-pressed=true]{background:#40594f;color:white}.editor-form{max-width:720px;display:grid;gap:14px}.editor-form h3{margin:0}.editor-form label{font-size:12px;display:grid;gap:7px}.field-pair{display:grid;grid-template-columns:1fr 1fr;gap:15px}.editor-form .inline-field{display:flex;align-items:center}.inline-field input{width:auto}.editor-form textarea{width:100%;padding:10px;border:1px solid #ccd9d1;border-radius:5px;background:#fcfdf9;color:inherit;font:inherit}.editor-form button[type=submit]{justify-self:start;background:#40594f;color:#fff}.local-inventory{margin-top:22px;font-size:12px}.local-inventory summary{cursor:pointer;min-height:36px}.inventory-row{display:flex;gap:8px;align-items:center;border-bottom:1px solid #dce5df;padding:10px 0}.inventory-row>span{margin-right:auto;overflow-wrap:anywhere}.backup-panel{font-size:12px;line-height:1.8}.backup-panel .editor-actions{margin-top:12px}@media(max-width:600px){.field-pair{grid-template-columns:1fr}.inventory-row{flex-wrap:wrap}}@media(prefers-color-scheme:dark){.local-editor{background:#1f302a;color:#d8e5de}.boundary{color:#adc2b6}.editor-form textarea{background:#18251f;color:#e1e9e3}}
</style>
