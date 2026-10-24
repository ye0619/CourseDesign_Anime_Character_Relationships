import { onScopeDispose, shallowRef } from 'vue'
import { ApiError, get, mutate } from '../api/client'
import type { IdentityCandidate, IdentityDecision, IdentityDetail, ReviewedIdentity } from '../types/identityReview'

export function useIdentityReview() {
  const rows=shallowRef<ReviewedIdentity[]>([]),detail=shallowRef<IdentityDetail>(),authorized=shallowRef(false)
  const provider=shallowRef(''),status=shallowRef(''),loading=shallowRef(false),saving=shallowRef(false),error=shallowRef(''),message=shallowRef('')
  let listController:AbortController|undefined,detailController:AbortController|undefined,listGeneration=0,detailGeneration=0,disposed=false
  function expireSession(message='维护会话已过期，请重新建立会话'){listController?.abort();detailController?.abort();listGeneration++;detailGeneration++;loading.value=false;authorized.value=false;rows.value=[];detail.value=undefined;error.value=message}
  function fail(e:unknown){error.value=e instanceof Error?e.message:'维护请求失败';if(e instanceof ApiError&&[401,403].includes(e.status))expireSession(error.value)}
  async function load(beforeId=0){
    listController?.abort();detailController?.abort();detailGeneration++;detail.value=undefined
    const current=++listGeneration,controller=new AbortController();listController=controller;loading.value=true;error.value='';rows.value=[]
    try{const result=await get<ReviewedIdentity[]>(`/admin/external-identities?${new URLSearchParams({provider:provider.value,status:status.value,beforeId:String(beforeId),limit:'30'})}`,controller.signal);if(!disposed&&current===listGeneration){rows.value=result;authorized.value=true}}
    catch(e){if(!controller.signal.aborted&&!disposed&&current===listGeneration)fail(e)}
    finally{if(!disposed&&current===listGeneration)loading.value=false}
  }
  async function select(id:number){
    detailController?.abort();const current=++detailGeneration,controller=new AbortController();detailController=controller;detail.value=undefined;error.value=''
    try{const result=await get<IdentityDetail>(`/admin/external-identities/${id}`,controller.signal);if(!disposed&&current===detailGeneration)detail.value=result}
    catch(e){if(!controller.signal.aborted&&!disposed&&current===detailGeneration)fail(e)}
  }
  async function act(operation:()=>Promise<unknown>,success:string){
    if(saving.value)return;saving.value=true;error.value='';message.value=''
    try{await operation();if(disposed)return;message.value=success;await load()}
    catch(e){if(!disposed){if(e instanceof ApiError&&e.status===409){detail.value=undefined;await load();error.value='记录已经变化，旧表单已清空。请重新选择并核对后操作。'}else fail(e)}}
    finally{if(!disposed)saving.value=false}
  }
  const login=(username:string,password:string)=>act(()=>mutate('/auth/login','POST',new URLSearchParams({username,password})),'维护会话已建立')
  async function logout(){if(saving.value)return;saving.value=true;listController?.abort();detailController?.abort();listGeneration++;detailGeneration++;loading.value=false;try{await mutate('/auth/logout','POST');authorized.value=false;rows.value=[];detail.value=undefined;message.value='已退出维护会话'}catch(e){fail(e)}finally{saving.value=false}}
  const create=(input:IdentityCandidate)=>act(()=>mutate('/admin/external-identities','POST',input),'候选已登记，尚未审核')
  const decide=(id:number,input:IdentityDecision)=>act(()=>mutate(`/admin/external-identities/${id}/decisions`,'POST',input),'操作已保存，请重新选择记录核对当前状态')
  onScopeDispose(()=>{disposed=true;listController?.abort();detailController?.abort()})
  return {rows,detail,authorized,provider,status,loading,saving,error,message,load,select,login,logout,create,decide,expireSession}
}
