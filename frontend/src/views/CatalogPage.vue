<script setup lang="ts">
import { ref, watch, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { get } from '../api/client'
import type { Page, Entity } from '../types/catalog'
import EntityCard from '../components/EntityCard.vue'
import Pager from '../components/Pager.vue'
const route = useRoute(), router = useRouter()
const keyword = ref(''), year = ref(''), format = ref(''), source = ref(''), loading = ref(false), error = ref('')
const result = ref<Page<Entity>>({ items: [], page: 1, pageSize: 20, total: 0 })
const heading = computed(() => route.path === '/people' ? '作品背后的人' : route.path === '/characters' ? '走近动画角色' : '从一部动画，发现联系。')
let controller: AbortController | undefined
async function load() {
  controller?.abort(); controller = new AbortController(); const current = controller
  keyword.value = String(route.query.keyword || ''); year.value = String(route.query.year || ''); format.value = String(route.query.format || ''); source.value = String(route.query.source || '')
  loading.value = true; error.value = ''
  try { result.value = await get<Page<Entity>>(`${route.path}?${new URLSearchParams(route.query as Record<string, string>)}`, current.signal) }
  catch (e) { if (!current.signal.aborted) error.value = e instanceof Error ? e.message : '加载失败' }
  finally { if (!current.signal.aborted) loading.value = false }
}
function search(page = 1) { router.push({ path: route.path, query: { ...(keyword.value ? { keyword: keyword.value } : {}), ...(year.value ? { year: year.value } : {}), ...(format.value ? { format: format.value } : {}), ...(source.value ? { source: source.value } : {}), page } }) }
watch(() => route.fullPath, load, { immediate: true }); onUnmounted(() => controller?.abort())
</script>
<template>
  <section class="page-intro"><p class="eyebrow">EXPLORE · 作品 / 人员 / 角色</p><h1>{{ heading }}</h1><p>浏览档案，展开图谱，寻找共同创作者与作品之间的关联。</p><div class="tabs"><RouterLink to="/anime">动画作品</RouterLink><RouterLink to="/people">制作人员</RouterLink><RouterLink to="/characters">动画角色</RouterLink></div></section>
  <form class="toolbar" @submit.prevent="search()"><label class="grow">名称搜索<input v-model="keyword" placeholder="名称、原名或已核验别名" maxlength="100"></label><template v-if="route.path === '/anime'"><label>年份<input v-model="year" type="number" min="1800" max="2200" placeholder="全部"></label><label>形式<select v-model="format"><option value="">全部</option><option>TV</option><option>剧场版</option><option>OVA</option><option>WEB</option></select></label></template><button class="primary" :disabled="loading">搜索</button></form>
  <div class="toolbar"><label>数据来源<select v-model="source" @change="search(1)"><option value="">全部来源</option><option value="bangumi">Bangumi 真实资料</option><option value="fixture">合成演示</option><option value="manual">人工维护</option></select></label></div>
  <p v-if="result.nameNotice" class="feedback" role="status">{{ result.nameNotice }}</p><p v-if="error" class="feedback error" role="alert">{{ error }} <button @click="load">重试</button></p><p v-else-if="loading" class="feedback" role="status">正在查找相关档案…</p><template v-else><div v-if="result.items.length" class="entity-grid"><EntityCard v-for="entity in result.items" :key="entity.uid" :entity="entity" /></div><div v-else class="empty-state"><h2>暂未找到相关档案</h2><p>可调整搜索条件，或由管理员导入作品数据。</p></div><Pager :page="result.page" :total="result.total" :size="result.pageSize" @change="search" /></template>
</template>
