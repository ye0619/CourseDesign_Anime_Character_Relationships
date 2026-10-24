import { computed, shallowRef, watch } from 'vue'
export interface Activity {key:string;title:string;at:number;subjectId?:string}
interface History {enabled:boolean;searches:Activity[];visits:Activity[]}
const prefix='animegraph.history.v1.'
const cache=shallowRef<Record<string,History>>({})
const empty=():History=>({enabled:true,searches:[],visits:[]})
function read(scope:string):History{try{const raw=JSON.parse(localStorage.getItem(prefix+scope)||'null');if(!raw)return empty();const valid=(rows:unknown,limit:number)=>Array.isArray(rows)?rows.filter((r):r is Activity=>r&&typeof r.key==='string'&&r.key.length<=100&&typeof r.title==='string'&&r.title.length<=500&&Number.isFinite(r.at)&&(!r.subjectId||/^[1-9]\d{0,9}$/.test(r.subjectId))).slice(0,limit):[];return {enabled:raw.enabled!==false,searches:valid(raw.searches,20),visits:valid(raw.visits,50)}}catch{return empty()}}
function save(scope:string,state:History){try{localStorage.setItem(prefix+scope,JSON.stringify(state));cache.value={...cache.value,[scope]:state};return true}catch{return false}}
export function clearActivityHistory(scope:string){try{localStorage.removeItem(prefix+scope)}catch{/* A disabled browser store must not break unbinding. */}const next={...cache.value};delete next[scope];cache.value=next}
export function useActivityHistory(scope:()=>string){
  const error=shallowRef('')
  watch(scope,key=>{if(key)cache.value={...cache.value,[key]:read(key)};error.value=''},{immediate:true})
  const state=computed(()=>cache.value[scope()]||empty())
  function commit(next:History){if(!scope())return;if(!save(scope(),next))error.value='本机存储不可用，历史未保存'}
  function record(kind:'searches'|'visits',item:Omit<Activity,'at'>){if(!scope()||!state.value.enabled)return;commit({...state.value,[kind]:[{...item,at:Date.now()},...state.value[kind].filter(r=>r.key!==item.key)].slice(0,kind==='searches'?20:50)})}
  return {state,error,recordSearch:(text:string)=>{const word=text.trim().slice(0,100);if(word)record('searches',{key:word,title:word})},recordVisit:(subjectId:string,title:string)=>{if(/^[1-9]\d{0,9}$/.test(subjectId))record('visits',{key:subjectId,subjectId,title:title.slice(0,500)})},remove:(kind:'searches'|'visits',key:string)=>commit({...state.value,[kind]:state.value[kind].filter(r=>r.key!==key)}),clear:()=>commit({...empty(),enabled:state.value.enabled}),toggle:(enabled:boolean)=>commit({...state.value,enabled})}
}
