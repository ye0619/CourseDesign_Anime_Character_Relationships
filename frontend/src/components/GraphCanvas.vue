<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { init, use, type EChartsType } from 'echarts/core'
import { GraphChart, type GraphSeriesOption } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { type Graph, title } from '../types/catalog'
use([GraphChart, TooltipComponent, CanvasRenderer])
const props = defineProps<{ graph: Graph; circular?: boolean }>()
const emit = defineEmits<{ select: [id: string]; edge: [id: string] }>()
const container = ref<HTMLDivElement>()
let chart: EChartsType | undefined, observer: ResizeObserver | undefined
const colors = { ANIME: '#37645a', PERSON: '#bc9168', CHARACTER: '#8e80ad' }
function render() {
  const option: GraphSeriesOption = { type: 'graph', layout: props.circular ? 'circular' : 'force', roam: true, draggable: true, left: 70, right: 70, top: 65, bottom: 65, scaleLimit: { min: 0.3, max: 3 }, force: { repulsion: 500, edgeLength: [100, 180], layoutAnimation: false, initLayout: 'circular' }, label: { show: true, position: 'bottom', fontSize: 11, color: '#233e38', formatter: (p) => p.name.length > 14 ? `${p.name.slice(0, 14)}…` : p.name }, data: props.graph.nodes.map(n => ({ id: n.uid, name: title(n), symbolSize: n.type === 'ANIME' ? 45 : 32, itemStyle: { color: colors[n.type], borderWidth: 2, borderColor: '#fff' } })), links: props.graph.edges.map((e, i) => ({ id: e.uid, source: e.source, target: e.target, name: String(e.properties?.roleName || e.properties?.roleCode || e.type), symbol: ['none', 'arrow'], symbolSize: [0, 6], lineStyle: { curveness: 0.06 + i % 3 * 0.04, color: '#9bab9e', width: e.type === 'COLLABORATED' ? Math.min(6, 1 + Number(e.properties?.workCount || 1)) : 1.5 }, label: { show: props.graph.edges.length < 35, formatter: () => String(e.properties?.roleName || e.properties?.label || ({ APPEARS_IN: '出场', VOICED: '配音', COLLABORATED: `${e.properties?.workCount} 部` } as Record<string, string>)[e.type] || '关联'), fontSize: 10, color: '#6a7b70' } })), emphasis: { focus: 'adjacency' } }
  chart?.setOption({ animation: !matchMedia('(prefers-reduced-motion: reduce)').matches, tooltip: { renderMode: 'richText', confine: true }, series: [option] }, { notMerge: true })
}
function exportImage() { if (!chart) return; const a = document.createElement('a'); a.href = chart.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#f8f9f4' }); a.download = '映间-关系图谱.png'; a.click() }
function exportData() { const url = URL.createObjectURL(new Blob([JSON.stringify(props.graph, null, 2)], { type: 'application/json' })); const a = document.createElement('a'); a.href = url; a.download = '映间-当前图谱.json'; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
onMounted(() => { if (!container.value) return; chart = init(container.value); chart.on('click', (event) => { const data = event.data as { id?: string }; if (data?.id) event.dataType === 'edge' ? emit('edge', data.id) : emit('select', data.id) }); observer = new ResizeObserver(() => chart?.resize()); observer.observe(container.value); render() })
watch(() => [props.graph, props.circular], render, { deep: true })
onUnmounted(() => { observer?.disconnect(); chart?.dispose() })
</script>
<template><div class="graph-stage"><div class="graph-tools"><span>● 作品 · <span style="color:#bc9168">● 人员</span> · <span style="color:#8e80ad">● 角色</span></span><button @click="render">重置视图</button><button @click="exportImage">保存图片</button><button @click="exportData">导出当前数据</button></div><div ref="container" class="generic-canvas" role="img" :aria-label="`关系图谱，${graph.nodes.length} 个节点，${graph.edges.length} 条关系。可通过旁边的节点列表浏览。`"></div></div></template>
