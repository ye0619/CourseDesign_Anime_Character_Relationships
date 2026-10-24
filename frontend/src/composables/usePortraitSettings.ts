import { computed, shallowRef } from 'vue'
import { imageFingerprint, portraitOverrides, type ImageDimensions, type PortraitOverride } from '../utils/portraitFraming'
const key = 'animegraph.portraits.v1'
function read(): PortraitOverride[] {
  try {
    const rows: unknown = JSON.parse(localStorage.getItem(key) || '[]')
    if (!Array.isArray(rows)) return []
    return rows.filter((r): r is PortraitOverride => r && typeof r.entityUid === 'string' && typeof r.imageFingerprint === 'string' && r.reviewed === true && ['portrait', 'full'].includes(r.mode) && [r.focalX, r.focalY, r.scale].every(Number.isFinite) && r.focalX >= 0 && r.focalX <= 1 && r.focalY >= 0 && r.focalY <= 1 && r.scale >= 1 && r.scale <= 3).slice(-200)
  } catch { return [] }
}
const local = shallowRef<PortraitOverride[]>(read())
export const effectivePortraitOverrides = computed(() => [...local.value, ...portraitOverrides])
export function savePortraitSetting(entityUid: string, url: string, image: ImageDimensions, adjustment: Pick<PortraitOverride, 'mode' | 'focalX' | 'focalY' | 'scale'> | null): boolean {
  const fingerprint = imageFingerprint(url, image)
  const next = local.value.filter(row => !(row.entityUid === entityUid && row.imageFingerprint === fingerprint))
  if (adjustment) next.push({ ...adjustment, entityUid, imageFingerprint: fingerprint, reviewed: true })
  try { localStorage.setItem(key, JSON.stringify(next.slice(-200))); local.value = next.slice(-200); return true }
  catch { return false }
}
