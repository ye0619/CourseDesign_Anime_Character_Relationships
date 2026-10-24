<script setup lang="ts">
import { computed, onScopeDispose, shallowRef, watch } from 'vue'
import DeleteLocalWorkButton from './DeleteLocalWorkButton.vue'
import { get, mutate } from '../api/client'
const props = defineProps<{ subjectId: string; localAvailable?: boolean; workTitle: string }>()
const emit = defineEmits<{ saved: []; deleted: [] }>()
interface Status { saved: boolean; status: string; error: string | null; updatedAt: string | null }
const state = shallowRef<Status>(), error = shallowRef(''), submitting = shallowRef(false)
const busy = computed(() => submitting.value || ['PENDING', 'RUNNING'].includes(state.value?.status || ''))
let controller: AbortController | undefined, timer: ReturnType<typeof setTimeout> | undefined, generation = 0
async function read(version: number, notify = false) {
  controller?.abort(); const current = new AbortController(); controller = current
  try {
    const result = await get<Status>(`/explore/anime/${encodeURIComponent(props.subjectId)}/saved`, current.signal)
    if (version !== generation) return
    state.value = result; error.value = ''
    if (['PENDING', 'RUNNING'].includes(result.status)) timer = setTimeout(() => void read(version, true), 1500)
    else if (notify && result.saved) emit('saved')
  } catch (cause) { if (version === generation && !current.signal.aborted) error.value = cause instanceof Error ? cause.message : '保存状态读取失败' }
}
watch(() => props.subjectId, () => {
  generation++; clearTimeout(timer); controller?.abort(); state.value = undefined; error.value = ''; submitting.value = false
  void read(generation)
}, { immediate: true })
onScopeDispose(() => { generation++; clearTimeout(timer); controller?.abort() })
async function save() {
  const version = generation
  submitting.value = true; error.value = ''; clearTimeout(timer)
  try {
    await mutate(`/explore/anime/${encodeURIComponent(props.subjectId)}/saved?refresh=${!!state.value?.saved || !!props.localAvailable}`, 'POST')
    if (version === generation) await read(version, true)
  } catch (cause) { if (version === generation) error.value = cause instanceof Error ? cause.message : '保存失败，可重试' }
  finally { if (version === generation) submitting.value = false }
}
</script>
<template><div class="save-anime"><button class="primary" :disabled="busy || !state" @click="save">{{ busy ? '正在保存与补全…' : state?.saved || localAvailable ? '更新数据' : '保存本地' }}</button><span v-if="state?.status === 'COMPLETE'" role="status">已保存 · {{ state.updatedAt ? new Date(state.updatedAt).toLocaleString('zh-CN') : '' }}</span><span v-if="state?.error || error" role="status">{{ error || state?.error }}</span><button v-if="error" @click="read(generation)">重新读取状态</button><DeleteLocalWorkButton v-if="state?.saved || localAvailable" :uid="`anime:bangumi:${subjectId}`" :work-title="workTitle" :disabled="busy || !state" @deleted="emit('deleted')"/></div></template>
<style scoped>.save-anime{display:flex;align-items:center;flex-wrap:wrap;gap:12px;margin:16px 0}.save-anime span{font-size:12px;color:#687d6d;max-width:65ch;line-height:1.8}</style>
