<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useExploreRequest } from '../composables/useExploreRequest'
import type { ExploreDetail } from '../types/explore'
import { title } from '../types/catalog'
import { defineAsyncComponent } from 'vue'
import SaveAnimeButton from '../components/SaveAnimeButton.vue'
const contentVersion = shallowRef(0)
function saved() { contentVersion.value++; retry() }
const WorkRelationships = defineAsyncComponent(() => import('../components/WorkRelationships.vue'))
import { useActivityHistory } from '../composables/useActivityHistory'
import { useViewerStore } from '../stores/viewer'
const router = useRouter()
const route = useRoute(), failed = shallowRef(false)
const viewer=useViewerStore(),history=useActivityHistory(()=>viewer.initialized?String(viewer.state?.profile?.id||'PUBLIC'):'')
const url = computed(() => `/explore/anime/${encodeURIComponent(String(route.params.id))}`)
const { data, pending, error, retry } = useExploreRequest<ExploreDetail>(url, computed(() => String(route.params.id)))
const entity = computed(() => data.value?.entity)
watch(url, () => { failed.value = false })
watch(()=>[entity.value,viewer.initialized,viewer.state?.profile?.id],()=>{if(entity.value&&viewer.initialized)history.recordVisit(String(route.params.id),title(entity.value))})
</script>
<template>
  <RouterLink to="/anime" class="back-link">返回探索作品</RouterLink><p v-if="pending" class="feedback" role="status">正在获取动画资料…</p><p v-if="error" class="feedback error" role="alert">{{ error }} <button @click="retry">重试</button></p>
  <p v-if="data && (pending || error)" class="notice">下方仍展示本作品上次成功读取的资料，来源以当前内容标签为准。</p><template v-if="data && entity"><p v-if="data.meta.notice" class="notice" role="status">{{ data.meta.notice }}</p><section class="detail-hero"><div class="detail-cover"><img v-if="entity.imageUrl && !failed" :src="entity.imageUrl" :alt="title(entity)" referrerpolicy="no-referrer" @error="failed = true"><span v-else>动画作品</span></div><div><p class="source-caption">{{ data.dataSource === 'LOCAL' ? '已保存资料' : 'Bangumi 在线资料' }} · {{ entity.localAvailable ? '已保存' : '可保存本地' }}</p><h1>{{ title(entity) }}</h1><p v-if="entity.nameCn && entity.nameCn !== entity.name" class="muted">{{ entity.name }}</p><p class="muted">{{ [entity.airDate, entity.format, entity.episodes ? `${entity.episodes}集` : ''].filter(Boolean).join(' · ') }}</p><p v-if="entity.score" class="online-score">{{ entity.score }} <small>Bangumi 社区评分{{ entity.rank ? ` · 排名 ${entity.rank}` : '' }}</small></p><div class="actions"><RouterLink :to="{path:'/compare',query:{subjectA:String(route.params.id)}}" class="button">加入作品对比</RouterLink><a :href="`https://bgm.tv/subject/${route.params.id}`" target="_blank" rel="noopener noreferrer" class="button">在 Bangumi 查看</a><RouterLink :to="{path:`/explore/anime/${route.params.id}/network`,query:{}}" class="button primary">全屏探索角色关系</RouterLink><RouterLink :to="{path:`/explore/anime/${route.params.id}/network`,query:{section:'staff'}}" class="button">查看制作名单</RouterLink></div></div></section><SaveAnimeButton :work-title="title(entity)" @deleted="router.push('/anime')" :local-available="!!entity.localAvailable" :subject-id="String(route.params.id)" @saved="saved"/><WorkRelationships :key="`${route.params.id}:${contentVersion}`" :subject-id="String(route.params.id)" :uid="entity.uid" :work-title="title(entity)" :source="data.dataSource"/><section class="panel"><h2>作品简介</h2><p class="summary">{{ entity.summary || '来源暂未提供简介。' }}</p><p v-if="data.meta.fetchedAt" class="muted">获取时间：{{ new Date(data.meta.fetchedAt).toLocaleString('zh-CN') }}{{ data.meta.cached ? '（缓存）' : '' }}</p></section></template>
</template>
<style scoped>.back-link{display:inline-block;margin:24px 0 6px;color:#66876a;font-size:13px}.source-caption{font-size:12px;color:#7595a6}.online-score{font-size:27px;color:#57796d}.online-score small{font-size:12px;color:#8a9c7e;margin-left:10px}</style>


