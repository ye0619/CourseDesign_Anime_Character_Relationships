<script setup lang="ts">
import { computed } from 'vue'
import { usePortraitFrames,usePortraitMode } from '../composables/usePortraitFrames'
import { safePortraitUrl } from '../utils/portraitFraming'
import type { CharacterNode } from '../types/characterGraph'
const props=defineProps<{node:CharacterNode}>()
const frames=usePortraitFrames(()=>[props.node],usePortraitMode())
const url=computed(()=>frames.value.get(props.node.id)?.url||safePortraitUrl(props.node.imageUrl))
</script>
<template><details v-if="url" class="portrait-preview"><summary>查看完整图片</summary><img :src="url" :alt="node.label" loading="lazy" referrerpolicy="no-referrer"><p>使用“编辑角色”调整通用或本作取景，保存后各页面统一生效。</p></details></template>
<style scoped>.portrait-preview{margin:12px 0;font-size:12px}.portrait-preview summary{cursor:pointer;padding:8px 0}.portrait-preview img{display:block;max-width:100%;max-height:300px;width:auto;height:auto;margin:10px auto}.portrait-preview p{line-height:1.8}</style>
