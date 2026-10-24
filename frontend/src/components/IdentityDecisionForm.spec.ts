import { describe,expect,it } from 'vitest'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import IdentityDecisionForm from './IdentityDecisionForm.vue'
import type { ReviewedIdentity } from '../types/identityReview'
const row=(status:string):ReviewedIdentity=>({id:1,provider:'PROJECT',providerId:'fixture',entityType:'CHARACTER',canonicalUid:'character:bangumi:87968',status,version:3,sourceUrl:'https://example.org',sourceRevision:'r1',licenseLabel:'fixture',evidenceText:'source',reviewReason:'',reviewedBy:7,updatedAt:'',matchMethod:'MANUAL'})
describe('identity decisions by current status',()=>{
  it('requires new manual evidence before offering review for title-only candidates',async()=>{const html=await renderToString(createSSRApp(IdentityDecisionForm,{identity:{...row('CANDIDATE'),matchMethod:'TITLE_ONLY'},busy:false}));expect(html).not.toContain('审核通过');expect(html).toContain('原名、作品归属');expect(html).toContain('补充候选资料')})
  it('offers review, proof amendment, remap and rejection only for a candidate',async()=>{const html=await renderToString(createSSRApp(IdentityDecisionForm,{identity:row('CANDIDATE'),busy:false}));expect(html).toContain('审核通过');expect(html).toContain('补充候选资料');expect(html).toContain('改绑并重新待审');expect(html).not.toContain('凭新修订重开')})
  it('requires explicit new revision to reopen and escapes recorded proof',async()=>{const identity={...row('REVOKED'),evidenceText:'<script>alert(1)</script>'};const html=await renderToString(createSSRApp(IdentityDecisionForm,{identity,busy:false}));expect(html).toContain('凭新修订重开');expect(html).toContain('&lt;script&gt;');expect(html).not.toContain('<script>alert')})
})
