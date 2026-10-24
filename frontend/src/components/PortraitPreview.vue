<script setup lang="ts">
import { computed, reactive, shallowRef, watch } from 'vue'
import type { CharacterNode } from '../types/characterGraph'
import { portraitFrame, portraitImageStyle, safePortraitUrl, type ImageDimensions, type PortraitOverride } from '../utils/portraitFraming'
import { portraitDimensions } from '../composables/usePortraitFrames'
import { effectivePortraitOverrides, savePortraitSetting } from '../composables/usePortraitSettings'
const props = defineProps<{ node: CharacterNode }>()
const url = computed(() => safePortraitUrl(props.node.imageUrl))
const image = shallowRef<ImageDimensions | null>(null), message = shallowRef('')
const modified = shallowRef(false)
const currentFrame = computed(() => portraitFrame(props.node.id, url.value, image.value, 'auto', props.node.characterType, effectivePortraitOverrides.value))
const draft = reactive({ mode: 'portrait' as 'portrait' | 'full', focalX: .5, focalY: 0, scale: 1 })
function resetDraft() {
  const frame = currentFrame.value
  const clamp = (value: number, low: number, high: number) => Math.max(low, Math.min(high, value))
  Object.assign(draft, { mode: frame.full ? 'full' : 'portrait', focalX: clamp(frame.x / 100, 0, 1), focalY: clamp(frame.y / 100, 0, 1), scale: clamp(image.value && !frame.full ? frame.width / Math.max(100, 100 * image.value.width / image.value.height) : 1, 1, 3) })
  modified.value = false
}
watch(() => [props.node.id, url.value], async (_value, _old, onCleanup) => {
  let current = true; onCleanup(() => { current = false }); image.value = null; message.value = ''
  const result = url.value ? await portraitDimensions(url.value) : null
  if (!current) return
  image.value = result; resetDraft()
}, { immediate: true })
const preview = computed(() => {
  const override: PortraitOverride = { ...draft, entityUid: props.node.id, imageFingerprint: `${url.value}|${image.value?.width}x${image.value?.height}`, reviewed: true }
  const f = modified.value ? portraitFrame(props.node.id, url.value, image.value, 'auto', props.node.characterType, [override]) : currentFrame.value
  return portraitImageStyle(f)
})
function save(reset = false) {
  if (!image.value) return
  if (!reset && !modified.value) { message.value = '当前取景已生效，无需重复保存'; return }
  const ok = savePortraitSetting(props.node.id, url.value, image.value, reset ? null : { ...draft })
  if (ok) resetDraft()
  message.value = ok ? reset ? '已恢复默认取景' : '已保存到本机，图谱与名册已同步' : '本机存储不可用，未保存'
}
function point(event: MouseEvent) {
  if (!image.value) return
  modified.value = true
  draft.mode = 'portrait'
  const bounds = (event.currentTarget as HTMLImageElement).getBoundingClientRect()
  const f = portraitFrame(props.node.id, url.value, image.value, 'auto', props.node.characterType, [{ ...draft, reviewed: true, entityUid: props.node.id, imageFingerprint: `${url.value}|${image.value.width}x${image.value.height}` }])
  if (f.width > 100) draft.focalX = Math.max(0, Math.min(1, ((event.clientX-bounds.left)/bounds.width*f.width-50)/(f.width-100)))
  if (f.height > 100) draft.focalY = Math.max(0, Math.min(1, ((event.clientY-bounds.top)/bounds.height*f.height-50)/(f.height-100)))
}
</script>
<template>
  <details v-if="url" class="portrait-preview"><summary>查看完整图片 / 调整头像</summary>
    <p>点击原图选择位置，再调整缩放。取景仅保存在这台设备。</p>
    <img class="portrait-original" :src="url" :alt="node.label" loading="lazy" referrerpolicy="no-referrer" @click="point">
    <template v-if="image"><div class="portrait-editor"><span class="portrait-crop"><img :src="url" :style="preview" alt="头像取景预览" referrerpolicy="no-referrer"></span><div class="portrait-controls">
      <label>显示<select v-model="draft.mode" @change="modified=true"><option value="portrait">头像取景</option><option value="full">完整图片</option></select></label>
      <label>横向位置<input v-model.number="draft.focalX" @input="modified=true" type="range" min="0" max="1" step="0.01"></label>
      <label>纵向位置<input v-model.number="draft.focalY" @input="modified=true" type="range" min="0" max="1" step="0.01"></label>
      <label>放大 {{ draft.scale.toFixed(1) }} 倍<input v-model.number="draft.scale" @input="modified=true" type="range" min="1" max="3" step="0.05"></label>
    </div></div><div class="actions"><button type="button" @click="save()">保存头像取景</button><button type="button" @click="save(true)">恢复默认</button></div></template>
    <p v-if="message" role="status">{{ message }}</p>
  </details>
</template>
<style scoped>
.portrait-preview{margin:12px 0;font-size:12px;color:#557c6b}.portrait-preview summary{cursor:pointer;padding:7px 0}.portrait-original{display:block;max-width:100%;max-height:300px;width:auto;height:auto;cursor:crosshair;background:#eef3eb;margin:8px auto}.portrait-editor{display:flex;align-items:center;gap:16px;margin:12px 0}.portrait-crop{position:relative;display:block;width:90px;height:90px;flex-shrink:0;border-radius:50%;overflow:hidden;background:#e8eee2}.portrait-crop img{position:absolute;max-width:none}.portrait-controls{flex:1;min-width:0}.portrait-controls label{display:block;font-size:11px}.portrait-controls input,.portrait-controls select{width:100%;min-width:0}.actions{flex-wrap:wrap}
</style>
