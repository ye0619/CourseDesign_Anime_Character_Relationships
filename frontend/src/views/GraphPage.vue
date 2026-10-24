<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useExploreRequest } from '../composables/useExploreRequest'
import { type Entity, type Page, title } from '../types/catalog'
import LocalSubjectCredits from '../components/LocalSubjectCredits.vue'
const route = useRoute(), router = useRouter()
const { data: works, pending, error, retry } = useExploreRequest<Page<Entity>>(computed(() => '/anime?pageSize=100'))
const selected = ref('')
const uid = computed(() => String(route.query.centerUid || works.value?.items[0]?.uid || ''))
watch(uid, value => {
  selected.value = value
  // Preserve old person/character bookmarks through their archives.
  if (value.startsWith('person:') || value.startsWith('character:')) {
    void router.replace(`/${value.startsWith('person:') ? 'people' : 'characters'}/${encodeURIComponent(value)}`)
  }
}, { immediate: true })
function choose() { void router.push({ path: '/graph', query: { centerUid: selected.value, section: 'staff' } }) }
</script>
<template>
  <section class="page-intro"><p class="eyebrow">PRODUCTION CREDITS</p><h1>制作名单与职务</h1><p>按作品核对制作人员、职务与配音记录。</p></section>
  <form class="toolbar" @submit.prevent="choose"><label class="grow">动画作品<select v-model="selected" :disabled="pending"><option v-if="uid && !works?.items.some(work => work.uid === uid)" :value="uid">{{ uid }}</option><option v-for="work in works?.items" :key="work.uid" :value="work.uid">{{ title(work) }}</option></select></label><button class="primary" :disabled="pending || !selected">查看名单</button></form>
  <p v-if="error" class="feedback error" role="alert">{{ error }} <button @click="retry">重试</button></p><p v-if="pending" role="status">正在读取作品…</p>
  <template v-if="uid.startsWith('anime:')"><div class="actions"><RouterLink :to="{path:'/character-graph',query:{animeUid:uid,dataSource:'NEO4J'}}">查看角色关系图谱</RouterLink></div><LocalSubjectCredits :key="uid" :uid="uid" initial-section="staff"/></template>
</template>
