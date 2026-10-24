<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import WorkGraphPanel from './WorkGraphPanel.vue'
import OnlineCreditsPanel from './OnlineCreditsPanel.vue'
import { providePortraitMode,providePortraitSubject } from '../composables/usePortraitFrames'
const props = withDefaults(defineProps<{ subjectId: string; source?: 'LOCAL' | 'ONLINE'; uid?: string; workTitle?: string; initialView?: 'CHARACTERS' | 'STAFF' | 'CREDITS' }>(), { source: 'ONLINE', initialView: 'CHARACTERS' })
const workUid = computed(() => props.uid || `anime:bangumi:${props.subjectId}`)
providePortraitMode()
providePortraitSubject(workUid)
const view = shallowRef<'CHARACTERS' | 'STAFF' | 'CREDITS'>(props.initialView)
watch(() => props.initialView, value => { view.value = value })
</script>
<template>
  <section class="online-network" aria-label="作品关系与演职员">
    <header><div><h2>作品关系与演职员</h2><p>探索本作角色、真实关系、制作人员与配音名册。</p></div>
      <div class="tabs" role="group" aria-label="资料视图">
        <button :aria-pressed="view === 'CHARACTERS'" @click="view = 'CHARACTERS'">角色关系</button>
        <button :aria-pressed="view === 'STAFF'" @click="view = 'STAFF'">制作人员</button>
        <button :aria-pressed="view === 'CREDITS'" @click="view = 'CREDITS'">配音与名册</button>
      </div>
    </header>
    <WorkGraphPanel v-if="view === 'CHARACTERS'" :uid="workUid" :source="source" :work-title="workTitle"/>
    <OnlineCreditsPanel v-else :subject-id="subjectId" :kind="view === 'STAFF' ? 'STAFF' : 'CAST'" :source="source" :uid="workUid"/>
  </section>
</template>
<style scoped>
.online-network{margin:30px 0;border:1px solid #d6e2db;border-radius:16px;background:#fff;overflow:hidden;min-width:0}header{display:flex;justify-content:space-between;align-items:center;gap:18px;padding:24px}h2{font-size:23px;font-weight:550;margin:0;color:#365f53}header p{font-size:12px;color:#7f9388;margin:7px 0 0}.tabs{display:flex;padding:4px;background:#edf4ef;border-radius:10px;flex-shrink:0}.tabs button{border:0;background:none;color:#6b8578;font-size:13px;min-height:40px}.tabs button[aria-pressed=true]{background:#fff;color:#2c6958;box-shadow:0 1px 5px #32533b14}@media(max-width:750px){header{align-items:start;flex-direction:column;padding:20px;gap:14px}h2{font-size:21px}.tabs{width:100%}.tabs button{flex:1;padding:8px}}
</style>
