<script setup lang="ts">
import { computed,onMounted,onBeforeUnmount,shallowRef,watch } from 'vue'
import { init,use,type EChartsType } from 'echarts/core'
import { BarChart,ScatterChart } from 'echarts/charts'
import { GridComponent,TooltipComponent,AriaComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
use([BarChart,ScatterChart,GridComponent,TooltipComponent,AriaComponent,CanvasRenderer])
export interface ChartDatum { id:string; label:string; count:number; secondaryCount?:number; score?:number|null; sample?:number; hint?:string }
const props=withDefaults(defineProps<{items:ChartDatum[];label:string;mode?:'bar'|'histogram'|'scatter'|'grouped';comparison?:{first:string;second:string};unit?:string}>(),{mode:'bar',unit:'部'})
const emit=defineEmits<{select:[item:ChartDatum]}>()
const host=shallowRef<HTMLDivElement>(),tableOpen=shallowRef(false)
const height=computed(()=>['bar','grouped'].includes(props.mode)?Math.max(180,Math.min(680,props.items.length*30+45)):280)
const rendered=computed(()=>props.mode==='scatter'?props.items.filter(n=>n.score!=null&&(n.sample||0)>=3):props.items)
let chart:EChartsType|undefined,observer:ResizeObserver|undefined,media:MediaQueryList|undefined
function draw(){if(!host.value)return;if(!chart){chart=init(host.value,undefined,{renderer:'canvas'});chart.on('click',event=>{if(typeof event.dataIndex==='number'){const item=rendered.value[event.dataIndex];if(item)emit('select',item)}})}
  const dark=window.matchMedia('(prefers-color-scheme: dark)').matches,text=dark?'#c7dace':'#536f61'
  const grouped=props.mode==='grouped',scatter=props.mode==='scatter',vertical=props.mode==='histogram'
  chart.setOption({animation:!window.matchMedia('(prefers-reduced-motion: reduce)').matches,animationDuration:200,aria:{enabled:true,label:{description:props.label}},tooltip:{trigger:'item',renderMode:'richText'},grid:{left:scatter?60:vertical?40:140,right:35,top:25,bottom:vertical?50:35},
    xAxis:{type:scatter||!vertical?'value':'category',min:0,...(vertical?{data:rendered.value.map(n=>n.label)}:{}),name:scatter?'作品数':'',axisLabel:{color:text,interval:0,rotate:vertical&&rendered.value.length>12?45:0},splitLine:{lineStyle:{color:dark?'#354c40':'#e6ede7'}}},
    yAxis:{type:scatter||vertical?'value':'category',...(scatter?{min:1,max:10,name:'个人均分'}:vertical?{minInterval:1}:{data:rendered.value.map(n=>n.label),inverse:true}),axisLabel:{color:text,width:125,overflow:'truncate'},splitLine:{show:scatter||vertical,lineStyle:{color:dark?'#354c40':'#e6ede7'}}},
    series:[{name:props.comparison?.first,type:scatter?'scatter':'bar',barMaxWidth:24,symbolSize:scatter?14:undefined,itemStyle:{color:scatter?'#7499a8':'#6d9681',borderRadius:scatter?0:3},label:{show:!scatter,position:vertical?'top':'right',color:text},data:rendered.value.map(n=>({name:n.label,value:scatter?[n.count,n.score]:n.count}))},...(grouped?[{name:props.comparison?.second,type:'bar',barMaxWidth:24,itemStyle:{color:'#7499a8',borderRadius:3},label:{show:true,position:'right',color:text},data:rendered.value.map(n=>({name:n.label,value:n.secondaryCount||0}))}]:[])]},true)
}
watch([()=>props.items,()=>props.mode],()=>draw(),{deep:true})
onMounted(()=>{draw();observer=new ResizeObserver(()=>chart?.resize());if(host.value)observer.observe(host.value);media=window.matchMedia('(prefers-color-scheme: dark)');media.addEventListener('change',draw)})
onBeforeUnmount(()=>{observer?.disconnect();media?.removeEventListener('change',draw);chart?.dispose();chart=undefined})
</script>
<template><div class="report-chart"><p v-if="!rendered.length" class="empty-chart">{{ mode==='scatter'?'至少需要3部已评分作品才展示散点。':'当前没有可统计资料。' }}</p><p v-if="mode==='grouped'">{{ comparison?.first }} / {{ comparison?.second }}，两组数量分别标注</p><div v-show="rendered.length" ref="host" :style="{height:height+'px'}" role="img" :aria-label="label+'；下方可展开数据表并通过键盘查看明细'"></div><button type="button" :aria-expanded="tableOpen" @click="tableOpen=!tableOpen">{{ tableOpen?'收起数据表':'查看数据表 / 键盘选择' }}</button><div v-if="tableOpen" class="table-scroll"><table><caption>{{ label }}</caption><thead><tr><th scope="col">项目</th><th scope="col">{{ comparison?.first||'数量' }}</th><th v-if="mode==='grouped'" scope="col">{{ comparison?.second }}</th><th v-if="mode==='scatter'" scope="col">个人均分 / 样本</th><th scope="col">明细</th></tr></thead><tbody><tr v-for="item in rendered" :key="item.id"><td>{{ item.label }}</td><td>{{ item.count }} {{ unit }}</td><td v-if="mode==='grouped'">{{ item.secondaryCount||0 }} {{ unit }}</td><td v-if="mode==='scatter'">{{ item.score }} / {{ item.sample }}</td><td><button type="button" @click="emit('select',item)">查看{{ item.label }}明细</button><small v-if="item.hint">{{ item.hint }}</small></td></tr></tbody></table></div></div></template>
<style scoped>.report-chart{min-width:0;margin:12px 0 18px}.report-chart>div[role=img]{width:100%;min-width:0}.report-chart>button{font-size:11px;min-height:36px}.report-chart table{font-size:12px}.report-chart td button{font-size:11px}.report-chart small{display:block}.empty-chart{padding:25px 0;font-size:12px;color:var(--muted,#758d7d)}@media(max-width:500px){.report-chart{margin-left:-12px;margin-right:-12px}}</style>
