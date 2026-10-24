import { describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, shallowRef } from 'vue'
import { get } from '../api/client'
import { usePrivateResource } from './usePrivateResource'
vi.mock('../api/client',()=>({get:vi.fn()}))
describe('private report/resource isolation',()=>{
  it('discards old account responses and cancels disposal',async()=>{let finish!:(data:{value:string})=>void;vi.mocked(get).mockReturnValueOnce(new Promise(resolve=>{finish=resolve})).mockResolvedValueOnce({value:'new'});const scope=effectScope(),owner=shallowRef('a');const state=scope.run(()=>usePrivateResource<{value:string}>(()=>'/private',()=>owner.value))!;const signal=vi.mocked(get).mock.calls[0]![1];owner.value='b';await nextTick();await nextTick();finish({value:'old'});await nextTick();expect(signal?.aborted).toBe(true);expect(state.data.value?.value).toBe('new');owner.value='';await nextTick();expect(state.data.value).toBeUndefined();scope.stop()})
})
