import {afterEach,describe,expect,it,vi} from 'vitest'
import {effectScope} from 'vue'
import {ApiError,mutate} from '../api/client'
import {useIdentityCandidateBatch} from './useIdentityCandidateBatch'
import file from '../../../data/samples/identity-title-candidates-2026-10-22.json'
vi.mock('../api/client',async importOriginal=>({...await importOriginal<typeof import('../api/client')>(),mutate:vi.fn()}))
afterEach(()=>vi.resetAllMocks())
const pending=()=>{let resolve!:(v:unknown)=>void;const promise=new Promise(r=>resolve=r);return {promise,resolve}}
const text=JSON.stringify({schemaVersion:1,candidates:file.candidates.slice(0,3)})
describe('candidate batch writes',()=>{
  it('serializes writes, pauses after current request and resumes pending rows without resubmitting successes',async()=>{
    const scope=effectScope(),ui=scope.run(()=>useIdentityCandidateBatch(vi.fn()))!,first=pending();vi.mocked(mutate).mockReturnValueOnce(first.promise).mockResolvedValue({});ui.preview(text)
    const saving=ui.start();await ui.start();expect(mutate).toHaveBeenCalledTimes(1);ui.stop();first.resolve({});await saving;expect(ui.counts.value).toEqual({saved:1,pending:2});await ui.start();expect(mutate).toHaveBeenCalledTimes(3);expect(ui.counts.value.saved).toBe(3);scope.stop()
  })
  it('does not count duplicate conflicts as saved or retry them',async()=>{
    const scope=effectScope(),ui=scope.run(()=>useIdentityCandidateBatch(vi.fn()))!;vi.mocked(mutate).mockRejectedValueOnce(new ApiError(409,'IDENTITY_CHANGED','duplicate')).mockResolvedValue({});ui.preview(text);await ui.start();await ui.start();expect(mutate).toHaveBeenCalledTimes(3);expect(ui.counts.value.saved).toBe(2);expect(ui.rows.value[0]?.message).toContain('原记录');scope.stop()
  })
  it('stops on uncertain network outcomes and expired permission, and never retries the uncertain row',async()=>{
    const scope=effectScope(),unauthorized=vi.fn(),ui=scope.run(()=>useIdentityCandidateBatch(unauthorized))!;ui.preview(text);vi.mocked(mutate).mockRejectedValueOnce(new Error('network'));await ui.start();expect(mutate).toHaveBeenCalledTimes(1);expect(ui.rows.value[0]?.outcome).toBe('uncertain');vi.mocked(mutate).mockRejectedValueOnce(new ApiError(401,'UNAUTHORIZED','expired'));await ui.start();expect(unauthorized).toHaveBeenCalledOnce();expect(mutate).toHaveBeenCalledTimes(2);expect(ui.counts.value.pending).toBe(1);scope.stop()
  })
  it('does not continue submitting when the page scope is disposed',async()=>{
    const scope=effectScope(),ui=scope.run(()=>useIdentityCandidateBatch(vi.fn()))!,first=pending();ui.preview(text);vi.mocked(mutate).mockReturnValueOnce(first.promise);const saving=ui.start();scope.stop();first.resolve({});await saving;expect(mutate).toHaveBeenCalledTimes(1)
  })
})
