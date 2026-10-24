import { describe,expect,it,vi,afterEach } from 'vitest'
import { effectScope } from 'vue'
import { ApiError,get,mutate } from '../api/client'
import { useIdentityReview } from './useIdentityReview'
vi.mock('../api/client',async importOriginal=>({...await importOriginal<typeof import('../api/client')>(),get:vi.fn(),mutate:vi.fn()}))
afterEach(()=>vi.resetAllMocks())
const pending=<T>()=>{let resolve!:(v:T)=>void;const promise=new Promise<T>(r=>resolve=r);return {promise,resolve}}
describe('identity maintenance request boundaries',()=>{
  it('cannot publish an older selection after another identity is selected',async()=>{
    const scope=effectScope(),ui=scope.run(useIdentityReview)!,old=pending<unknown>();vi.mocked(get).mockImplementationOnce(()=>old.promise).mockResolvedValueOnce({identity:{id:2},history:[]})
    const first=ui.select(1);await ui.select(2);old.resolve({identity:{id:1},history:[]});await first;expect(ui.detail.value?.identity.id).toBe(2);scope.stop()
  })
  it('clears stale forms on conflict and never resubmits automatically',async()=>{
    const scope=effectScope(),ui=scope.run(useIdentityReview)!;vi.mocked(get).mockResolvedValue([]);vi.mocked(mutate).mockRejectedValue(new ApiError(409,'IDENTITY_CHANGED','stale'))
    await ui.decide(1,{expectedVersion:0,decision:'REVIEW',reason:'verified'});expect(ui.detail.value).toBeUndefined();expect(ui.error.value).toContain('重新选择');expect(mutate).toHaveBeenCalledTimes(1);scope.stop()
  })
  it('prevents duplicate submissions and clears records when permission expires',async()=>{
    const scope=effectScope(),ui=scope.run(useIdentityReview)!,write=pending<unknown>();vi.mocked(mutate).mockReturnValue(write.promise);vi.mocked(get).mockRejectedValue(new ApiError(401,'UNAUTHORIZED','expired'))
    const first=ui.decide(1,{expectedVersion:0,decision:'REVIEW',reason:'verified'});await ui.decide(1,{expectedVersion:0,decision:'REVIEW',reason:'verified'});expect(mutate).toHaveBeenCalledTimes(1);write.resolve({});await first;expect(ui.authorized.value).toBe(false);expect(ui.rows.value).toEqual([]);scope.stop()
  })
  it('cannot restore authorized data from a request that finishes after logout',async()=>{
    const scope=effectScope(),ui=scope.run(useIdentityReview)!,old=pending<unknown>();vi.mocked(get).mockReturnValue(old.promise);vi.mocked(mutate).mockResolvedValue({});const loading=ui.load();await ui.logout();old.resolve([{id:1}]);await loading;expect(ui.authorized.value).toBe(false);expect(ui.rows.value).toEqual([]);scope.stop()
  })
  it('expires the maintenance session immediately and ignores older reads even when they later succeed',async()=>{
    const scope=effectScope(),ui=scope.run(useIdentityReview)!,old=pending<unknown>();vi.mocked(get).mockReturnValue(old.promise);const loading=ui.load();ui.expireSession();old.resolve([{id:1}]);await loading;expect(ui.authorized.value).toBe(false);expect(ui.rows.value).toEqual([]);expect(ui.loading.value).toBe(false);expect(ui.error.value).toContain('过期');scope.stop()
  })
})
