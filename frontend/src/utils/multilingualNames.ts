import type { LocalName } from '../types/localGraph'
export function multilingualDisplay(names: LocalName[], preferred?: string | null, originalLanguages: string[] = []) {
  if (preferred) { const name=names.find(n=>n.id===preferred); if(name) return name.text.trim() }
  const priority=(n:LocalName)=>n.locale.toLowerCase().startsWith('zh')?0:originalLanguages.includes(n.locale)?1:['ORIGINAL','PRIMARY'].includes(n.kind)?2:3
  return [...names].sort((a,b)=>priority(a)-priority(b))[0]?.text.trim() || ''
}
export function legacyNames(name: string, aliases: string[] = []): LocalName[] {
  return [name,...aliases].filter((n,i,all)=>n.trim()&&all.indexOf(n)===i).map((text,i)=>({id:'legacy-'+i,text,locale:'und',kind:i?'ALIAS':'PRIMARY',source:'LEGACY',reviewStatus:'MANUAL'}))
}
