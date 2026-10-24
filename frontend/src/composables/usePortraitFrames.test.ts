import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, shallowRef } from 'vue'
import { usePortraitFrames } from './usePortraitFrames'
import type { CharacterNode } from '../types/characterGraph'

class TestImage {
  static requests: TestImage[] = []
  naturalWidth = 200
  naturalHeight = 600
  crossOrigin = ''
  referrerPolicy = ''
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  private value = ''
  set src(value: string) { this.value = value; if (value) TestImage.requests.push(this) }
  get src() { return this.value }
}
const node = (id: string): CharacterNode => ({ id, label: id, role: '', group: '', description: '', imageUrl: `https://lain.bgm.tv/pic/crt/l/${id}.jpg` })
const tick = async () => { await Promise.resolve(); await nextTick() }
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); TestImage.requests = [] })

describe('portrait dimensions loading lifecycle', () => {
  it('limits image requests to six and shares completed dimensions between consumers', async () => {
    vi.stubGlobal('Image', TestImage)
    const scope = effectScope(), input = shallowRef(Array.from({ length: 10 }, (_, i) => node(`budget-${i}`)))
    const frames = scope.run(() => usePortraitFrames(() => input.value, shallowRef('auto' as const)))!
    expect(TestImage.requests).toHaveLength(6)
    for (let i = 0; i < TestImage.requests.length; i++) TestImage.requests[i]!.onload?.()
    await tick()
    expect(TestImage.requests).toHaveLength(10)
    expect([...frames.value.values()].every(f => !!f.url && !f.frame.full)).toBe(true)
    const second = effectScope()
    second.run(() => usePortraitFrames(() => input.value, shallowRef('auto' as const)))
    await tick()
    expect(TestImage.requests).toHaveLength(10)
    scope.stop(); second.stop()
  })
  it('cannot publish an old image result after the node/image generation changes', async () => {
    vi.stubGlobal('Image', TestImage)
    const scope = effectScope(), input = shallowRef([node('old-generation')])
    const frames = scope.run(() => usePortraitFrames(() => input.value, shallowRef('auto' as const)))!
    const old = TestImage.requests[0]!
    input.value = [node('new-generation')]; await tick()
    old.onload?.(); await tick()
    expect(frames.value.has('old-generation')).toBe(false)
    expect(frames.value.get('new-generation')?.url).toBe('')
    TestImage.requests[1]!.onload?.(); await tick()
    expect(frames.value.get('new-generation')?.url).toContain('new-generation.jpg')
    scope.stop()
  })
  it('falls back on CORS/error and stalled images with a bounded timeout', async () => {
    vi.stubGlobal('Image', TestImage); vi.useFakeTimers()
    const scope = effectScope(), input = shallowRef([node('cors-error'), node('stalled-image')])
    const frames = scope.run(() => usePortraitFrames(() => input.value, shallowRef('auto' as const)))!
    TestImage.requests[0]!.onerror?.()
    await vi.advanceTimersByTimeAsync(12000); await tick()
    expect([...frames.value.values()].every(f => f.failed && !f.url)).toBe(true)
    scope.stop()
  })
})

