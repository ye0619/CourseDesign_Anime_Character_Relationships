import { computed, inject, provide, shallowRef, watch, type InjectionKey, type Ref } from 'vue'
import type { CharacterNode } from '../types/characterGraph'
import { portraitFrame, safePortraitUrl, type ImageDimensions, type PortraitMode } from '../utils/portraitFraming'
import { computed as vueComputed } from 'vue'
import type { CharacterCrop } from '../types/characterEdit'
import { effectivePortraitOverrides } from './usePortraitSettings'

const subjectKey: InjectionKey<Readonly<Ref<string>>> = Symbol('portraitSubject')
export function providePortraitSubject(subject:Readonly<Ref<string>>) {provide(subjectKey,subject)}
const modeKey: InjectionKey<Ref<PortraitMode>> = Symbol('portraitMode')
export function providePortraitMode() { const mode = shallowRef<PortraitMode>('auto'); provide(modeKey, mode); return mode }
export function usePortraitMode() { return inject(modeKey, () => shallowRef<PortraitMode>('auto'), true) }

// Public image dimensions only; bounded shared cache and six concurrent loads.
const cacheTimes = new Map<string,number>()
const cache = new Map<string, Promise<ImageDimensions | null>>()
const queue: Array<() => void> = []
let active = 0
export function portraitDimensions(url: string): Promise<ImageDimensions | null> {
  if(Date.now()-(cacheTimes.get(url)||0)>60000){cache.delete(url);cacheTimes.delete(url)}
  const old = cache.get(url)
  if (old) { cache.delete(url); cache.set(url, old); return old }
  const result = new Promise<ImageDimensions | null>(resolve => {
    const start = () => {
      active++
      const image = new Image()
      let done = false
      const finish = (value: ImageDimensions | null) => {
        if (done) return
        done = true; clearTimeout(timer); image.onload = null; image.onerror = null
        active--; resolve(value); queue.shift()?.()
      }
      const timer = setTimeout(() => { finish(null); image.src = '' }, 12000)
      image.crossOrigin = 'anonymous'; image.referrerPolicy = 'no-referrer'
      image.onload = async () => {
        if(!image.naturalWidth || !image.naturalHeight){finish(null);return}
        const dimensions:ImageDimensions={width:image.naturalWidth,height:image.naturalHeight}
        try {
          if(dimensions.width*dimensions.height<=16000000 && typeof document!=='undefined') {
            const canvas=document.createElement('canvas');canvas.width=dimensions.width;canvas.height=dimensions.height
            const context=canvas.getContext('2d');if(context){context.drawImage(image,0,0);const pixels=context.getImageData(0,0,dimensions.width,dimensions.height).data
              const bytes=new Uint8Array(pixels.length+8);const view=new DataView(bytes.buffer);view.setUint32(0,dimensions.width);view.setUint32(4,dimensions.height);bytes.set(pixels,8);const digest=await crypto.subtle.digest('SHA-256',bytes);dimensions.contentFingerprint='sha256:'+Array.from(new Uint8Array(digest)).map(n=>n.toString(16).padStart(2,'0')).join('')}
            canvas.width=0;canvas.height=0
          }
        }catch{/* A non-CORS source can still display; content-bound crops cannot be applied. */}
        finish(dimensions)
      }
      image.onerror = () => finish(null); image.src = url
    }
    if (active < 6) start(); else queue.push(start)
  })
  cache.set(url, result);cacheTimes.set(url,Date.now())
  if (cache.size > 512) cache.delete(cache.keys().next().value!)
  return result
}

export function usePortraitFrames(nodes: () => CharacterNode[], mode: Readonly<Ref<PortraitMode>>) {
  const subject=inject(subjectKey,()=>vueComputed(()=>''),true)
  const urlFor=(node:CharacterNode)=>safePortraitUrl(node.portraitSettings?.[node.portraitSubjectUid||subject.value]?.imageUrl??node.portraitSettings?.GLOBAL?.imageUrl??node.imageUrl)
  const images = shallowRef(new Map<string, ImageDimensions | null>())
  watch(() => nodes().map(urlFor), (urls, _old, onCleanup) => {
    let current = true
    onCleanup(() => { current = false })
    const unique = [...new Set(urls.filter(Boolean))]
    // Publish as each image arrives; stale component generations cannot write back.
    images.value = new Map(unique.filter(url => images.value.has(url)).map(url => [url, images.value.get(url)!]))
    for (const url of unique) {
      if (images.value.has(url)) continue
      void portraitDimensions(url).then(value => { if (current) images.value = new Map(images.value).set(url, value) })
    }
  }, { immediate: true })
  return computed(() => new Map(nodes().map(node => {
    const url = urlFor(node), image = images.value.get(url)
    return [node.id, { url: image ? url : '', failed: !!url && image === null,
      frame: portraitFrame(node.id, url, image ?? null, mode.value, node.characterType, [node.portraitSettings?.[node.portraitSubjectUid||subject.value],node.portraitSettings?.GLOBAL].filter((p):p is CharacterCrop=>!!p).map(p=>({...p,entityUid:node.id,reviewed:true as const})).concat(effectivePortraitOverrides.value)) }]
  })))
}
