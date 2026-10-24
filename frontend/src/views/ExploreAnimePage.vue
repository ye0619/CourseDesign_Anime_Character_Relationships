<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DeleteLocalWorkButton from '../components/DeleteLocalWorkButton.vue'
import EntityCard from '../components/EntityCard.vue'
import AnimeSearchSelect from '../components/AnimeSearchSelect.vue'
import { entityLink, title } from '../types/catalog'
import ActivityHistoryPanel from '../components/ActivityHistoryPanel.vue'
import { useActivityHistory } from '../composables/useActivityHistory'
import { useViewerStore } from '../stores/viewer'
import { useExploreRequest } from '../composables/useExploreRequest'
import type { ExploreResult } from '../types/explore'
const route = useRoute(), router = useRouter(), viewer = useViewerStore()
const scope = computed(() => viewer.initialized ? String(viewer.state?.profile?.id || 'PUBLIC') : '')
const history = useActivityHistory(() => scope.value)
const form = reactive({ keyword: '', year: '', format: '' })
const page = computed(() => Math.max(1, Number(route.query.page) || 1))
const query = computed(() => new URLSearchParams({ keyword: String(route.query.keyword || ''), year: String(route.query.year || ''), format: String(route.query.format || ''), page: String(page.value), localPage: String(page.value) }))
const { data, pending, error, retry } = useExploreRequest<ExploreResult>(computed(() => `/explore/anime?${query.value}`))
const items = computed(() => {
  const result = new Map((data.value?.local.items || []).map(item => [item.uid, item]))
  for (const item of data.value?.online.items || []) result.set(item.uid, { ...item, ...result.get(item.uid) })
  return [...result.values()]
})
const more = computed(() => !!data.value && [data.value.online, data.value.local].some(result => result.page * result.pageSize < result.total))
watch(() => route.fullPath, () => {
  for (const key of ['keyword', 'year', 'format'] as const) form[key] = String(route.query[key] || '')
  if (scope.value && form.keyword) history.recordSearch(form.keyword)
}, { immediate: true })
function search() {
  const target = { path: '/anime', query: { keyword: form.keyword.trim(), year: form.year, format: form.format } }
  if (router.resolve(target).fullPath === route.fullPath) retry(); else void router.push(target)
}
function changePage(value: number) { void router.push({ path: '/anime', query: { ...route.query, page: String(value) } }) }
</script>
<template>
  <section class="page-intro"><p class="eyebrow">映间 · ANIME CONNECTIONS</p><h1>探索故事里的人物，也发现自己的偏好。</h1><p>搜索动画，沿着真实关系认识角色。关联 Bangumi 片单，查看你的观看画像与推荐。</p><div class="actions"><RouterLink class="button" to="/library">关联 Bangumi 与我的片单</RouterLink><RouterLink class="button" to="/preferences">个人观看画像</RouterLink></div></section>
  <form class="toolbar" @submit.prevent="search"><AnimeSearchSelect v-model="form.keyword" class="grow" @select="router.push(entityLink($event))" @search="search"/><label>发行年份<input v-model="form.year" type="number" min="1800" max="2200" placeholder="全部"></label><label>形式<select v-model="form.format"><option value="">全部</option><option>TV</option><option>剧场版</option><option>OVA</option><option>WEB</option></select></label><button class="primary" :disabled="pending">搜索动画</button></form>
  <ActivityHistoryPanel :scope="scope"/>
  <p v-if="pending" class="feedback" role="status">正在查找作品…</p><p v-if="error" class="feedback error" role="alert">{{ error }} <button @click="retry">重试</button></p>
  <template v-if="data"><p v-if="data.meta.notice" class="notice" role="status">{{ data.meta.notice }}</p><div v-if="items.length" class="entity-grid"><div v-for="entity in items" :key="entity.uid" class="work-result"><EntityCard :entity="entity"/><DeleteLocalWorkButton v-if="entity.localAvailable || entity.libraryStatus === 'ARCHIVED'" :uid="entity.localUid || entity.uid" :work-title="title(entity)" @deleted="retry"/></div></div><p v-else class="empty-state">当前没有可展示的匹配作品，可调整名称或筛选条件。</p><nav class="actions" aria-label="搜索结果翻页"><button :disabled="page <= 1 || pending" @click="changePage(page - 1)">上一页</button><span>第 {{ page }} 页 · 当前 {{ items.length }} 部</span><button :disabled="!more || pending" @click="changePage(page + 1)">下一页</button></nav></template>
</template>

<style scoped>.work-result{display:flex;flex-direction:column;align-items:stretch;gap:10px}.work-result :deep(.entity-card){flex:1}.work-result :deep(.delete-local){align-self:flex-start}</style>

