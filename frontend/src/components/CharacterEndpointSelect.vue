<script setup lang="ts">
import { shallowRef, watch } from 'vue'
import { get } from '../api/client'
import CharacterPortrait from './CharacterPortrait.vue'
import type { ConnectionCandidate } from '../types/connections'
const props = defineProps<{ label: string; subjectId?: number; initialUid?: string; revision?: string }>()
const selected = defineModel<ConnectionCandidate | null>({ default: null })
const restrictSubject=shallowRef(true),refreshKey=shallowRef(0)
let initialized=''
const query = shallowRef(''), candidates = shallowRef<ConnectionCandidate[]>([]), error = shallowRef(''), pending = shallowRef(false)
watch([query,restrictSubject,refreshKey, () => props.subjectId, () => props.initialUid, () => props.revision], async (_v, _p, cleanup) => {
  const abort = new AbortController(); let current = true; cleanup(() => { current = false; abort.abort(); clearTimeout(timer) })
  pending.value = true; error.value = ''
  const timer = setTimeout(async () => {
    try {
      const initialKey=props.initialUid+':'+props.subjectId
      const params = new URLSearchParams({ q: !selected.value&&props.initialUid&&initialized!==initialKey&&!query.value?props.initialUid:query.value })
      if (props.subjectId&&restrictSubject.value) params.set('subjectId', String(props.subjectId))
      const data = await get<ConnectionCandidate[]>(`/connections/characters?${params}`, abort.signal)
      if (!current) return
      candidates.value = data
      if (!selected.value && props.initialUid&&initialized!==initialKey){ selected.value = data.find(n => n.characterUid === props.initialUid) ?? null;if(selected.value)initialized=initialKey }
    } catch (cause) { if (current) error.value = cause instanceof Error ? cause.message : '角色读取失败' }
    finally { if (current) pending.value = false }
  }, 200)
}, { immediate: true })
function choose(candidate: ConnectionCandidate) { selected.value = candidate }
function clear(){selected.value=null;initialized=props.initialUid+':'+props.subjectId;query.value='';refreshKey.value++}
</script>
<template>
  <section class="endpoint"><h2>{{ label }}</h2>
    <div v-if="selected" class="chosen"><CharacterPortrait :node="{id:selected.characterUid,label:selected.name,imageUrl:selected.imageUrl,portraitSettings:selected.portraitSettings,portraitSubjectUid:'anime:bangumi:'+selected.subjectId,group:'',role:'',description:''}"/><div><strong>{{ selected.name }}</strong><small>{{ selected.workTitle }} · 角色</small></div><button type="button" @click="clear">重选</button></div>
    <template v-else><label v-if="subjectId" class="work-restriction"><input v-model="restrictSubject" type="checkbox">仅当前作品（{{ subjectId }}）</label><label>搜索角色名称<input v-model="query" type="search" placeholder="中文、原名或别名" maxlength="100"></label><p v-if="pending" role="status">正在读取角色…</p><p v-if="error" role="alert">{{ error }}</p>
      <div class="candidates" role="group" :aria-label="label+'候选角色'"><button v-for="n in candidates" :key="n.characterUid+':'+n.subjectId" type="button" @click="choose(n)"><strong>{{ n.name }}</strong><span>{{ n.workTitle }}</span><small v-if="n.matchedName!==n.name">匹配名称：{{ n.matchedName }}</small><small v-if="!n.hasVoice">配音资料缺失</small></button></div><p v-if="candidates.length===100">当前展示前100个候选，请继续输入名称缩小范围。</p><p v-if="!pending&&!candidates.length">当前范围没有匹配角色，请先构建范围或调整名称。</p></template>
  </section>
</template>
<style scoped>
.endpoint{min-width:0;padding:22px;border:1px solid var(--line,#dce5df);border-radius:12px;background:var(--panel,#fff)}h2{font-size:19px;margin:0 0 18px}.chosen{display:flex;gap:12px;align-items:center;min-height:100px}.chosen>div{flex:1;min-width:0}.chosen small{display:block;margin-top:7px}.chosen strong{overflow-wrap:anywhere}.candidates{display:grid;gap:6px;max-height:240px;overflow:auto;margin-top:12px}.candidates button{display:grid;text-align:left;gap:5px;background:transparent;min-height:52px}.candidates span,small,p{font-size:12px}.candidates span{color:var(--muted,#728779)}label{font-size:12px}button:focus-visible{outline:3px solid #87abb4;outline-offset:2px}@media(prefers-color-scheme:dark){.endpoint{background:#20342b;color:#dce8e1}}
</style>
