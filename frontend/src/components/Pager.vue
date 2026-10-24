<script setup lang="ts">
defineProps<{ page: number; total: number; size: number; loading?: boolean }>()
defineEmits<{ change: [page: number] }>()
</script>
<template><div class="pager"><span>共 {{ total }} 条 · 第 {{ page }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span><button :disabled="loading || page <= 1" @click="$emit('change', page - 1)">上一页</button><button :disabled="loading || page * size >= total" @click="$emit('change', page + 1)">下一页</button></div></template>
