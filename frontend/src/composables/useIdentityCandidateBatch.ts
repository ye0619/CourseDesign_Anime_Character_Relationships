import { computed, onScopeDispose, readonly, ref, shallowRef } from 'vue'
import { ApiError, mutate } from '../api/client'
import type { IdentityCandidate } from '../types/identityReview'
import { parseIdentityCandidateFile } from '../utils/identityCandidateFile'

type Outcome='pending'|'sending'|'saved'|'failed'|'uncertain'
interface BatchRow {candidate:IdentityCandidate;outcome:Outcome;message:string}
export function useIdentityCandidateBatch(onUnauthorized:()=>void){
  const rows=ref<BatchRow[]>([]),running=shallowRef(false),error=shallowRef(''),paused=shallowRef(false)
  let disposed=false,stopRequested=false
  const counts=computed(()=>({saved:rows.value.filter(r=>r.outcome==='saved').length,pending:rows.value.filter(r=>r.outcome==='pending').length}))
  function preview(text:string){if(running.value)return;rows.value=[];error.value='';paused.value=false;if(!text)return;try{rows.value=parseIdentityCandidateFile(text).map(candidate=>({candidate,outcome:'pending',message:'待登记'}))}catch(e){error.value=e instanceof Error?e.message:'无法读取文件'}}
  function stop(){stopRequested=true;paused.value=true}
  async function start(){
    if(running.value||disposed||!counts.value.pending)return
    running.value=true;stopRequested=false;paused.value=false
    try{for(const row of rows.value){
      if(disposed||stopRequested)break
      if(row.outcome!=='pending')continue
      row.outcome='sending';row.message='正在登记'
      try{await mutate('/admin/external-identities','POST',row.candidate);if(disposed)break;row.outcome='saved';row.message='已登记候选，尚未审核'}
      catch(e){
        if(disposed)break
        if(e instanceof ApiError&&e.status>=400&&e.status<500&&e.code!=='INVALID_RESPONSE'){
          row.outcome='failed';row.message=e.status===409?'来源编号已存在，请读取原记录核对':e.message
          if(e.status===401||e.status===403){stop();onUnauthorized();break}
        }else{row.outcome='uncertain';row.message='提交结果未确认，请读取身份记录核对；不会自动重试';stop()}
      }
    }}finally{running.value=false}
  }
  onScopeDispose(()=>{disposed=true;stopRequested=true})
  return {rows:readonly(rows),running:readonly(running),error:readonly(error),paused:readonly(paused),counts,preview,start,stop}
}
