<script setup lang="ts">
import { onScopeDispose, shallowRef } from 'vue'
import { useIdentityCandidateBatch } from '../composables/useIdentityCandidateBatch'
import { candidateFileLimit } from '../utils/identityCandidateFile'
const props=defineProps<{busy:boolean}>()
const emit=defineEmits<{busy:[value:boolean];changed:[];unauthorized:[]}>()
let sessionLost=false,disposed=false
const {rows,running,error,paused,counts,preview,start,stop}=useIdentityCandidateBatch(()=>{sessionLost=true;emit('unauthorized')})
const reading=shallowRef(false),fileError=shallowRef('')
let generation=0
onScopeDispose(()=>{generation++;disposed=true})
async function readFile(event:Event){
  const file=(event.target as HTMLInputElement).files?.[0],token=++generation
  preview('');fileError.value='';reading.value=false
  if(!file){fileError.value='请选择候选清单';return}
  if(file.size>candidateFileLimit){fileError.value='候选文件不能超过512 KiB';return}
  reading.value=true
  try{const text=await file.text();if(token===generation)preview(text)}catch{if(token===generation)fileError.value='无法读取文件'}finally{if(token===generation)reading.value=false}
}
async function register(){if(props.busy||running.value)return;emit('busy',true);try{await start()}finally{emit('busy',false);if(!sessionLost&&!disposed)emit('changed')}}
</script>
<template>
  <section class="panel candidate-batch">
    <h2>来源候选清单</h2>
    <p>选择版本1的JSON清单预览，每份最多30条、512 KiB。标题对应只用于发现候选；审核前须补充原名、作品和来源依据。</p>
    <label>候选文件<input type="file" accept="application/json,.json" :disabled="busy||running||reading" @change="readFile"></label>
    <p v-if="fileError||error" role="alert">{{ fileError||error }}</p>
    <template v-if="rows.length">
      <p role="status">共 {{ rows.length }} 条；已登记 {{ counts.saved }} 条；未提交 {{ counts.pending }} 条。每条独立保存，重复来源编号不会覆盖。</p>
      <div class="batch-rows"><article v-for="(row,index) in rows" :key="index" class="batch-row"><strong>{{ row.candidate.provider }} · {{ row.candidate.providerId }}</strong><p>{{ row.candidate.canonicalUid }}</p><p>{{ row.candidate.evidenceText }}</p><a :href="row.candidate.sourceUrl" target="_blank" rel="noopener noreferrer">核对来源原页</a><p>{{ row.message }}</p></article></div>
      <button :disabled="busy||running||!counts.pending" @click="register">{{ paused?'继续登记未提交项':'逐条登记为候选' }}</button>
      <button v-if="running" :disabled="paused" @click="stop">当前请求完成后暂停</button>
      <p>网络结果未确认时会暂停；请先核对记录。失败项不会自动重试，已登记项不会再次提交。</p>
    </template>
  </section>
</template>
<style scoped>.candidate-batch{display:grid;gap:14px;margin-bottom:24px}.batch-rows{max-height:420px;overflow:auto;border:1px solid #dce6d1}.batch-row{padding:14px;border-bottom:1px solid #dce6d1;overflow-wrap:anywhere}.batch-row p{white-space:pre-wrap;line-height:1.7}.batch-row a{text-decoration:underline}</style>
