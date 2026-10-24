<script setup lang="ts">
import type { ConnectionQuery } from '../types/connections'
export type SearchOptions = Omit<ConnectionQuery,'from'|'to'|'graphRevision'>
const options = defineModel<SearchOptions>({ required: true })
</script>
<template><details class="search-options"><summary>探索条件 · 最多 {{ options.maxHops }} 步关系</summary><p>角色—声优是1步，角色—声优—另一作品角色是2步。剧情关系每条也是1步。</p><div class="option-grid">
<label>最多经过几步关系<input v-model.number="options.maxHops" type="number" min="2" max="12"></label><label>最多返回联系<input v-model.number="options.limit" type="number" min="1" max="50"></label>
<label>剧情方向<select v-model="options.storyDirection"><option value="CONNECTIVITY">按可连接性（保留原始方向）</option><option value="STRICT">严格遵循事实方向</option></select></label>
<label>配音语言<select v-model="options.language"><option :value="null">所有已知及未知语言</option><option value="ja">明确日语</option><option value="zh">明确中文</option><option value="en">明确英语</option><option value="ko">明确韩语</option></select></label>
<label>配音版本<input v-model="options.version" maxlength="120" placeholder="留空不筛选；未知不拼接已知版本"></label>
<label class="check"><input v-model="options.includeUnknownLanguage" type="checkbox">筛选语言时包含未知</label><label class="check"><input v-model="options.spoilers" type="checkbox">包含剧透关系</label><label class="check"><input v-model="options.includeEnded" type="checkbox">包含已结束关系</label><label class="check"><input v-model="options.includeManualRelations" type="checkbox">包含本地人工声明</label><label class="check"><input v-model="options.requireStoryRelation" type="checkbox">至少包含一条剧情关系</label>
</div></details></template>
<style scoped>.search-options{margin:22px 0;font-size:13px}.search-options summary{cursor:pointer;min-height:40px;padding:10px 0}.search-options p{font-size:12px;line-height:1.8}.option-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin:16px 0}.option-grid label{font-size:12px}.option-grid .check{display:flex;align-items:center;gap:7px}.check input{width:auto}@media(max-width:650px){.option-grid{grid-template-columns:1fr 1fr}}@media(max-width:420px){.option-grid{grid-template-columns:1fr}}</style>
