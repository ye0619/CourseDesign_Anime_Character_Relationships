<script setup lang="ts">
import { ref, watch } from 'vue'
import { type Entity, entityLink, title, kindLabel } from '../types/catalog'
const props = defineProps<{ entity: Entity }>()
const failed = ref(false)
watch(() => props.entity.imageUrl, () => failed.value = false)
</script>
<template>
  <RouterLink :to="entityLink(entity)" class="entity-card">
    <div class="entity-image"><img v-if="entity.imageUrl && !failed" :src="entity.imageUrl" alt="" loading="lazy" referrerpolicy="no-referrer" @error="failed = true"><span v-else>{{ kindLabel(entity.type) }}</span></div>
    <div><small>{{ entity.onlineAvailable ? 'Bangumi 在线' : entity.source === 'fixture' ? '合成演示' : entity.source === 'bangumi' ? 'Bangumi 本地档案' : '人工维护' }} · {{ entity.year || kindLabel(entity.type) }}</small><h2>{{ title(entity) }}</h2><p v-if="entity.nameCn && entity.nameCn !== entity.name">{{ entity.name }}</p><span class="muted">{{ entity.format || '查看档案与关联 →' }}</span><span v-if="entity.libraryStatus" class="local-badge">{{ ({ARCHIVED:'已保存资料',ONLINE_ONLY:'仅在线资料',UNCHECKED:'本地状态待核对'})[entity.libraryStatus] }}</span><span v-else-if="entity.localAvailable" class="local-badge">已有本地档案</span><span v-else-if="entity.onlineAvailable" class="online-badge">{{ entity.localChecked ? '在线资料' : '本地状态待核对' }}</span></div>
  </RouterLink>
</template>
<style scoped>.local-badge,.online-badge{display:block;margin-top:9px;font-size:10px;color:#6d8a62}.online-badge{color:#7a98ab}</style>
