import { onScopeDispose, shallowRef, watch } from 'vue'
import { get } from '../api/client'
/** Private request state is discarded whenever the binding/collection scope changes. */
export function usePrivateResource<T>(path: () => string, scope: () => string, running: (data: T) => boolean = () => false) {
  const data=shallowRef<T>(), error=shallowRef(''), pending=shallowRef(false)
  let controller:AbortController|undefined,timer:ReturnType<typeof setTimeout>|undefined,sequence=0
  async function refresh(){controller?.abort();clearTimeout(timer);const current=new AbortController();controller=current;const version=++sequence;const owner=scope();if(!owner){data.value=undefined;pending.value=false;return}pending.value=true;error.value=''
    try{const result=await get<T>(path(),current.signal);if(version!==sequence||owner!==scope())return;data.value=result;if(running(result))timer=setTimeout(refresh,1800)}
    catch(cause){if(!current.signal.aborted&&owner===scope())error.value=cause instanceof Error?cause.message:'资料暂不可用'}
    finally{if(version===sequence)pending.value=false}
  }
  watch(()=>[scope(),path()],()=>{data.value=undefined;void refresh()},{immediate:true})
  onScopeDispose(()=>{sequence++;controller?.abort();clearTimeout(timer)})
  return {data,error,pending,refresh}
}
