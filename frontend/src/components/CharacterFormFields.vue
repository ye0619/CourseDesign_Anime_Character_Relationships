<script setup lang="ts">
import { shallowRef } from 'vue'
import MultilingualNameEditor from './MultilingualNameEditor.vue'
import type { LocalName } from '../types/localGraph'
export interface CharacterFields { names?:LocalName[]; preferredNameId?:string|null; imageUrl:string; gender:string; description:string; notes:string }
defineProps<{hideGender?:boolean}>()
const draft=defineModel<CharacterFields>({required:true})
const error=shallowRef('')
async function upload(event:Event){const file=(event.target as HTMLInputElement).files?.[0];if(!file)return;error.value='';if(file.size>512*1024||!['image/png','image/jpeg','image/webp'].includes(file.type)){error.value='请选择512KB以内的 PNG、JPEG 或 WebP 图片';return}const current=draft.value;const reader=new FileReader();reader.onload=()=>{if(draft.value===current)draft.value.imageUrl=String(reader.result)};reader.onerror=()=>error.value='图片读取失败';reader.readAsDataURL(file)}
</script>
<template><div class="character-fields"><MultilingualNameEditor v-model="draft.names!" v-model:preferred="draft.preferredNameId"/><label v-if="!hideGender">性别<input v-model="draft.gender" maxlength="40" placeholder="未知可留空"></label><label>头像（512KB以内）<input type="file" accept="image/png,image/jpeg,image/webp" @change="upload"></label><button v-if="draft.imageUrl" type="button" @click="draft.imageUrl=''">清空头像</button><p v-if="error" role="alert">{{ error }}</p><label>简介<textarea v-model="draft.description" rows="3" maxlength="4000"></textarea></label><label>备注<textarea v-model="draft.notes" rows="2" maxlength="2000"></textarea></label></div></template>
<style scoped>.character-fields{display:grid;gap:14px}.character-fields label{display:grid;gap:7px;font-size:12px}.character-fields textarea{width:100%;padding:10px;border:1px solid #ccd9d1;border-radius:5px;background:var(--panel,#fcfdf9);color:inherit;font:inherit}.character-fields>button{justify-self:start}</style>
