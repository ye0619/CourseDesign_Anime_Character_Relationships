<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { get } from '../api/client'
import { useViewerStore } from '../stores/viewer'
import type { PreferenceReport, ViewingPage } from '../types/viewer'
import { defaultLibraryFilters, libraryQuery, libraryRequest, readLibraryQuery, type LibraryFilters } from '../utils/libraryFilters'
import ViewingCard from '../components/ViewingCard.vue'
import Pager from '../components/Pager.vue'
const viewer = useViewerStore()
const route = useRoute(), router = useRouter(), filters = ref(defaultLibraryFilters())
const username = ref(''), token = ref(''), tokenMode = ref(false), changing = ref(false), showUnbind = ref(false), actionError = ref(''), syncing = ref(false)
const status = computed({ get: () => filters.value.status, set: value => { filters.value.status = value } })
const keyword = computed({ get: () => filters.value.keyword, set: value => { filters.value.keyword = value } })
const sort = computed({ get: () => filters.value.sort, set: value => { filters.value.sort = value } })
const page = ref(1), loading = ref(false), error = ref(''), summary = ref<PreferenceReport>()
const emptyPage = (): ViewingPage => ({ items: [], page: 1, pageSize: 20, total: 0, facets: { years: [], formats: [], tags: [] } })
const result = ref<ViewingPage>(emptyPage())
const facets = computed(() => result.value.facets || emptyPage().facets)
const statuses = [{ code: '', label: '全部' }, { code: '2', label: '已看' }, { code: '3', label: '在看' }, { code: '1', label: '想看' }, { code: '4', label: '搁置' }, { code: '5', label: '抛弃' }]
const bound = computed(() => viewer.state?.bound)
const running = computed(() => viewer.state?.syncStatus === 'RUNNING')
const lastSync = computed(() => viewer.state?.lastSyncedAt ? new Date(viewer.state.lastSyncedAt).toLocaleString('zh-CN') : '尚未同步')
let controller: AbortController | undefined, version = 0
async function fetchData(nextPage = page.value) {
  controller?.abort(); controller = new AbortController(); const current = controller, currentVersion = ++version
  if (!bound.value) { result.value = emptyPage(); summary.value = undefined; loading.value = false; return }
  loading.value = true; error.value = ''; page.value = nextPage
  try {
    const params = libraryRequest(readLibraryQuery(route.query).filters, page.value)
    const [items, stats] = await Promise.all([get<ViewingPage>(`/viewer/collections?${params}`, current.signal), get<PreferenceReport>('/viewer/preferences', current.signal)])
    if (currentVersion === version) { result.value = items; summary.value = stats }
  } catch(e) { if (!current.signal.aborted) error.value = e instanceof Error ? e.message : '片单加载失败' }
  finally { if (currentVersion === version) loading.value = false }
}
async function load(nextPage = 1) {
  const target = { path: '/library', query: libraryQuery(filters.value, nextPage) }
  if (router.resolve(target).fullPath === route.fullPath) await fetchData(nextPage)
  else await router.push(target)
}
function clearFilters() { filters.value = defaultLibraryFilters(); load(1) }
const ratingLabels: Record<string, string> = { UNRATED: '未评分', RATED: '已评分', HIGH: '8分及以上' }
const chips = computed(() => {
  const f = readLibraryQuery(route.query).filters
  return [
    f.keyword && { key: 'keyword', label: `名称：${f.keyword}` },
    f.tag && { key: 'tag', label: `${f.tagSource === 'PERSONAL' ? '个人' : '社区'}标签：${f.tag}` },
    f.year && { key: 'year', label: `发行年：${f.year === 'UNKNOWN' ? '未知' : f.year}` },
    f.format && { key: 'format', label: `形式：${f.format === 'UNKNOWN' ? '未标注' : f.format}` },
    f.rating && { key: 'rating', label: `个人评分：${ratingLabels[f.rating] || f.rating + '分'}` },
    f.missingTags && { key: 'missingTags', label: `缺少${f.tagSource === 'PERSONAL' ? '个人' : '社区'}有效标签` },
  ].filter(Boolean) as { key: keyof LibraryFilters; label: string }[]
})
function removeFilter(key: keyof LibraryFilters) { const f = readLibraryQuery(route.query).filters; if (key === 'missingTags') f.missingTags = false; else f[key] = defaultLibraryFilters()[key]; filters.value = f; load(1) }
const now = ref(Date.now()), cooldown = computed(() => Math.max(0, Math.ceil(((Date.parse(viewer.state?.lastSyncedAt || '') || 0) + 60_000 - now.value) / 1000)))
let clock: ReturnType<typeof setInterval> | undefined
async function bind() { actionError.value = ''; try { await viewer.bind(username.value, tokenMode.value ? token.value : ''); changing.value = false } catch(e) { actionError.value = e instanceof Error ? e.message : '关联失败' } finally { token.value = '' } }
async function sync() { syncing.value = true; actionError.value = ''; try { await viewer.sync() } catch(e) { actionError.value = e instanceof Error ? e.message : '同步失败' } finally { syncing.value = false } }
async function unbind() { actionError.value = ''; try { await viewer.unbind(); showUnbind.value = false; changing.value = false; username.value = ''; token.value = '' } catch(e) { actionError.value = e instanceof Error ? e.message : '解除关联失败' } }
function switchStatus(value: string) { status.value = value; load(1) }
watch(() => route.fullPath, () => { const parsed = readLibraryQuery(route.query); filters.value = parsed.filters; page.value = parsed.page; fetchData(parsed.page) }, { immediate: true })
watch(() => `${viewer.state?.bound}|${viewer.state?.profile?.id}|${viewer.state?.lastSyncedAt}`, (next, previous) => { if (next.split('|')[1] !== previous?.split('|')[1]) { result.value = emptyPage(); summary.value = undefined } fetchData(readLibraryQuery(route.query).page) })
onMounted(() => { viewer.load(); clock = setInterval(() => { now.value = Date.now() }, 1000) })
onUnmounted(() => { controller?.abort(); clearInterval(clock) })
</script>
<template>
  <section class="library-intro"><p class="eyebrow">YOUR WATCHLIST / 我的片单</p><h1>看过的故事，<span>都有你的偏好。</span></h1><p>连接 Bangumi，整理看过的动画，慢慢读懂自己喜欢什么。</p></section>
  <p v-if="viewer.loading && !viewer.state" class="feedback" role="status">正在读取账号关联…</p>
  <p v-if="viewer.error" class="feedback error" role="alert">{{ viewer.error }} <button @click="viewer.load">重试</button></p>
  <p v-if="actionError" class="feedback error" role="alert">{{ actionError }}</p>
  <section v-if="!bound || changing" class="binding-panel">
    <div class="binding-copy"><p class="eyebrow">CONNECT WITH BANGUMI</p><h2>{{ changing ? '更换关联账号' : '从你的 Bangumi 片单开始' }}</h2><p>无需注册本地账号。输入用户名、UID 或个人主页，即可读取公开的动画收藏。</p><p class="binding-note">公开关联只指定资料来源，不证明账号归属；想读取自己的私有收藏，可使用令牌验证。</p></div>
    <form class="binding-form" @submit.prevent="bind"><label v-if="!tokenMode">Bangumi 用户名 / UID / 主页<input v-model="username" required maxlength="200" placeholder="例如 sai 或 https://bgm.tv/user/用户名" autocomplete="off"></label><label v-else>Bangumi Access Token<input v-model="token" type="password" required minlength="16" maxlength="3000" placeholder="粘贴访问令牌，请勿输入账号密码" autocomplete="off" spellcheck="false"></label><div class="binding-actions"><button class="primary" :disabled="viewer.busy || running">{{ viewer.busy ? '正在关联…' : tokenMode ? '验证并同步片单' : '关联并同步片单' }}</button><button v-if="changing" type="button" @click="changing = false">取消</button></div><label class="inline-check"><input v-model="tokenMode" type="checkbox" @change="token = ''">使用令牌验证自己的账号</label><p v-if="tokenMode" class="token-note">在 <a href="https://next.bgm.tv/demo/access-token" target="_blank" rel="noopener noreferrer">Bangumi 官方页面生成令牌 ↗</a>。本项目仅读取资料；令牌只保存在当前服务会话，不写入数据库或浏览器存储。会话结束后需重新验证。</p></form>
  </section>
  <template v-if="bound">
    <section class="viewer-profile"><img v-if="viewer.state?.profile?.avatar" :src="viewer.state.profile.avatar" alt="" referrerpolicy="no-referrer"><div class="profile-copy"><h2>{{ viewer.state?.profile?.nickname }}<span>{{ viewer.state?.verified ? '令牌已验证' : '公开关联' }}</span></h2><a :href="`https://bgm.tv/user/${viewer.state?.profile?.username}`" target="_blank" rel="noopener noreferrer">@{{ viewer.state?.profile?.username }} ↗</a><small>最近成功同步：{{ lastSync }}</small></div><div class="profile-actions"><button class="primary" @click="sync" :disabled="running || syncing || viewer.busy || cooldown > 0 || viewer.state?.requiresReauthorization">{{ running ? `同步中 ${viewer.state?.processed} / ${viewer.state?.remoteTotal || '…'}` : cooldown > 0 ? `${cooldown}秒后可更新` : '更新片单' }}</button><button @click="changing = !changing; tokenMode = false" :disabled="running">更换账号</button><button @click="showUnbind = !showUnbind" :disabled="viewer.busy">解除关联</button></div></section>
    <div v-if="showUnbind" class="notice" role="alert"><p>解除后将删除当前浏览器对应的本地片单与关联信息，Bangumi 上的收藏保持不变。</p><button @click="unbind" :disabled="viewer.busy">确认解除</button> <button @click="showUnbind = false">取消</button></div>
    <p v-if="running" class="feedback" role="status">正在分页读取 Bangumi 动画收藏，{{ viewer.state?.lastSyncedAt ? '完成前仍展示上次片单。' : '首次同步完成后将显示作品和偏好统计。' }}<progress :value="viewer.state?.processed" :max="viewer.state?.remoteTotal || 1"></progress></p>
    <p v-if="viewer.state?.requiresReauthorization" class="notice">令牌会话已结束，已保存的片单仍可查看。<button @click="changing = true; tokenMode = true">重新验证</button></p>
    <p v-if="viewer.state?.error" class="feedback error" role="alert">{{ viewer.state.error }} <button @click="sync" :disabled="running">重试同步</button></p>
    <p v-if="viewer.state?.truncated" class="notice">当前片单为部分数据：单次最多同步5,000条，或来源缺少部分条目。偏好分析仅针对已同步记录。</p>
    <div v-if="summary" class="library-metrics"><div><strong>{{ summary.summary.completed }}</strong><span>已看动画</span></div><div><strong>{{ summary.summary.rated }}</strong><span>已有个人评分</span></div><div><strong>{{ summary.summary.averageRating ?? '—' }}</strong><span>个人平均评分</span></div><RouterLink to="/preferences">读懂你的观看偏好 <span>↗</span></RouterLink></div>
    <section class="watchlist-section"><div class="watchlist-heading"><h2>你的动画片单 <small>{{ result.total }} 部</small></h2><div class="status-tabs" aria-label="观看状态"><button v-for="item in statuses" :key="item.code" :class="{ active: status === item.code }" :aria-pressed="status === item.code" @click="switchStatus(item.code)">{{ item.label }}</button></div></div><form class="toolbar" @submit.prevent="load(1)"><label class="grow">搜索片单<input v-model="keyword" type="search" maxlength="100" placeholder="搜索中文名或原名"></label><label>排序<select v-model="sort" @change="load(1)"><option value="updated">收藏记录更新顺序</option><option value="rating">个人评分优先</option><option value="title">作品名称</option></select></label><button :disabled="loading">应用筛选</button><button type="button" @click="clearFilters">重置筛选</button><div class="library-filter-grid"><label>个人评分<select v-model="filters.rating"><option value="">全部评分</option><option value="UNRATED">未评分</option><option value="RATED">已评分</option><option value="HIGH">8分及以上</option><option v-for="n in 10" :key="n" :value="String(n)">{{ n }}分</option></select></label><label>发行年份<select v-model="filters.year"><option value="">全部年份</option><option v-for="year in facets.years" :key="year" :value="year === '未知' ? 'UNKNOWN' : year">{{ year }}</option></select></label><label>动画形式<select v-model="filters.format"><option value="">全部形式</option><option v-for="format in facets.formats" :key="format" :value="format === '未标注' ? 'UNKNOWN' : format">{{ format }}</option></select></label><label>标签来源<select v-model="filters.tagSource" @change="load(1)"><option value="COMMUNITY">社区标签</option><option value="PERSONAL">个人标签</option></select></label><label class="tag-filter">包含标签<input v-model="filters.tag" list="library-tags" maxlength="100" placeholder="输入完整标签，如科幻" @input="filters.missingTags = false"><datalist id="library-tags"><option v-for="tag in facets.tags" :key="tag" :value="tag"></option></datalist></label><label class="inline-check"><input v-model="filters.missingTags" type="checkbox" @change="filters.missingTags && (filters.tag = '')">仅看缺少有效标签的作品</label></div></form><div v-if="chips.length" class="filter-chips" aria-label="当前筛选"><span>正在筛选</span><button v-for="chip in chips" :key="chip.key" :aria-label="'移除筛选：' + chip.label" @click="removeFilter(chip.key)">{{ chip.label }} ×</button></div><p v-if="error" class="feedback error" role="alert">{{ error }} <button @click="load()">重试</button></p><p v-if="loading" class="feedback" role="status">正在整理片单…</p><div v-else-if="result.items.length" class="watchlist-grid"><ViewingCard v-for="item in result.items" :key="item.subjectId" :item="item" /></div><div v-else class="empty-state"><h2>{{ running ? '片单正在路上' : '暂无符合条件的作品' }}</h2><p>{{ viewer.state?.lastSyncedAt ? '可切换观看状态或调整搜索条件。公开关联只能读取公开收藏。' : '请先同步片单；若收藏为私有，请使用令牌验证。' }}</p></div><Pager v-if="result.total" :page="result.page" :size="result.pageSize" :total="result.total" @change="load" /></section>
  </template>
  <section v-else-if="!viewer.loading" class="library-empty"><div><span>01</span><h3>关联片单</h3><p>从 Bangumi 带回你的收藏记录</p></div><div><span>02</span><h3>回看故事</h3><p>筛选已看、在看和想看的动画</p></div><div><span>03</span><h3>发现偏好</h3><p>从标签与个人评分中寻找线索</p></div></section>
