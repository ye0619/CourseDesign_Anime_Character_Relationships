import { computed, inject, provide, shallowRef, watch, type InjectionKey, type Ref } from 'vue'
import type { CharacterNode } from '../types/characterGraph'
import { portraitFrame, safePortraitUrl, type ImageDimensions, type PortraitMode } from '../utils/portraitFraming'
import { effectivePortraitOverrides } from './usePortraitSettings'

const modeKey: InjectionKey<Ref<PortraitMode>> = Symbol('portraitMode')
export function providePortraitMode() { const mode = shallowRef<PortraitMode>('auto'); provide(modeKey, mode); return mode }
export function usePortraitMode() { return inject(modeKey, () => shallowRef<PortraitMode>('auto'), true) }

// Public image dimensions only; bounded shared cache and six concurrent loads.
const cache = new Map<string, Promise<ImageDimensions | null>>()
const queue: Array<() => void> = []
let active = 0
export function portraitDimensions(url: string): Promise<ImageDimensions | null> {
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
      image.onload = () => finish(image.naturalWidth && image.naturalHeight ? { width: image.naturalWidth, height: image.naturalHeight } : null)
      image.onerror = () => finish(null); image.src = url
    }
    if (active < 6) start(); else queue.push(start)
  })
  cache.set(url, result)
  if (cache.size > 512) cache.delete(cache.keys().next().value!)
  return result
}

export function usePortraitFrames(nodes: () => CharacterNode[], mode: Readonly<Ref<PortraitMode>>) {
  const images = shallowRef(new Map<string, ImageDimensions | null>())
  watch(() => nodes().map(n => safePortraitUrl(n.imageUrl)), (urls, _old, onCleanup) => {
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
    const url = safePortraitUrl(node.imageUrl), image = images.value.get(url)
    return [node.id, { url: image ? url : '', failed: !!url && image === null,
      frame: portraitFrame(node.id, url, image ?? null, mode.value, node.characterType, effectivePortraitOverrides.value) }]
  })))
}
