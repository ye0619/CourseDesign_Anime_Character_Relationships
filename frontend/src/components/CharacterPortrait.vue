<script setup lang="ts">
import { computed } from 'vue'
import type { CharacterNode } from '../types/characterGraph'
import { usePortraitFrames, usePortraitMode } from '../composables/usePortraitFrames'
import { portraitImageStyle } from '../utils/portraitFraming'
const props = defineProps<{ node: CharacterNode; large?: boolean }>()
const frames = usePortraitFrames(() => [props.node], usePortraitMode())
const portrait = computed(() => frames.value.get(props.node.id)!)
const imageStyle = computed(() => portraitImageStyle(portrait.value.frame))
</script>
<template><span class="portrait" :class="{ large }" aria-hidden="true" :title="portrait.failed ? '图片暂不可用' : undefined"><img v-if="portrait.url" :src="portrait.url" :style="imageStyle" alt="" referrerpolicy="no-referrer"><span v-else>{{ node.label.slice(0, 1) }}</span></span></template>
<style scoped>.portrait{position:relative;display:inline-grid;place-items:center;flex-shrink:0;width:38px;height:38px;border-radius:50%;background:#e8eee2;color:#688372;overflow:hidden;box-shadow:0 0 0 1px #0000000a}.portrait img{position:absolute;max-width:none;object-fit:fill}.portrait.large{width:64px;height:64px;font-size:25px}</style>
