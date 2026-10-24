<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import type { Bucket } from '../types/viewer'
const props = defineProps<{ items: Bucket[]; label: string; unit?: string; linkFor?: (item: Bucket) => RouteLocationRaw }>()
const maximum = computed(() => Math.max(1, ...props.items.map(i => i.count)))
</script>
<style scoped>a.distribution-row{min-height:36px;padding:5px;border-radius:6px}a.distribution-row:hover{background:#f2f6ec}a.distribution-row:focus-visible{outline:2px solid #8aab7c;outline-offset:2px}@media(pointer:coarse){a.distribution-row{min-height:40px}}</style>
<template><div v-if="items.some(i => i.count > 0)" class="distribution" :role="linkFor ? 'group' : 'img'" :aria-label="`${label}：${items.map(i => `${i.label}${i.count}${unit || '部'}`).join('，')}`"><component :is="linkFor ? RouterLink : 'div'" v-for="item in items" :key="item.label" :to="linkFor?.(item)" class="distribution-row" :title="`${item.label}：${item.count}${unit || '部'}${linkFor ? '，点击查看作品' : ''}`"><span>{{ item.label }}</span><div class="bar-track"><i :style="{ width: `${item.count / maximum * 100}%` }"></i></div><strong>{{ item.count }}</strong></component></div><p v-else class="no-distribution">暂无足够数据</p></template>
<style scoped>.distribution{display:grid;gap:13px}.distribution-row{display:grid;grid-template-columns:65px minmax(0,1fr) 30px;align-items:center;gap:12px;font-size:11px;font-variant-numeric:tabular-nums}.distribution-row>span{color:#819375;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.distribution-row strong{font-weight:450;text-align:right;color:#8c9c80}.bar-track{height:8px;border-radius:5px;background:#f0f4e9;overflow:hidden}.bar-track i{height:100%;display:block;background:#9db68b;border-radius:5px}.no-distribution{color:#9eac90;font-size:12px;text-align:center;padding:35px 0}</style>
