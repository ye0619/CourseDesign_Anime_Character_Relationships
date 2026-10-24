<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import WorkRelationships from '../components/WorkRelationships.vue'
import { useExploreRequest } from '../composables/useExploreRequest'
import type { ExploreDetail } from '../types/explore'
import { title } from '../types/catalog'
const route = useRoute()
const source = computed(() => data.value?.dataSource || 'ONLINE')
const id = computed(() => String(route.params.id))
const { data, error, retry } = useExploreRequest<ExploreDetail>(computed(() => `/explore/anime/${encodeURIComponent(id.value)}`))
</script>
<template><div class="network-page-heading"><RouterLink :to="{path:`/explore/anime/${id}`,query:{source}}">返回作品资料</RouterLink><h1>{{ data ? title(data.entity) : '作品关系图谱' }}</h1><p v-if="error" role="alert">{{ error }} <button @click="retry">重试</button></p></div><WorkRelationships :subject-id="id" :uid="data?.entity.uid" :work-title="data ? title(data.entity) : undefined" :source="source" :initial-view="route.query.section === 'staff' ? 'STAFF' : 'CHARACTERS'"/></template>
<style scoped>.network-page-heading{padding:28px 0 0}.network-page-heading>a{font-size:13px;color:#5d8571}.network-page-heading h1{font-size:28px;margin:16px 0 0;font-weight:550;color:#355f50}</style>
