<script setup lang="ts">
import { computed, onScopeDispose, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AnimeSearchSelect from '../components/AnimeSearchSelect.vue'
import OnlineComparisonResults from '../components/OnlineComparisonResults.vue'
import LocalComparison from '../components/LocalComparison.vue'
import { get, mutate } from '../api/client'
import type { Entity } from '../types/catalog'
import type { Comparison, MetadataSnapshot } from '../types/compare'
const route=useRoute(), router=useRouter()
const a=shallowRef(''), b=shallowRef(''), queryA=shallowRef(''), queryB=shallowRef('')
const result=shallowRef<Comparison>(), pending=shallowRef(false), error=shallowRef(''), preparing=shallowRef(false), showLocal=shallowRef(false)
let controller: AbortController | undefined, timer: ReturnType<typeof setTimeout> | undefined, version=0
const valid=computed(()=>/^[1-9]\d*$/.test(a.value)&&/^[1-9]\d*$/.test(b.value))
async function load() {
  controller?.abort();clearTimeout(timer);const current=new AbortController();controller=current
  if(!valid.value){result.value=undefined;return}
  pending.value=true;error.value=''
  try {
    const response=await get<Comparison>(`/explore/compare?subjectA=${a.value}&subjectB=${b.value}`,current.signal)
    if(current.signal.aborted)return
    result.value=response
    const running=[response.a,response.b].some(side=>side.metadata.job && ['PENDING','RUNNING'].includes(side.metadata.job.status))
    if(running)timer=setTimeout(load,1800)
  } catch(cause) {if(!current.signal.aborted)error.value=cause instanceof Error?cause.message:'对比读取失败'}
  finally{if(!current.signal.aborted)pending.value=false}
}
function updateRoute() {router.replace({path:'/compare',query:{...(a.value?{subjectA:a.value}:{}),...(b.value?{subjectB:b.value}:{})}})}
function edit(side:'A'|'B') {version++;controller?.abort();clearTimeout(timer);result.value=undefined;pending.value=false;preparing.value=false;if(side==='A')a.value='';else b.value='';updateRoute()}
function choose(side:'A'|'B',entity:Entity) {if(entity.source!=='bangumi')return; if(side==='A'){a.value=entity.sourceId}else{b.value=entity.sourceId};result.value=undefined;updateRoute()}
function swap(){[a.value,b.value]=[b.value,a.value];[queryA.value,queryB.value]=[queryB.value,queryA.value];result.value=undefined;updateRoute()}
async function prepare(refresh=false) {
  const ids=[...new Set([a.value,b.value])], current=version;preparing.value=true;error.value=''
  try {await Promise.all(ids.map(id=>mutate<MetadataSnapshot>(`/explore/anime/${id}/metadata/jobs?refresh=${refresh}`,'POST')));if(current===version)await load()}
  catch(cause){if(current===version)error.value=cause instanceof Error?cause.message:'资料补充暂不可用'}
  finally{if(current===version)preparing.value=false}
}
watch(()=>[route.query.subjectA,route.query.subjectB],()=>{version++;a.value=String(route.query.subjectA||'');b.value=String(route.query.subjectB||'');preparing.value=false;void load()},{immediate:true})
watch(result,value=>{if(value){queryA.value=value.a.entity.nameCn||value.a.entity.name||queryA.value;queryB.value=value.b.entity.nameCn||value.b.entity.name||queryB.value}})
onScopeDispose(()=>{version++;controller?.abort();clearTimeout(timer)})
</script>
<template>
  <section class="page-intro"><h1>两部作品，有什么联系？</h1><p>从在线作品中选择，比较共同人员、声优、角色与标签。资料补充后自动更新结果。</p></section>
  <form class="toolbar comparison-search" @submit.prevent="load"><AnimeSearchSelect v-model="queryA" class="grow" label="作品 A" @update:model-value="edit('A')" @select="choose('A',$event)" @search="router.push({path:'/anime',query:{keyword:queryA}})"/><AnimeSearchSelect v-model="queryB" class="grow" label="作品 B" @update:model-value="edit('B')" @select="choose('B',$event)" @search="router.push({path:'/anime',query:{keyword:queryB}})"/><button type="button" :disabled="!valid" @click="swap">交换</button><button class="primary" :disabled="!valid||pending">比较作品</button></form>
  <p v-if="!valid" class="notice">从下拉候选中选定两部动画，即可比较；不需要先导入本地。</p>
  <p v-if="error" class="feedback error" role="alert">{{ error }} <button @click="load">重试读取</button></p>
  <template v-if="valid"><div class="actions"><button :disabled="preparing" @click="prepare()">{{ preparing?'正在请求…':'补充 / 继续读取两部作品资料' }}</button><button :disabled="preparing" @click="prepare(true)">刷新来源资料</button></div><p class="muted">每轮有界补充；若来源失败，已保存的分页仍可比较。首次读取需主动补充资料。</p></template>
  <p><RouterLink :to="{path:'/connections',query:{...(a?{fromSubject:a}:{}),...(b?{toSubject:b}:{})}}">从这两部作品选择角色，探索配音与剧情联系</RouterLink></p><p v-if="pending" role="status" class="feedback">正在核对共同项…</p>
  <template v-if="result"><p v-for="side in [result.a,result.b].filter(s=>s.metadata.job)" :key="side.subjectId" class="notice" role="status">作品 {{ side.entity.nameCn||side.entity.name||side.subjectId }}：{{ ({PENDING:'等待补充',RUNNING:'补充中',COMPLETE:'已读完',PARTIAL:'本轮已达上限，可继续补充',FAILED:'补充失败'} as Record<string,string>)[side.metadata.job!.status] }} · 本轮 {{ side.metadata.job!.processed }} 页 {{ side.metadata.job!.error }}</p><OnlineComparisonResults :result="result"/></template>
  <details class="local-comparison" @toggle="showLocal=($event.target as HTMLDetailsElement).open"><summary>本地档案共同项</summary><p class="notice">以下仅核对已保存作品的共同项，覆盖范围与上方在线资料不同。</p><LocalComparison v-if="showLocal"/></details>
</template>
<style scoped>.comparison-search{align-items:end}.local-comparison{margin:30px 0;border-top:1px solid #dce6d1;padding-top:20px}.local-comparison>summary{cursor:pointer;padding:12px;font-size:14px}.actions{flex-wrap:wrap}</style>
