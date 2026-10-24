import { beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { mutate } from '../api/client'
import { useConnectionSearch } from './useConnectionSearch'
import type { ConnectionQuery, ConnectionResult } from '../types/connections'
vi.mock('../api/client', () => ({ get: vi.fn(), mutate: vi.fn() }))
const query:ConnectionQuery = { from:{characterUid:'character:bangumi:1',subjectId:1},to:{characterUid:'character:bangumi:2',subjectId:2},maxHops:8,limit:10,spoilers:false,storyDirection:'CONNECTIVITY',language:null,includeUnknownLanguage:false,version:null,includeManualRelations:false,requireStoryRelation:false,includeEnded:true }
const result = (id:string):ConnectionResult => ({queryId:id,status:'RUNNING',paths:[],query,stopReason:'RUNNING',shortestPhase:'RUNNING',morePhase:'PENDING',graphRevision:'r1',returnedPaths:0,foundPathCount:0,exactPathCount:null,searchExhausted:false,shortestProven:false,truncated:false,elapsedMillis:0,scope:[]})
const deferred = () => {let resolve!: (value:unknown) => void;const promise=new Promise(r=>resolve=r);return {promise,resolve}}
beforeEach(()=>vi.mocked(mutate).mockReset().mockResolvedValue(undefined))
describe('connection request isolation',()=>{
  it('does not resurrect a cancelled result after a newer query completes',async()=>{
    const deletion=deferred();vi.mocked(mutate).mockResolvedValueOnce(result('old')).mockReturnValueOnce(deletion.promise).mockResolvedValueOnce({...result('new'),status:'COMPLETE'})
    const scope=effectScope(),state=scope.run(()=>useConnectionSearch())!
    try{await state.start(query);const cancel=state.cancel();await state.start(query);deletion.resolve(undefined);await cancel;expect(state.result.value?.queryId).toBe('new');expect(state.result.value?.status).toBe('COMPLETE')}
    finally{scope.stop()}
  })
  it('does not submit an older start after its deletion wait is superseded',async()=>{
    const deletion=deferred();vi.mocked(mutate).mockResolvedValueOnce(result('old')).mockReturnValueOnce(deletion.promise).mockResolvedValueOnce({...result('new'),status:'COMPLETE'})
    const scope=effectScope(),state=scope.run(()=>useConnectionSearch())!
    try{await state.start(query);const delayed=state.start(query);await state.start(query);deletion.resolve(undefined);await delayed;expect(state.result.value?.queryId).toBe('new');expect(vi.mocked(mutate).mock.calls.filter(c=>c[1]==='POST')).toHaveLength(2)}finally{scope.stop()}
  })
  it('cancels a late server-created task without replacing current selection',async()=>{
    const post=deferred();vi.mocked(mutate).mockReturnValueOnce(post.promise).mockResolvedValueOnce({...result('new'),status:'COMPLETE'}).mockResolvedValue(undefined)
    const scope=effectScope(),state=scope.run(()=>useConnectionSearch())!
    try{const old=state.start(query);await Promise.resolve();state.reset();await state.start(query);post.resolve(result('late'));await old;expect(state.result.value?.queryId).toBe('new');expect(mutate).toHaveBeenCalledWith('/connections/searches/late','DELETE')}finally{scope.stop()}
  })
})
