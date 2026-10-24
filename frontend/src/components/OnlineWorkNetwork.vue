<script setup lang="ts">
import WorkRelationships from './WorkRelationships.vue'
defineProps<{ subjectId: string; workTitle?: string }>()
</script>
<template><WorkRelationships :subject-id="subjectId" :work-title="workTitle" source="ONLINE"/></template>
