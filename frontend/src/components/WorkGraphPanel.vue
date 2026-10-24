<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { get } from '../api/client'
import { getCharacterGraph } from '../api/characterGraph'
import type { CharacterGraph } from '../types/characterGraph'
import type { LocalGraphDocument } from '../types/localGraph'
import { mergeLocalGraph } from '../utils/localGraph'
import CharacterGraphWorkspace from './CharacterGraphWorkspace.vue'
import LocalGraphEditor from './LocalGraphEditor.vue'
const props = withDefaults(defineProps<{ uid: string; source: 'LOCAL' | 'ONLINE' | 'DEMO'; workTitle?: string }>(), { workTitle: '角色关系图谱' })
const route = useRoute()
const subjectId = computed(() => /^anime:bangumi:([1-9]\d*)$/.exec(props.uid)?.[1])
const official = shallowRef<CharacterGraph>(), local = shallowRef<LocalGraphDocument>(), loading = shallowRef(false), error = shallowRef(''), localError = shallowRef(''), attempt = shallowRef(0)
const selectedNodeId = shallowRef<string | null>(null), selectedEdgeId = shallowRef<string | null>(null)
const graph = computed(() => official.value ? mergeLocalGraph(official.value,local.value) : undefined)
const focus = computed(() => typeof route.query.character === 'string' ? route.query.character : null)
watch([() => props.uid, attempt], async (_value, _previous, onCleanup) => {
  const controller = new AbortController(); onCleanup(() => controller.abort())
  official.value = undefined;local.value=undefined;loading.value = true;error.value = '';localError.value='';selectedNodeId.value=null;selectedEdgeId.value=null
  const id=subjectId.value
  const results=await Promise.allSettled([
    id ? get<CharacterGraph>(`/explore/anime/${id}/graph`,controller.signal) : getCharacterGraph(props.source==='DEMO'?'DEMO':'NEO4J',props.uid,controller.signal),
    id ? get<LocalGraphDocument>(`/explore/anime/${id}/local-graph`,controller.signal) : Promise.resolve(undefined)
  ])
  if(controller.signal.aborted)return
  const [remote,extension]=results
  if(extension.status==='fulfilled')local.value=extension.value
  else localError.value=extension.reason instanceof Error?extension.reason.message:'本地扩展读取失败，暂不可编辑'
  if(remote.status==='fulfilled')official.value=remote.value
  else {
    error.value=remote.reason instanceof Error?remote.reason.message:'官方角色资料读取失败'
    if(local.value && (local.value.nodes.length || local.value.edges.length))official.value={work:{uid:props.uid,title:props.workTitle,subtitle:'',characterCount:0,relationCount:0},nodes:[],edges:[],relationTypes:[],meta:{dataSource:'NEO4J',demo:false,truncated:true,notice:'官方资料暂不可用，当前仅显示已保存的本地扩展与官方编号引用。',maxNodes:300,maxEdges:800}}
  }
  loading.value=false
}, { immediate: true })
</script>
<template><section class="work-graph-panel" aria-label="角色关系图谱"><p v-if="loading" class="feedback" role="status">正在读取角色与关系…</p><p v-if="error || localError" class="feedback error" role="alert">{{ error }} {{ localError }} <button :disabled="loading" @click="attempt++">重试</button></p><LocalGraphEditor v-if="subjectId && local && graph" :subject-id="subjectId" :document="local" :graph="graph" :selected-node-id="selectedNodeId" :selected-edge-id="selectedEdgeId" @saved="local=$event" @reload="attempt++"/><CharacterGraphWorkspace v-if="graph" :key="uid" :graph="graph" :initial-selected-id="focus" @node-selected="selectedNodeId=$event;selectedEdgeId=null" @edge-selected="selectedEdgeId=$event;selectedNodeId=null"/><footer><span>已加载 {{ graph?.nodes.length || 0 }} 个实体</span><button :disabled="loading" @click="attempt++">重新读取</button></footer></section></template>
<style scoped>.work-graph-panel{min-width:0}.work-graph-panel footer{display:flex;align-items:center;gap:16px;padding:14px 20px;font-size:12px;color:#718677;border-top:1px solid #e0e9e3}</style>
