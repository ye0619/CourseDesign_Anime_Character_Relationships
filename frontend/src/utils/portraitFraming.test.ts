import { describe, expect, it } from 'vitest'
import { imageFingerprint, portraitFrame, safePortraitUrl, type PortraitOverride } from './portraitFraming'
describe('shared portrait geometry', () => {
  it('fits all four corners inside the circular mask for full-body, landscape, square and logo images', () => {
    for (const [width, height] of [[200, 1800], [1200, 300], [500, 500], [800, 1000], [100, 100]]) {
      const frame = portraitFrame('entity', '', { width: width!, height: height! }, 'full')
      expect(Math.hypot(frame.width / 2, frame.height / 2)).toBeLessThan(50)
      expect(frame.width / frame.height).toBeCloseTo(width! / height!)
    }
  })
  it('uses head framing for tall portraits and full images for wide and non-person images', () => {
    expect(portraitFrame('e', '', { width: 600, height: 800 }).y).toBe(0)
    const tall = portraitFrame('e', '', { width: 200, height: 1200 })
    expect(tall.full).toBe(false); expect(tall.width).toBe(150); expect(tall.y).toBe(0)
    expect(portraitFrame('e', '', { width: 1200, height: 400 }).full).toBe(true)
    expect(portraitFrame('e', '', { width: 600, height: 800 }, 'auto', 4).full).toBe(true)
    expect(portraitFrame('e', '', null).width).toBe(100)
    expect(safePortraitUrl('javascript:alert(1)')).toBe('')
  })
  it('binds reviewed overrides to both entity and image version', () => {
    const url = 'https://lain.bgm.tv/pic/crt/l/test.jpg', image = { width: 400, height: 600 }
    const overrides: PortraitOverride[] = [{ entityUid: 'character:bangumi:1', imageFingerprint: imageFingerprint(url, image), reviewed: true, mode: 'portrait', focalX: .4, focalY: .25, scale: 1.1 }]
    expect(portraitFrame('character:bangumi:1', url, image, 'auto', 1, overrides).y).toBe(25)
    expect(portraitFrame('character:bangumi:2', url, image, 'auto', 1, overrides).y).toBe(0)
    expect(portraitFrame('character:bangumi:1', `${url}?new`, image, 'auto', 1, overrides).y).toBe(0)
    expect(portraitFrame('character:bangumi:1', url, image, 'full', 1, overrides).full).toBe(true)
  })
  it('invalidates a saved content-bound crop when pixels change at the same URL and dimensions',()=>{
    const url='https://lain.bgm.tv/pic/crt/l/same.jpg',first='sha256:'+'a'.repeat(64),second='sha256:'+'b'.repeat(64)
    const overrides:PortraitOverride[]=[{entityUid:'c',imageFingerprint:first,reviewed:true,mode:'portrait',focalX:.4,focalY:.3,scale:1.2}]
    expect(portraitFrame('c',url,{width:400,height:600,contentFingerprint:first},'auto',1,overrides).y).toBe(30)
    expect(portraitFrame('c',url,{width:400,height:600,contentFingerprint:second},'auto',1,overrides).y).toBe(0)
    expect(portraitFrame('c',url,{width:400,height:600},'auto',1,overrides).y).toBe(0)
  })

})
