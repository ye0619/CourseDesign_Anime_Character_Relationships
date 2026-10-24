import {afterEach,describe,it,expect,vi} from 'vitest'
import {effectScope,nextTick} from 'vue'
import {ApiError,get,mutate} from '../api/client'
import {useIdentityNames,type IdentityNamesDetail} from './useIdentityNames'
import type {ReviewedIdentity} from '../types/identityReview'
vi.mock('../api/client',async original=>({...await original<typeof import('../api/client')>(),get:vi.fn(),mutate:vi.fn()}))
afterEach(()=>vi.resetAllMocks())
const identity={id:1,status:'REVIEWED',version:3} as ReviewedIdentity
const stored={name:'旧别名',locale:'ja',evidenceText:'source proof',canonicalUid:'character:bangumi:87968',sourceUrl:'https://example.org',sourceRevision:'r1',licenseLabel:'fixture',reviewVersion:3,provider:'PROJECT',providerId:'fixture'}
const response=(active=true):IdentityNamesDetail=>({identity,names:[stored],activeForSearch:active})
const callbacks=()=>({busy:vi.fn(),changed:vi.fn(),unauthorized:vi.fn()})
const settle=async()=>{await Promise.resolve();await nextTick()}
describe('reviewed identity names lifecycle',()=>{
  it('does not prefill or silently revive inactive names',async()=>{const scope=effectScope(),events=callbacks();vi.mocked(get).mockResolvedValue(response(false));const ui=scope.run(()=>useIdentityNames(()=>identity,events))!;await settle();expect(ui.detail.value?.names).toHaveLength(1);expect(ui.draft.value).toEqual([]);expect(mutate).not.toHaveBeenCalled();scope.stop()})
  it('sends version-bound names once, clears conflicted draft and does not retry',async()=>{const scope=effectScope(),events=callbacks();vi.mocked(get).mockResolvedValue(response());vi.mocked(mutate).mockRejectedValue(new ApiError(409,'IDENTITY_CHANGED','stale'));const ui=scope.run(()=>useIdentityNames(()=>identity,events))!;await settle();ui.reason.value='核验原文';const first=ui.save();await ui.save();await first;expect(mutate).toHaveBeenCalledTimes(1);expect(mutate).toHaveBeenCalledWith('/admin/external-identities/1/names','PUT',{expectedVersion:3,reason:'核验原文',names:[{name:stored.name,locale:'ja',evidenceText:'source proof'}]});expect(ui.draft.value).toEqual([]);expect(events.changed).toHaveBeenCalledWith(expect.stringContaining('旧别名表单已清空'));scope.stop()})
  it('expires permission immediately and cannot publish a read after disposal',async()=>{const scope=effectScope(),events=callbacks();vi.mocked(get).mockRejectedValueOnce(new ApiError(403,'FORBIDDEN','expired'));const ui=scope.run(()=>useIdentityNames(()=>identity,events))!;await settle();expect(events.unauthorized).toHaveBeenCalledOnce();let resolve!:(value:IdentityNamesDetail)=>void;vi.mocked(get).mockReturnValueOnce(new Promise(r=>resolve=r));const loading=ui.load();scope.stop();resolve(response());await loading;expect(ui.detail.value).toBeUndefined()})
})
