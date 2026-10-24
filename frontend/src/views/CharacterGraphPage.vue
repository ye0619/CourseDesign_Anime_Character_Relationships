<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useExploreRequest } from '../composables/useExploreRequest'
import type { GraphCatalog } from '../types/characterGraph'
import WorkGraphPanel from '../components/WorkGraphPanel.vue'
const route = useRoute(), router = useRouter()
const source = computed(() => route.query.dataSource === 'ONLINE' ? 'ONLINE' : route.query.dataSource === 'DEMO' ? 'DEMO' : 'LOCAL')
const catalogUrl = computed(() => `/character-graph/works?dataSource=${source.value === 'DEMO' ? 'DEMO' : 'NEO4J'}`)
const { data: catalog, pending, error, retry } = useExploreRequest<GraphCatalog>(catalogUrl)
const uid = computed(() => String(route.query.animeUid || catalog.value?.items[0]?.uid || ''))
const picked = shallowRef('')
watch(uid, value => { picked.value = value }, { immediate: true })
const workTitle = computed(() => catalog.value?.items.find(work => work.uid === uid.value)?.title || '角色关系图谱')
const detailsLocation = computed(() => /^anime:bangumi:[1-9]\d*$/.test(uid.value)
  ? { path: '/explore/anime/' + uid.value.split(':').at(-1), query: { source: source.value } }
  : { path: '/anime/' + encodeURIComponent(uid.value) })
function selectSource(event: Event) {
  const next = (event.target as HTMLSelectElement).value
  void router.push({name:'character-graph',query:{dataSource:next === 'LOCAL' ? 'NEO4J' : next,...(next !== 'DEMO' && source.value !== 'DEMO' ? {animeUid:uid.value} : {})}})
}
function selectWork() { void router.push({name:'character-graph',query:{dataSource:source.value === 'LOCAL' ? 'NEO4J' : source.value,animeUid:picked.value}}) }
</script>
<template><div class="character-graph-page">
  <header class="graph-page-heading"><div><p class="eyebrow">CHARACTER ATLAS / 角色关系图谱</p><h1>相遇之后，<span>故事相连。</span></h1><p class="lead">从角色出发，探索彼此的身份、羁绊与立场。</p></div></header>
  <section class="graph-controls" aria-label="选择作品与来源"><label>数据来源<select :value="source" @change="selectSource"><option value="LOCAL">本地已保存关系</option><option value="ONLINE">Bangumi 在线角色关系</option><option value="DEMO">虚构演示</option></select></label><label class="work-picker">动画作品<select v-model="picked" :disabled="pending" @change="selectWork"><option v-if="uid && !catalog?.items.some(work => work.uid === uid)" :value="uid">{{ uid }}</option><option v-for="work in catalog?.items" :key="work.uid" :value="work.uid">{{ work.title }}</option></select></label></section>
  <p v-if="error" class="feedback error" role="alert">{{ error }} <button @click="retry">重试</button></p><p v-if="pending && !uid" role="status">正在读取作品…</p>
  <div v-if="uid && source !== 'DEMO'" class="actions"><RouterLink :to="detailsLocation">查看作品与演职员</RouterLink></div>
  <WorkGraphPanel v-if="uid" :uid="uid" :source="source" :work-title="workTitle"/>
</div></template>
<style scoped>
.character-graph-page{padding:32px 0 45px}.graph-page-heading{margin-bottom:25px}.eyebrow{font-size:10px;letter-spacing:2px}.graph-page-heading h1{font-size:34px;font-weight:550;margin:10px 0}.graph-page-heading h1 span{color:#8ca082}.lead{font-size:13px;color:#8b9784}.graph-controls{display:flex;gap:20px;align-items:end;padding:18px 22px;border:1px solid #e0e6d8;background:#fff;border-radius:13px;margin-bottom:16px}.graph-controls label{font-size:11px;min-width:140px}.graph-controls select{font-size:13px}.work-picker{flex:1;max-width:330px}@media(max-width:550px){.graph-page-heading h1{font-size:27px}.graph-controls{flex-wrap:wrap;padding:16px;gap:14px}.graph-controls label{min-width:100px}.work-picker{min-width:0!important}}
</style>
