<script setup lang="ts">
import { computed } from 'vue'
import { relationDefinitions } from '../utils/characterGraph'
import type { ConnectionPath, ConnectionSubject } from '../types/connections'
const props=defineProps<{path:ConnectionPath;scope:ConnectionSubject[];selectedStep:number}>()
const names=computed(()=>new Map(props.path.nodes.map(n=>[n.uid,n.name])))
const works=computed(()=>new Map(props.scope.map(s=>[s.subjectId,s.title])))
const relationLabel=(semantic:string)=>semantic.includes(':')?semantic.slice(semantic.indexOf(':')+1):relationDefinitions.find(r=>r.code===semantic)?.label||'来源记录关系'
const safe=(url:string|null)=>url&&/^https?:\/\//.test(url)?url:undefined
</script>
<template><section class="evidence" aria-label="逐步证据"><h3>每一步的依据</h3><details v-for="(step,index) in path.edges" :key="step.fact.uid" :open="selectedStep===index">
<summary>第{{ index+1 }}步 · {{ names.get(step.traversalSource) }} — {{ names.get(step.traversalTarget) }} · {{ step.fact.kind==='VOICE'?'配音':'剧情关系' }}</summary>
<p>作品：{{ works.get(step.fact.subjectId) }}；语言：{{ step.fact.language || '未知' }}；版本：{{ step.fact.version || '未知' }}；集数：{{ step.fact.episodeScope || '未知' }}。</p>
<p v-if="step.fact.kind==='VOICE'">原始事实：{{ names.get(step.fact.source) }} 为 {{ names.get(step.fact.target) }} 配音。{{ step.reversed?'本步从角色走向声优，配音事实方向保持不变。':'' }}</p><p v-else>剧情关系：{{ relationLabel(step.fact.semantic) }}；来源方向 {{ names.get(step.fact.source) }} {{ step.fact.directed?'→':'—' }} {{ names.get(step.fact.target) }}。{{ step.reversed&&step.fact.directed?'本步反向阅读，原始事实方向保持不变。':'' }}</p>
<p v-if="step.fact.scope==='GLOBAL_SOURCE_PROJECTED'">来源是全局角色关联，在本作背景展示，尚未经本季剧情时点独立审核。</p><p v-if="step.fact.manual">本地人工声明，来源可追溯不等于事实已独立审核。</p><p v-if="step.fact.spoiler">本条涉及剧透。</p><p v-if="step.fact.ended">来源标记为已结束关系。</p>
<ul><li v-for="(source,i) in step.fact.evidence" :key="i"><a v-if="safe(source.url)" :href="safe(source.url)" target="_blank" rel="noopener noreferrer">{{ source.provider || '来源记录' }}</a><span v-else>{{ source.provider || '来源记录' }}</span> · {{ source.reviewStatus==='MANUAL'?'人工声明':'来源已记录' }}<p>{{ source.text || '来源未提供额外说明' }}</p><small>来源字段 {{ source.sourceField }} · 版本 {{ source.revision || '未知' }}{{ source.retrievedAt?' · '+source.retrievedAt:'' }}</small></li></ul>
</details></section></template>
<style scoped>.evidence{margin:20px 0;font-size:12px;line-height:1.9}.evidence details{border-top:1px solid #dce5df;padding:10px 0}.evidence summary{cursor:pointer;min-height:36px;padding:8px 0}.evidence li{margin-bottom:14px}.evidence small{overflow-wrap:anywhere;color:var(--muted,#74897a)}.evidence p{max-width:80ch}</style>
