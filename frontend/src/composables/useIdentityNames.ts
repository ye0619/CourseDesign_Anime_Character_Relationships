import { onScopeDispose, ref, shallowRef, watch } from 'vue'
import { ApiError, get, mutate } from '../api/client'
import type { ReviewedIdentity } from '../types/identityReview'
export interface ReviewedNameInput {name:string;locale:string;evidenceText:string}
export interface StoredIdentityName extends ReviewedNameInput {sourceUrl:string;sourceRevision:string;licenseLabel:string;canonicalUid:string;reviewVersion:number;provider:string;providerId:string}
export interface IdentityNamesDetail {identity:ReviewedIdentity;names:StoredIdentityName[];activeForSearch:boolean}
export function useIdentityNames(identity:()=>ReviewedIdentity,events:{busy:(value:boolean)=>void;changed:(message:string)=>void;unauthorized:()=>void}){
  const detail=shallowRef<IdentityNamesDetail>(),draft=ref<ReviewedNameInput[]>([]),reason=shallowRef(''),error=shallowRef(''),loading=shallowRef(false),saving=shallowRef(false)
  let controller:AbortController|undefined,generation=0,disposed=false
  function fail(e:unknown){error.value=e instanceof Error?e.message:'别名维护请求失败';if(e instanceof ApiError&&[401,403].includes(e.status))events.unauthorized()}
  async function load(){
    controller?.abort();const current=new AbortController(),version=++generation;controller=current;loading.value=true;detail.value=undefined;draft.value=[];reason.value='';error.value=''
    try{const result=await get<IdentityNamesDetail>(`/admin/external-identities/${identity().id}/names`,current.signal);if(disposed||version!==generation)return;if(result.identity.version!==identity().version){events.changed('身份记录已变化，请重新选择后核对别名。');return}detail.value=result;if(result.activeForSearch)draft.value=result.names.map(({name,locale,evidenceText})=>({name,locale,evidenceText}))}
    catch(e){if(!current.signal.aborted&&!disposed&&version===generation)fail(e)}finally{if(!disposed&&version===generation)loading.value=false}
  }
  async function save(){
    if(saving.value||loading.value||!detail.value||identity().status!=='REVIEWED')return
    const current=identity(),version=generation,input={expectedVersion:current.version,reason:reason.value,names:draft.value.map(n=>({...n}))};saving.value=true;events.busy(true);error.value=''
    try{await mutate(`/admin/external-identities/${current.id}/names`,'PUT',input);if(!disposed&&version===generation)events.changed('检索别名已保存。请重新选择身份核对；公开页面重新读取后生效。')}
    catch(e){if(!disposed&&version===generation){if(e instanceof ApiError&&e.status===409){draft.value=[];events.changed('身份记录已变化，旧别名表单已清空，请重新选择后核对。')}else fail(e)}}finally{saving.value=false;events.busy(false)}
  }
  watch(()=>[identity().id,identity().version],()=>void load(),{immediate:true})
  onScopeDispose(()=>{disposed=true;generation++;controller?.abort()})
  return {detail,draft,reason,error,loading,saving,load,save}
}
