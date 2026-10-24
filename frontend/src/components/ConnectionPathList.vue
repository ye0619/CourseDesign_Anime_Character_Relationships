<script setup lang="ts">
import type { ConnectionResult } from '../types/connections'
defineProps<{result:ConnectionResult}>()
const selected=defineModel<number>({default:0})
const reasons:Record<string,string>={RUNNING:'正在继续寻找其他联系',EXHAUSTED:'已穷尽当前资料和步数范围',RESULT_LIMIT:'已达到返回条数上限',TIME_BUDGET:'本轮达到时间上限',STATE_BUDGET:'本轮达到搜索规模上限',EDGE_BUDGET:'本轮达到关系检查上限',CANCELLED:'已取消，保留已找到的联系',INTERNAL_ERROR:'本轮查询失败，请重试'}
</script>
<template><section class="path-results" aria-live="polite"><p>{{ reasons[result.stopReason] || result.stopReason }}。{{ result.shortestProven&&result.paths.length?'第一条已证明是当前资料及条件下的最短联系。':'' }}</p>
<p v-if="result.exactPathCount!==null">最多 {{ result.query.maxHops }} 步内共 {{ result.exactPathCount }} 条联系，返回 {{ result.returnedPaths }} 条。</p><p v-else>已找到至少 {{ result.foundPathCount }} 条，返回 {{ result.returnedPaths }} 条；总数尚未穷尽。{{ !result.shortestProven&&result.paths.length?'当前找到的较短路径，最短性尚未证明。':'' }}</p>
<p v-if="!result.paths.length&&result.status!=='RUNNING'">{{ result.searchExhausted?'当前资料和限定步数内未找到联系。':'本轮未找到联系，搜索尚未穷尽。' }} 可以核对配音覆盖、增加步数或调整筛选；未收录不等于没有关系。</p>
<div class="path-choices" role="group" aria-label="选择联系"><button v-for="(path,index) in result.paths" :key="path.signature" :aria-pressed="selected===index" @click="selected=index">联系 {{ index+1 }} · {{ path.hopCount }} 步 · {{ path.storyEdgeCount }} 条剧情关系</button></div>
<p class="muted">最短不等于关系最强；路径条数不代表剧情亲密程度。资料不足时不会自动放开剧透。作品背景、语言和版本以每条证据为准。</p></section></template>
<style scoped>.path-results{margin:24px 0;font-size:13px;line-height:1.9}.path-choices{display:flex;flex-wrap:wrap;gap:8px}.path-choices button{font-size:12px}.path-choices button[aria-pressed=true]{background:#3b6a57;color:#fff}.muted{font-size:12px;color:var(--muted,#718677)}</style>
