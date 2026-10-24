import { describe,it,expect } from 'vitest'
import { mergeLocalGraph,collapsedMemberIds } from './localGraph'
import { characterElements } from './characterLayout'
import { projectRelations } from './semanticZoom'
import { focusedPositions } from './focusNetwork'
import { safePortraitUrl } from './portraitFraming'
import { relationshipText } from './characterGraph'
import type { CharacterGraph,CharacterNode } from '../types/characterGraph'
import type { LocalGraphDocument } from '../types/localGraph'
const official:CharacterGraph={work:{uid:'anime:bangumi:1',title:'来源作品',subtitle:'',characterCount:1,relationCount:0},nodes:[{id:'character:bangumi:1',label:'官方角色',group:'',role:'',description:''}],edges:[],relationTypes:[],meta:{dataSource:'NEO4J',demo:false,truncated:false,notice:'',maxNodes:200,maxEdges:400}}
const entity=(id:string,kind:'CHARACTER'|'ORGANIZATION'='CHARACTER')=>({id,kind,name:id,imageUrl:'',aliases:[],gender:'',description:'',notes:'',organizationType:''})
const link=(id:string,source:string,target:string,relationType='MEMBER')=>({id,source,target,relationType,label:'队长',description:'',evidence:'第1集',spoiler:false,directed:true,notes:''})
const local:LocalGraphDocument={version:1,schemaVersion:1,workUid:official.work.uid,nodes:[entity('a'),entity('o','ORGANIZATION'),entity('o2','ORGANIZATION')],edges:[link('e','a','o'),link('e2','a','o2'),link('e3','character:bangumi:1','a','OTHER')]}
describe('local graph projection',()=>{
  it('accepts persisted raster data while rejecting SVG and oversized avatar input',()=>{expect(safePortraitUrl('data:image/png;base64,aGVsbG8=')).toContain('data:image/png');expect(safePortraitUrl('data:image/svg+xml;base64,aGVsbG8=')).toBe('');expect(safePortraitUrl('data:image/png;base64,'+'a'.repeat(720001))).toBe('')})
  it('merges stable local identities and provenance without mutating source',()=>{const result=mergeLocalGraph(official,local);expect(result.nodes).toHaveLength(4);expect(result.edges).toHaveLength(3);expect(result.nodes.find(n=>n.id==='a')?.dataSource).toBe('LOCAL');expect(official.nodes).toHaveLength(1);expect(characterElements(result.nodes,result.edges).find(e=>e.data.originalId==='e3')?.data.line).toBe('dashed');expect(relationshipText(result.edges[2]!,true)).toContain('队长')})
  it('keeps official references readable after a source cache is unavailable',()=>{const result=mergeLocalGraph({...official,nodes:[]},local);expect(result.nodes.find(n=>n.id==='character:bangumi:1')?.label).toContain('#1');expect(result.edges).toHaveLength(3)})
  it('does not hide multi-organization or externally connected members prematurely',()=>{const graph=mergeLocalGraph(official,{...local,edges:local.edges.slice(0,2)});expect(collapsedMemberIds(graph,new Set(['o']))).toEqual(new Set());expect(collapsedMemberIds(graph,new Set(['o','o2']))).toEqual(new Set(['a']));expect(collapsedMemberIds(mergeLocalGraph(official,local),new Set(['o','o2']))).toEqual(new Set())})
  it('does not aggregate official and local edges into an ambiguous source style',()=>{const edge=mergeLocalGraph(official,local).edges[2]!;expect(projectRelations([edge,{...edge,id:'official',dataSource:'BANGUMI'}],'overview')).toHaveLength(2)})
  it('keeps focus central and direct/indirect bands separated for a dense graph',()=>{const nodes:CharacterNode[]=Array.from({length:80},(_,i)=>({id:String(i),label:String(i),role:'',group:'',description:''}));const edges=nodes.slice(1).map((n,i)=>({...mergeLocalGraph(official,local).edges[0]!,id:n.id,source:String(Math.floor(i/4)),target:n.id}));const positions=focusedPositions(nodes,edges,'0');expect(positions.get('0')).toEqual({x:0,y:0});for(const point of positions.values())expect(Number.isFinite(point.x)&&Number.isFinite(point.y)).toBe(true);const values=[...positions.values()];for(let i=0;i<values.length;i++)for(let j=i+1;j<values.length;j++)expect(Math.hypot(values[i]!.x-values[j]!.x,values[i]!.y-values[j]!.y)).toBeGreaterThan(100)})
})
