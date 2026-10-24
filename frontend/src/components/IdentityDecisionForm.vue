<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import IdentityTargetPreview from './IdentityTargetPreview.vue'
import { identityActions, type IdentityDecision, type ReviewedIdentity } from '../types/identityReview'
const props=defineProps<{identity:ReviewedIdentity;busy:boolean}>()
const emit=defineEmits<{save:[id:number,input:IdentityDecision]}>()
const form=reactive({decision:'REVIEW',reason:'',targetUid:'',newRevision:'',newEvidence:'',newSourceUrl:'',newLicenseLabel:''})
const titleOnly=computed(()=>props.identity.matchMethod==='TITLE_ONLY')
const choices=computed(()=>props.identity.status==='CANDIDATE'?(titleOnly.value?['AMEND','REMAP','REJECT']:['REVIEW','AMEND','REMAP','REJECT']):props.identity.status==='REVIEWED'?['REVOKE','REMAP']:['REOPEN'])
const proof=computed(()=>['AMEND','REOPEN'].includes(form.decision))
watch(()=>props.identity,identity=>Object.assign(form,{decision:choices.value[0],reason:'',targetUid:'',newRevision:identity.sourceRevision||'',newEvidence:identity.evidenceText||'',newSourceUrl:identity.sourceUrl,newLicenseLabel:identity.licenseLabel}),{immediate:true})
function save(){emit('save',props.identity.id,{expectedVersion:props.identity.version,decision:form.decision,reason:form.reason,...(form.decision==='REMAP'?{targetUid:form.targetUid}:{}),...(proof.value?{newRevision:form.newRevision,newEvidence:form.newEvidence,newSourceUrl:form.newSourceUrl,newLicenseLabel:form.newLicenseLabel}:{})})}
</script>
<template><form class="decision" @submit.prevent="save"><h3>核对后操作</h3><p v-if="titleOnly">仅标题对应的候选尚未确认身份。请补充原名、作品归属和来源引用依据；原样提交标题元信息不能进入人工审核。</p><label>操作<select v-model="form.decision"><option v-for="action in choices" :key="action" :value="action">{{ identityActions[action] }}</option></select></label><template v-if="form.decision==='REMAP'"><p>改绑后重新进入待审核状态。已有关系不会迁移；错误的关系证据需要单独撤回。</p><label>新的稳定实体编号<input v-model="form.targetUid" required maxlength="200"></label><IdentityTargetPreview :uid="form.targetUid" :disabled="busy"/></template><template v-if="proof"><p v-if="form.decision==='REOPEN'">须提供与原记录不同的新修订和核验原文；重开后仍为候选。</p><label>来源修订<input v-model="form.newRevision" required maxlength="100"></label><label>来源链接<input v-model="form.newSourceUrl" type="url" required maxlength="1000"></label><label>许可与署名<input v-model="form.newLicenseLabel" required maxlength="200"></label><label>核验原文<textarea v-model="form.newEvidence" required maxlength="2000" rows="4"></textarea></label></template><label>操作理由<textarea v-model="form.reason" required maxlength="500" rows="2"></textarea></label><button :disabled="busy">{{ busy?'正在提交…':identityActions[form.decision] }}</button><small>当前记录版本 {{ identity.version }}。其他维护者更新后，旧表单会被拒绝。</small></form></template>
<style scoped>.decision{display:grid;gap:14px}textarea{width:100%;box-sizing:border-box}p,small{font-size:12px;line-height:1.8}small{color:#70846b}</style>
