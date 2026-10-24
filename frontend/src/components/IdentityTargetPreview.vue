<script setup lang="ts">
import { computed,onScopeDispose,shallowRef,watch } from 'vue'
import { get } from '../api/client'
import { entityLink,title,type Entity } from '../types/catalog'
const props=defineProps<{uid:string;disabled?:boolean}>()
const target=shallowRef<Entity>(),error=shallowRef(''),pending=shallowRef(false)
const valid=computed(()=>/^(character|person|anime):[a-z0-9_-]+:[A-Za-z0-9_-]{1,100}$/.test(props.uid))
const collections:Record<string,string>={character:'characters',person:'people',anime:'anime'}
let controller:AbortController|undefined
watch(()=>props.uid,()=>{controller?.abort();target.value=undefined;error.value='';pending.value=false})
async function inspect(){
  if(!valid.value)return;controller?.abort();const request=new AbortController(),uid=props.uid;controller=request;pending.value=true;error.value=''
  const collection=collections[uid.split(':')[0]!]!
  try{const result=await get<Entity>(`/${collection}/${encodeURIComponent(uid)}`,request.signal);if(!request.signal.aborted&&props.uid===uid)target.value=result}
  catch(e){if(!request.signal.aborted)error.value=e instanceof Error?e.message:'目标档案暂不可用'}
  finally{if(!request.signal.aborted)pending.value=false}
}
onScopeDispose(()=>controller?.abort())
</script>
<template><div class="target-preview"><button type="button" :disabled="!valid||pending||disabled" @click="inspect">{{ pending?'正在核对…':'核对目标档案' }}</button><p v-if="target"><strong>{{ title(target) }}</strong> · {{ target.name }} <a :href="entityLink(target)" target="_blank" rel="noopener noreferrer">打开档案</a></p><p v-if="error" role="alert">{{ error }}</p></div></template>
<style scoped>.target-preview{font-size:12px;line-height:1.8;overflow-wrap:anywhere}.target-preview a{text-decoration:underline}</style>
