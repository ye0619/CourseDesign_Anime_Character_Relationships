<script setup lang="ts">
import type { RelationEvidence } from '../types/evidence'
defineProps<{ evidence: RelationEvidence }>()
</script>
<template>
  <article class="relation-evidence">
    <a :href="evidence.sourceUrl" target="_blank" rel="noopener noreferrer">{{ evidence.sourceName }}</a>
    <p>{{ evidence.description }}</p>
    <dl v-if="evidence.rawRelation || evidence.sourceField || evidence.licenseLabel">
      <template v-if="evidence.rawRelation"><dt>原始关系文本</dt><dd>{{ evidence.rawRelation }}</dd></template>
      <template v-if="evidence.sourceField"><dt>来源位置</dt><dd>{{ evidence.sourceField }}</dd></template>
      <template v-if="evidence.licenseLabel"><dt>资料许可</dt><dd>{{ evidence.licenseLabel }}</dd></template>
    </dl>
    <small>{{ evidence.attribution }} · 版本 {{ evidence.sourceRevision }}</small>
  </article>
</template>
<style scoped>
.relation-evidence{margin:12px 0;padding:12px;background:#f4f7ef;border:1px solid #dfe7d8;border-radius:6px;font-size:12px;overflow-wrap:anywhere}.relation-evidence p{line-height:1.7}.relation-evidence dl{margin:10px 0}.relation-evidence dt{color:#718471;font-size:11px;margin-top:8px}.relation-evidence dd{margin:3px 0;line-height:1.6;white-space:pre-wrap}.relation-evidence small{font-size:10px;line-height:1.6;color:#6a7e6a}
</style>
