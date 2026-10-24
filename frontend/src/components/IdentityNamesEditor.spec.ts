import {describe,it,expect,vi} from 'vitest'
import {createSSRApp} from 'vue'
import {renderToString} from 'vue/server-renderer'
import IdentityNamesEditor from './IdentityNamesEditor.vue'
import EntityNames from './EntityNames.vue'
import {reviewedNameSourceLink} from '../utils/reviewedNameSource'
import type {ReviewedIdentity} from '../types/identityReview'
import type {CharacterNode} from '../types/characterGraph'
vi.mock('../api/client',()=>({get:vi.fn(()=>new Promise(()=>{})),mutate:vi.fn(),ApiError:class extends Error{status=0}}))
describe('reviewed name provenance',()=>{
  it('explains identity version and original-name preservation on the maintenance page',async()=>{const html=await renderToString(createSSRApp(IdentityNamesEditor,{identity:{id:1,version:0,status:'CANDIDATE'} as ReviewedIdentity,busy:false}));expect(html).toContain('保留原名和当前显示名');expect(html).toContain('身份状态或版本变化后旧别名失效')})
  it('renders external revision, evidence and license as escaped text',async()=>{const node:CharacterNode={id:'character:bangumi:1',label:'当前名',role:'',group:'',description:'',aliases:['外站别名'],nameAssertions:[{name:'外站别名',locale:'zh-Hans',kind:'REVIEWED_ALIAS',provider:'MOEGIRL',providerId:'123',sourceUrl:'https://zh.moegirl.org.cn/index.php?curid=123',sourceRevision:'r2',reviewStatus:'REVIEWED',retrievedAt:null,reviewedOn:'2026-10-22',evidenceText:'<script>bad()</script>',licenseLabel:'source authors',identityVersion:3}]};const html=await renderToString(createSSRApp(EntityNames,{node}));expect(html).toContain('来源修订 r2');expect(html).toContain('source authors');expect(html).toContain('&lt;script&gt;');expect(html).not.toContain('<script>bad');expect(html).toContain('https://zh.moegirl.org.cn/')})
  it('rejects unsafe and spoofed source links while allowing official anchors',()=>{expect(reviewedNameSourceLink('MOEGIRL','https://zh.moegirl.org.cn/角色#别名')).toContain('#');for(const url of ['javascript:alert(1)','http://zh.moegirl.org.cn/x','https://zh.moegirl.org.cn.evil.test/x','https://user:pass@zh.moegirl.org.cn/x','https://zh.moegirl.org.cn:444/x'])expect(reviewedNameSourceLink('MOEGIRL',url)).toBeUndefined()})
})
