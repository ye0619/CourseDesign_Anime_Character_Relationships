<script setup lang="ts">
import { ref, watch, onScopeDispose } from 'vue'
import { mutate } from '../api/client'
const props = defineProps<{ uid: string; workTitle: string; disabled?: boolean }>()
const emit = defineEmits<{ deleted: [] }>()
const dialog = ref<HTMLDialogElement>(), pending = ref(false), error = ref('')
let generation = 0
watch(() => props.uid, () => { generation++; dialog.value?.close(); pending.value = false; error.value = '' })
onScopeDispose(() => generation++)
function open() { error.value = ''; dialog.value?.showModal() }
async function remove() {
  if (pending.value) return
  const version = generation
  pending.value = true; error.value = ''
  try {
    await mutate(`/anime/${encodeURIComponent(props.uid)}`, 'DELETE')
    if (version !== generation) return
    dialog.value?.close(); emit('deleted')
  } catch (cause) { if (version === generation) error.value = cause instanceof Error ? cause.message : '删除失败，请重试' }
  finally { if (version === generation) pending.value = false }
}
</script>
<template>
  <button class="delete-local" :disabled="disabled || pending" @click="open">删除本地作品</button>
  <dialog ref="dialog" :aria-labelledby="`delete-work-${uid}`" @cancel="pending && $event.preventDefault()">
    <h2 :id="`delete-work-${uid}`">删除《{{ workTitle }}》的本地资料？</h2>
    <p>将删除作品档案及本作所属的名册、配音和角色关系。共享角色、人员和手工补充的本地图谱会保留。</p>
    <p>删除前自动保存备份。Bangumi 作品仍可在线浏览，之后也可重新保存到本地。</p>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <div class="actions"><button :disabled="pending" autofocus @click="dialog?.close()">取消</button><button class="confirm-delete" :disabled="pending" @click="remove">{{ pending ? '正在备份并删除…' : '确认删除本地资料' }}</button></div>
  </dialog>
</template>
<style scoped>
.delete-local{color:#956057;font-size:12px}.confirm-delete{background:#985e53;color:white;border-color:#985e53}dialog{border:1px solid #d6d9c9;border-radius:16px;padding:28px;max-width:min(540px,calc(100vw - 40px));background:#fffef8;color:#34433b}dialog::backdrop{background:#1c302a77}dialog h2{font-size:20px;line-height:1.6}dialog p{font-size:14px;line-height:1.9}
</style>
