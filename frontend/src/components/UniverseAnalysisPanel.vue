<script setup lang="ts">
import { computed } from 'vue'
import { useExploreRequest } from '../composables/useExploreRequest'
import DistributionBars from './DistributionBars.vue'
const emit=defineEmits<{refresh:[]}>()
const props=defineProps<{uid:string;filter:string;version:string}>()
interface Analysis {version:string;nodeCount:number;edgeCount:number;ranking:{uid:string;label:string;degree:number}[];relationTypes:Record<string,number>;factions:{uid:string;label:string;count:number}[];path:string[];methodology:string}
const {data,error,retry}=useExploreRequest<Analysis>(computed(()=>`/universes/${encodeURIComponent(props.uid)}/analysis?${props.filter}&version=${encodeURIComponent(props.version)}`))
const relationNames:Record<string,string>={CLASSMATE:'同学',COLLEAGUE:'同事',FAMILY:'亲属',FOUNDER:'创始人',FRIEND:'朋友',MASTER:'主人',MEMBER:'成员',OTHER:'其他',OWNER:'饲主',SPOUSE:'配偶',SUPERIOR:'上司',LOVER:'恋人',RIVAL:'对手',STUDENT:'学生',TEACHER:'老师',PET:'宠物',SUBORDINATE:'下属'}
const relations=computed(()=>Object.entries(data.value?.relationTypes||{}).map(([code,count])=>({label:relationNames[code]||code,count})))
const factions=computed(()=>data.value?.factions.map(f=>({label:f.label,count:f.count}))||[])
</script>
<template><section class="panel"><h2>当前范围的关系分布</h2><p v-if="error" role="alert">{{ error }} <button @click="retry();emit('refresh')">重试并重新读取图谱</button></p><template v-if="data"><p class="muted">{{ data.nodeCount }} 个角色 · {{ data.edgeCount }} 条关系。{{ data.methodology }}</p><div class="analysis-charts"><section><h3>关系类型</h3><DistributionBars :items="relations" label="关系类型分布" unit="条"/></section><section><h3>有明确成员证据的组织</h3><DistributionBars v-if="factions.length" :items="factions" label="组织成员分布" unit="名"/><p v-else class="muted">当前没有可核验的组织成员资料。</p></section></div></template></section></template>
<style scoped>.analysis-charts{display:grid;grid-template-columns:1fr 1fr;gap:30px}.analysis-charts h3{font-size:14px}.panel h2{font-size:18px}.panel .muted{font-size:12px;line-height:1.8}@media(max-width:650px){.analysis-charts{grid-template-columns:1fr}}</style>
