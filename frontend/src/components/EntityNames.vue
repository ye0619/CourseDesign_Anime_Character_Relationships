<script setup lang="ts">
import { computed } from 'vue'
import { reviewedNameSourceLink } from '../utils/reviewedNameSource'
import type { CharacterNode, NameAssertion } from '../types/characterGraph'
const props = defineProps<{ node: CharacterNode }>()
const preferred = computed(() => props.node.nameAssertions?.some(n => n.kind === 'PROJECT_PREFERRED'))
const assertions = computed(() => {
  const seen = new Set<string>()
  return [...(props.node.nameAssertions || [])].sort((a, b) => Number(b.kind === 'PROJECT_PREFERRED') - Number(a.kind === 'PROJECT_PREFERRED')).filter(item => {
    const key = JSON.stringify([item.provider,item.providerId||'',item.sourceUrl,item.sourceRevision,item.name])
    if (seen.has(key)) return false
    seen.add(key); return true
  })
})
const labels: Record<NameAssertion['kind'], string> = { ORIGINAL: '来源原名', SOURCE_CHINESE: '来源中文名', ALIAS: '来源别名', REVIEWED_ALIAS: '已核验别名', PROJECT_PREFERRED: '项目首选名' }
const sourceLink = (item:NameAssertion) => reviewedNameSourceLink(item.provider,item.sourceUrl)
</script>
<template>
  <details v-if="assertions.length || node.aliases?.length" class="entity-names"><summary>名称、别名与来源</summary>
    <p v-if="preferred">当前名称采用已审核的项目显示习惯，来源原值与别名保留供核对。</p>
    <p v-if="node.aliases?.length">别名：{{ node.aliases.join(' / ') }}</p>
    <dl><template v-for="(item, index) in assertions" :key="`${item.provider}-${index}`"><dt>{{ labels[item.kind] }}</dt><dd>{{ item.name }} <a v-if="sourceLink(item)" :href="sourceLink(item)" target="_blank" rel="noopener noreferrer">核对来源</a><small v-if="item.sourceRevision">{{ item.provider }} · 来源修订 {{ item.sourceRevision }}</small><small v-if="item.licenseLabel">{{ item.licenseLabel }}</small><small v-if="item.evidenceText">核验依据：{{ item.evidenceText }}</small><small v-if="item.identityVersion!==undefined">身份版本 {{ item.identityVersion }}</small><small v-if="item.reviewedOn">核验日期 {{ item.reviewedOn }}</small><small v-else-if="item.retrievedAt">读取于 {{ new Date(item.retrievedAt).toLocaleString('zh-CN') }}</small></dd></template></dl>
    <small v-if="node.namePolicyVersion">显示规则版本 {{ node.namePolicyVersion }}</small>
  </details>
</template>
<style scoped>.entity-names{margin:12px 0;color:#60786c;font-size:11px;line-height:1.8;overflow-wrap:anywhere}.entity-names summary{cursor:pointer;padding:7px 0}.entity-names p{font-size:11px}.entity-names dt{color:#7e9285;margin-top:9px}.entity-names dd{margin:2px 0 7px}.entity-names a{font-size:10px;color:#397d77;margin-left:5px}.entity-names small{display:block;font-size:10px;color:#8a9a8e}</style>
