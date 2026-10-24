import {describe,it,expect} from 'vitest'
import {legacyNames,multilingualDisplay} from './multilingualNames'
import type {LocalName} from '../types/localGraph'
const n=(id:string,text:string,locale:string,kind:LocalName['kind']='ALIAS'):LocalName=>({id,text,locale,kind,source:'LOCAL',reviewStatus:'MANUAL'})
describe('multilingual names',()=>{
 it('prioritizes Chinese, explicit original language, then stable original names',()=>{const names=[n('en','English','en','PRIMARY'),n('ja','原名','ja','ORIGINAL'),n('zh','中文','zh-Hant')];expect(multilingualDisplay(names)).toBe('中文');expect(multilingualDisplay(names.slice(0,2),null,['ja'])).toBe('原名');expect(multilingualDisplay(names,'en')).toBe('English')})
 it('keeps legacy ids and names stable without guessing languages',()=>{const rows=legacyNames('原名',['Alias','原名']);expect(rows).toHaveLength(2);expect(rows.every(n=>n.locale==='und')).toBe(true);expect(multilingualDisplay(rows)).toBe('原名');expect(legacyNames('原名',['Alias'])).toEqual(rows)})
 it('does not infer Japanese from characters',()=>expect(multilingualDisplay([n('en','English','en','PRIMARY'),n('und','名前','und')],null,['ja'])).toBe('English'))
})
