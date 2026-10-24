<script setup lang="ts">
import type { ViewingRecord } from '../types/viewer'
defineProps<{ works: ViewingRecord[] }>()
</script>
<template><ul class="report-works"><li v-for="work in works" :key="work.subjectId"><RouterLink :to="`/explore/anime/${work.subjectId}`">{{ work.title }}</RouterLink><small>个人评分 {{ work.personalRating || '未评分' }}</small></li><li v-if="!works.length">暂无作品</li></ul></template>
<style scoped>.report-works{padding-left:20px;font-size:12px;line-height:1.8}.report-works li{margin:8px 0;overflow-wrap:anywhere}.report-works small{color:#718677;margin-left:12px}</style>
