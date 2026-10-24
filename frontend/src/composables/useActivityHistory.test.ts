import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, shallowRef } from 'vue'
import { clearActivityHistory, useActivityHistory } from './useActivityHistory'
afterEach(()=>vi.unstubAllGlobals())
describe('bounded account-scoped history',()=>{
  it('deduplicates, bounds, disables and clears with account switches',async()=>{const storage=new Map<string,string>();vi.stubGlobal('localStorage',{getItem:(k:string)=>storage.get(k)||null,setItem:(k:string,v:string)=>storage.set(k,v),removeItem:(k:string)=>storage.delete(k)});const scope=effectScope(),key=shallowRef('history-test-account');const history=scope.run(()=>useActivityHistory(()=>key.value))!;for(let i=0;i<30;i++)history.recordSearch('作品'+i);expect(history.state.value.searches).toHaveLength(20);history.recordSearch('作品29');expect(history.state.value.searches).toHaveLength(20);history.toggle(false);history.recordSearch('不保存');expect(history.state.value.searches[0]?.title).toBe('作品29');key.value='another-history-account';await nextTick();expect(history.state.value.searches).toEqual([]);clearActivityHistory('history-test-account');key.value='history-test-account';await nextTick();expect(history.state.value.searches).toEqual([]);scope.stop()})
})
