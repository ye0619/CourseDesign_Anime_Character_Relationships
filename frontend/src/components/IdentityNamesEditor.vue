<script setup lang="ts">
import {useIdentityNames} from '../composables/useIdentityNames'
import type {ReviewedIdentity} from '../types/identityReview'
const props=defineProps<{identity:ReviewedIdentity;busy:boolean}>()
const emit=defineEmits<{busy:[value:boolean];changed:[message:string];unauthorized:[]}>()
const {detail,draft,reason,error,loading,saving,load,save}=useIdentityNames(()=>props.identity,{busy:value=>emit('busy',value),changed:message=>emit('changed',message),unauthorized:()=>emit('unauthorized')})
</script>
<template>
  <section class="name-editor"><h3>已审核来源的检索别名</h3><p>只增加检索入口，保留原名和当前显示名。每个别名须有本页修订中的明确依据；身份状态或版本变化后旧别名失效。</p>
    <p v-if="error" role="alert">{{ error }} <button :disabled="saving" @click="load">重新读取</button></p><p v-if="loading" role="status">正在读取别名…</p>
    <template v-if="detail"><p v-if="!detail.activeForSearch">旧别名当前不参与检索；重新审核身份也不会自动恢复。需要重新核对后显式保存。</p>
      <ul v-if="detail.names.length"><li v-for="(name,index) in detail.names" :key="index">{{ name.name }}（{{ name.locale }}） · {{ detail.activeForSearch?'参与检索':'已失效' }} · 修订 {{ name.sourceRevision }}</li></ul>
      <p v-if="identity.status!=='REVIEWED'">当前身份尚未审核，不能登记检索别名。</p>
      <form v-else @submit.prevent="save"><fieldset :disabled="busy||saving||loading"><legend>核对来源修订 {{ identity.sourceRevision }}</legend>
        <article v-for="(name,index) in draft" :key="index" class="name-fields"><label>别名<input v-model="name.name" required maxlength="200"></label><label>语言<select v-model="name.locale"><option value="zh-Hans">简体中文</option><option value="zh-Hant">繁体中文</option><option value="ja">日语</option><option value="en">英语</option><option value="und">未确定</option></select></label><label>此名称的来源依据<textarea v-model="name.evidenceText" required maxlength="1000" rows="2"></textarea></label><button type="button" @click="draft.splice(index,1)">移除此项</button></article>
        <button type="button" :disabled="draft.length>=20" @click="draft.push({name:'',locale:'und',evidenceText:''})">添加别名（{{ draft.length }}/20）</button>
        <label>保存理由<textarea v-model="reason" required maxlength="500" rows="2"></textarea></label><p v-if="!draft.length">保存空列表将明确清除当前身份的检索别名。</p><button>{{ saving?'正在保存…':'保存已核验别名' }}</button>
      </fieldset></form>
    </template>
  </section>
</template>
<style scoped>.name-editor{border-top:1px solid #dce6d1;margin-top:24px;padding-top:14px}.name-editor p,.name-editor li{font-size:12px;line-height:1.8}.name-editor fieldset{display:grid;gap:14px;border:1px solid #dce6d1;padding:16px;min-width:0}.name-fields{display:grid;gap:12px;border-bottom:1px solid #dce6d1;padding-bottom:16px}.name-editor textarea{width:100%;box-sizing:border-box}</style>
