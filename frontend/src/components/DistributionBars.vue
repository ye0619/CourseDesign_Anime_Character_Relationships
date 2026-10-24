<script setup lang="ts">
import { computed } from 'vue'
import { useRouter,type RouteLocationRaw } from 'vue-router'
import type { Bucket } from '../types/viewer'
import ReportChart from './ReportChart.vue'
const props=defineProps<{items:Bucket[];label:string;unit?:string;linkFor?:(item:Bucket)=>RouteLocationRaw}>()
const router=useRouter()
const rows=computed(()=>props.items.map((n,i)=>({id:String(i),label:n.label,count:n.count})))
const mode=computed(()=>/评分|年份/.test(props.label)?'histogram':'bar')
function select(id:string){const item=props.items[Number(id)];if(item&&props.linkFor)void router.push(props.linkFor(item))}
</script>
<template><ReportChart :items="rows" :label="label" :unit="unit" :mode="mode" @select="select($event.id)"/></template>
