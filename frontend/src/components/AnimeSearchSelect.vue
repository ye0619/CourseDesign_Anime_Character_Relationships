<script setup lang="ts">
import { computed, shallowRef, useId, useTemplateRef, watch } from 'vue'
import { title, type Entity } from '../types/catalog'
import { useAnimeSuggestions, type SuggestionFilter } from '../composables/useAnimeSuggestions'
const model = defineModel<string>({ default: '' })
const props = withDefaults(defineProps<{ label?: string; filter?: SuggestionFilter; placeholder?: string }>(), { label: '名称搜索', placeholder: '输入中文名或原名（至少2字）', filter: () => ({ mode: 'ONLINE' }) })
const emit = defineEmits<{ select: [entity: Entity]; search: [] }>()
const id = useId(), root = useTemplateRef<HTMLDivElement>('root')
const focused = shallowRef(false), composing = shallowRef(false), closed = shallowRef(false), activeIndex = shallowRef(-1)
const { items, pending, error, notice, retry } = useAnimeSuggestions(() => model.value, () => composing.value, () => props.filter)
const expanded = computed(() => focused.value && !closed.value && !composing.value && Array.from(model.value.trim()).length >= 2)
watch(model, () => { closed.value = false; activeIndex.value = -1 }, { flush: 'sync' })
watch(items, () => { activeIndex.value = -1 })
function choose(entity: Entity) { model.value = title(entity); closed.value = true; activeIndex.value = -1; emit('select', entity) }
function keydown(event: KeyboardEvent) {
  if (event.isComposing || composing.value) return
  if (event.key === 'Escape') { closed.value = true; event.preventDefault(); return }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault(); closed.value = false
    if (items.value.length) activeIndex.value = activeIndex.value < 0 ? (event.key === 'ArrowDown' ? 0 : items.value.length - 1) : (activeIndex.value + (event.key === 'ArrowDown' ? 1 : -1) + items.value.length) % items.value.length
  }
  if (event.key === 'Enter') {
    event.preventDefault()
    const entity = expanded.value ? items.value[activeIndex.value] : undefined
    if (entity) choose(entity); else { closed.value = true; emit('search') }
  }
}
function focusout(event: FocusEvent) { if (!root.value?.contains(event.relatedTarget as Node | null)) { focused.value = false; closed.value = true } }
</script>
<template>
  <div ref="root" class="anime-select" @focusout="focusout">
    <label :for="id">{{ label }}</label>
    <input :id="id" v-model="model" type="search" maxlength="100" autocomplete="off" :placeholder="placeholder" role="combobox" aria-autocomplete="list" :aria-expanded="expanded" :aria-controls="`${id}-options`" :aria-activedescendant="expanded && activeIndex >= 0 ? `${id}-option-${activeIndex}` : undefined" @focus="focused = true; closed = false" @keydown="keydown" @compositionstart="composing = true" @compositionend="composing = false">
    <div v-if="expanded" class="suggestion-popup">
      <p v-if="pending" role="status">正在找候选作品…</p>
      <p v-else-if="error" role="status">{{ error }} <button type="button" @click="retry">重试</button></p>
      <p v-else-if="!items.length" role="status">暂无候选，可继续输入或搜索全部结果。</p>
      <ul :id="`${id}-options`" role="listbox" :aria-label="`${label}候选`">
        <li v-for="(entity, index) in items" :id="`${id}-option-${index}`" :key="entity.uid" role="option" :aria-selected="index === activeIndex" @pointerdown.prevent="choose(entity)" @mouseenter="activeIndex = index">
          <img v-if="entity.imageUrl" class="suggestion-cover" :src="entity.imageUrl" alt="" referrerpolicy="no-referrer"><span v-else class="suggestion-cover no-cover">番</span>
          <span class="suggestion-text"><strong>{{ title(entity) }}</strong><small v-if="entity.name !== title(entity)">{{ entity.name }}</small><small>{{ entity.year || '年份未知' }} · {{ entity.format || '形式未标注' }}</small></span>
        </li>
      </ul>
      <p v-if="notice" class="suggestion-notice">{{ notice }}</p>
      <button type="button" class="all-results" @click="closed = true; emit('search')">查看全部搜索结果</button>
    </div>
  </div>
</template>
<style scoped>
.anime-select{position:relative;min-width:0}.anime-select>label{display:block;font-size:12px;margin-bottom:6px}.anime-select>input{width:100%;min-width:0}.suggestion-popup{position:absolute;top:100%;left:0;width:100%;min-width:min(310px,80vw);max-width:90vw;z-index:80;background:#fcfdf9;border:1px solid #b7ccbc;box-shadow:0 12px 25px #40594f1a;border-radius:8px;overflow:hidden}.suggestion-popup ul{list-style:none;margin:0;padding:3px;max-height:340px;overflow:auto}.suggestion-popup li{display:flex;gap:12px;padding:9px;text-align:left;cursor:pointer;border-radius:5px}.suggestion-popup li[aria-selected=true]{background:#e7f0e3;outline:1px solid #b7ccbc}.suggestion-cover{width:35px;height:48px;object-fit:cover;flex-shrink:0;background:#e7f0e3}.no-cover{display:grid;place-items:center}.suggestion-text{min-width:0;display:grid;gap:3px}.suggestion-text strong{font-size:13px;color:#40594f;overflow-wrap:anywhere}.suggestion-text small{font-size:11px;color:#657e70;overflow-wrap:anywhere}.suggestion-popup p{font-size:12px;padding:10px;margin:0}.all-results{width:100%;border:0;border-top:1px solid #dfe8d7;border-radius:0;background:#f0f5ed;text-align:center;font-size:12px;padding:12px}
</style>