</template>
<style scoped>
.library-filter-grid{flex-basis:100%;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px;border-top:1px solid #e4ebdc;padding-top:18px}.library-filter-grid label{min-width:0}.library-filter-grid .inline-check{align-self:end;font-size:11px}.filter-chips{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin:12px 0 20px;font-size:11px;color:#8b9c7e}.filter-chips button{max-width:100%;overflow-wrap:anywhere;white-space:normal;text-align:left;font-size:10px;background:#eef4e6;color:#648255;border-color:#dae5ce;padding:7px 10px}.library-filter-grid input,.library-filter-grid select{font-size:12px}@media(max-width:650px){.library-filter-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.library-filter-grid .inline-check{grid-column:1/-1}}@media(max-width:380px){.library-filter-grid{grid-template-columns:1fr}}

.library-intro{padding:42px 0 28px}.library-intro h1{font-size:clamp(26px,3.2vw,38px);font-weight:550;letter-spacing:-.5px;margin:9px 0 12px}.library-intro h1 span{color:#8a9f7e}.library-intro>p:last-child{font-size:13px;color:#8a9880}.binding-panel{display:grid;grid-template-columns:1fr 1fr;gap:50px;padding:34px;border:1px solid #dce5d3;border-radius:15px;background:#fff}.binding-copy h2{font-weight:550;font-size:23px;margin:10px 0 18px}.binding-copy>p{font-size:13px;color:#819274;line-height:1.9}.binding-copy .binding-note{font-size:11px;color:#a1ad95}.binding-form{display:grid;gap:16px;align-content:center}.binding-form input{font-size:12px;padding:13px}.binding-actions{display:flex;gap:10px}.binding-form .inline-check{font-size:11px;padding:0}.token-note{font-size:11px;color:#91a080;line-height:1.9;margin:0}.token-note a{color:#54764e;text-decoration:underline}.viewer-profile{display:flex;align-items:center;gap:17px;padding:23px 26px;background:white;border:1px solid #dde6d5;border-radius:13px}.viewer-profile>img{height:58px;width:58px;object-fit:cover;border-radius:50%}.profile-copy{min-width:0}.profile-copy h2{font-size:20px;font-weight:550;margin:0 0 4px;overflow-wrap:anywhere}.profile-copy h2 span{font-size:9px;font-weight:400;vertical-align:middle;background:#f0f4e9;color:#81996e;padding:4px 7px;border-radius:5px;margin-left:10px}.profile-copy>a{font-size:11px;color:#83a071}.profile-copy>small{font-size:10px;color:#a0ac93;display:block;margin-top:4px}.profile-actions{display:flex;flex-wrap:wrap;gap:8px;margin-left:auto}.profile-actions button{font-size:11px}.library-metrics{display:flex;align-items:center;gap:40px;padding:28px 3px;color:#8d9d7f;font-size:11px;font-variant-numeric:tabular-nums}.library-metrics>div{display:grid;gap:3px}.library-metrics strong{font-size:29px;font-weight:450;color:#4b704d}.library-metrics>a{margin-left:auto;color:#64845a;display:flex;gap:30px}.watchlist-section{padding:10px 0 30px}.watchlist-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap}.watchlist-heading h2{font-size:20px;font-weight:550}.watchlist-heading h2 small{font-size:11px;color:#a5b098;font-weight:400;margin-left:8px}.status-tabs{display:flex;gap:4px;background:#edf2e6;padding:4px;border-radius:10px;flex-wrap:wrap}.status-tabs button{font-size:11px;border:0;background:none;padding:9px 13px;color:#91a17e;min-height:38px}.status-tabs button.active{background:white;color:#4e714c;box-shadow:0 1px 5px #293a2812}.watchlist-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:17px}.library-empty{display:grid;grid-template-columns:repeat(3,1fr);gap:20px;padding:40px 20px;color:#9aab89}.library-empty>div{padding:10px 20px}.library-empty span{font-size:28px;font-weight:300;color:#c0ceb1}.library-empty h3{font-size:15px;font-weight:500;color:#7a9569}.library-empty p{font-size:11px}.feedback progress{margin-top:12px}.notice p{margin:0 0 12px}@media(max-width:850px){.binding-panel{gap:24px;padding:25px}.viewer-profile{flex-wrap:wrap}.profile-actions{margin-left:0;width:100%}}@media(max-width:650px){.binding-panel{grid-template-columns:1fr;gap:15px;padding:22px}.binding-copy h2{font-size:21px}.watchlist-grid{grid-template-columns:1fr}.library-metrics{gap:20px;flex-wrap:wrap}.library-metrics>a{margin-left:0;width:100%}.library-empty{grid-template-columns:1fr;gap:0;padding:15px 0}.library-empty>div{padding:10px}.profile-actions button{flex:1}.viewer-profile{padding:20px}.profile-copy h2{font-size:17px}.library-intro{padding-top:30px}.status-tabs button{padding:9px 11px;min-height:40px}}
</style>
