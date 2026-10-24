import { describe, expect, it } from 'vitest'
import cytoscape from 'cytoscape'
import audit from '../../../data/samples/10.21-portrait-head-audit.json'
import { imageFingerprint, portraitFrame, portraitImageStyle, portraitOverrides, type HeadBox, type PortraitOverride } from './portraitFraming'

function cornerRadii(box: HeadBox, image: {width:number;height:number}, frame: ReturnType<typeof portraitFrame>) {
  const left=(100-frame.width)*frame.x/100, top=(100-frame.height)*frame.y/100
  return [box.x,box.x+box.width].flatMap(x=>[box.y,box.y+box.height].map(y=>Math.hypot(left+x/image.width*frame.width-50,top+y/image.height*frame.height-50)))
}

describe('reviewed visible-head protection',()=>{
  it('contains every annotated head corner, including both people in the group and the animal',()=>{
    expect(audit.samples).toHaveLength(33)
    expect(new Set(portraitOverrides.map(o=>o.entityUid))).toHaveLength(33)
    for(const s of audit.samples){
      const frame=portraitFrame(s.uid,s.imageUrl,s.dimensions)
      const setting=portraitOverrides.find(o=>o.entityUid===s.uid)!
      expect(setting.imageFingerprint).toBe(imageFingerprint(s.imageUrl,s.dimensions))
      expect(frame.width/frame.height).toBeCloseTo(s.dimensions.width/s.dimensions.height)
      for(const b of s.headBoxes){
        expect(b.x+b.width).toBeLessThanOrEqual(s.dimensions.width)
        expect(b.y+b.height).toBeLessThanOrEqual(s.dimensions.height)
        for(const r of cornerRadii(b,s.dimensions,frame))expect(r,`${s.uid} head corner outside circle`).toBeLessThanOrEqual(46.000001)
      }
      if(s.kind!=='person')expect(frame.full).toBe(true)
    }
  })
  it('uses identical offsets in DOM styles and the actual Cytoscape style parser',()=>{
    const cy=cytoscape({headless:true,styleEnabled:true,elements:audit.samples.map(s=>({data:{id:s.uid}}))})
    try{for(const s of audit.samples){
      const f=portraitFrame(s.uid,s.imageUrl,s.dimensions),style=portraitImageStyle(f),node=cy.getElementById(s.uid)
      node.style({'background-width':`${f.width}%`,'background-height':`${f.height}%`,'background-position-x':`${f.x}%`,'background-position-y':`${f.y}%`})
      const w=parseFloat(node.style('background-width')),h=parseFloat(node.style('background-height'))
      expect((100-w)*parseFloat(node.style('background-position-x'))/100).toBeCloseTo(parseFloat(style.left),5)
      expect((100-h)*parseFloat(node.style('background-position-y'))/100).toBeCloseTo(parseFloat(style.top),5)
    }}finally{cy.destroy()}
  })
  it('rejects stale image dimensions, URLs and entity identities before using a head box',()=>{
    const s=audit.samples[0]!,f=portraitFrame(s.uid,s.imageUrl,s.dimensions)
    expect(f.y).not.toBe(0)
    expect(portraitFrame(s.uid,s.imageUrl,{...s.dimensions,height:s.dimensions.height+1}).y).toBe(0)
    expect(portraitFrame(s.uid,s.imageUrl+'?changed',s.dimensions).y).toBe(0)
    expect(portraitFrame('different-entity',s.imageUrl,s.dimensions).y).toBe(0)
    expect(portraitFrame(s.uid,s.imageUrl,s.dimensions,'full').full).toBe(true)
  })
  it('keeps personal adjustments ahead of the reviewed project crop',()=>{
    const s=audit.samples[0]!,local:PortraitOverride={entityUid:s.uid,imageFingerprint:imageFingerprint(s.imageUrl,s.dimensions),reviewed:true,mode:'portrait',focalX:.3,focalY:.4,scale:1.2}
    expect(portraitFrame(s.uid,s.imageUrl,s.dimensions,'auto',1,[local,...portraitOverrides]).y).toBe(40)
  })
  it('falls back from invalid boxes and stays finite at CSS positioning singularities',()=>{
    const url='https://lain.bgm.tv/pic/crt/l/test.jpg'
    for(const image of [{width:200,height:400},{width:200,height:200}]){
      const override:PortraitOverride={entityUid:'fixture',imageFingerprint:imageFingerprint(url,image),reviewed:true,mode:'portrait',focalX:.5,focalY:0,scale:1,headBox:{x:20,y:20,width:105.6,height:140.8}}
      const frame=portraitFrame('fixture',url,image,'auto',1,[override])
      expect(Object.values(frame).filter(v=>typeof v==='number').every(Number.isFinite)).toBe(true)
      expect(Math.max(...cornerRadii(override.headBox!,image,frame))).toBeLessThanOrEqual(44.000001)
      for(const headBox of [{x:-1,y:0,width:30,height:40},{x:0,y:0,width:999,height:30},{x:0,y:0,width:0,height:30},{x:0,y:NaN,width:30,height:40}]){
        expect(portraitFrame('fixture',url,image,'auto',1,[{...override,headBox}]).y).toBe(0)
      }
    }
  })
})
