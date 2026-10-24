import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
const audit=JSON.parse(readFileSync(new URL('../data/samples/10.21-portrait-head-audit.json',import.meta.url),'utf8'))
const rows=audit.samples.map(s=>({entityUid:s.uid,imageFingerprint:`${s.imageUrl}|${s.dimensions.width}x${s.dimensions.height}`,reviewed:true,mode:s.kind==='person'?'portrait':'full',focalX:.5,focalY:0,scale:1,...(s.kind==='person'?{headBox:s.headBoxes[0]}:{})}))
mkdirSync(new URL('../frontend/src/data/',import.meta.url),{recursive:true})
writeFileSync(new URL('../frontend/src/data/reviewedPortraits.json',import.meta.url),JSON.stringify(rows,null,2)+'\n')
console.log(`Generated ${rows.length} image-bound reviewed portrait settings`)
