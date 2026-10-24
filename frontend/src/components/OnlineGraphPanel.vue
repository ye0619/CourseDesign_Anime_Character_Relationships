<script setup lang="ts">
import WorkGraphPanel from './WorkGraphPanel.vue'
defineProps<{ subjectId: string; workTitle?: string }>()
</script>
<template><WorkGraphPanel :uid="`anime:bangumi:${subjectId}`" source="ONLINE" :work-title="workTitle"/></template>
