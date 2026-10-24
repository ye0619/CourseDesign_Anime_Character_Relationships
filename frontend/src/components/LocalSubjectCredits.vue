<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import WorkRelationships from './WorkRelationships.vue'
const props = withDefaults(defineProps<{ uid: string; initialSection?: string; workTitle?: string }>(), { initialSection: 'characters' })
const route = useRoute()
const initialView = computed(() => (route.query.section || props.initialSection) === 'staff' ? 'STAFF' : (route.query.section || props.initialSection) === 'cast' ? 'CREDITS' : 'CHARACTERS')
</script>
<template><WorkRelationships :uid="uid" :subject-id="uid.split(':').at(-1)!" :work-title="workTitle" source="LOCAL" :initial-view="initialView"/></template>
