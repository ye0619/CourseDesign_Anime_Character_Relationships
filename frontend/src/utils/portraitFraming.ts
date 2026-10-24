import reviewed from '../data/reviewedPortraits.json'
export type PortraitMode = 'auto' | 'full'
export interface ImageDimensions { width: number; height: number }
export interface HeadBox { x: number; y: number; width: number; height: number }
export interface PortraitOverride {
  entityUid: string
  imageFingerprint: string
  reviewed: true
  mode: 'portrait' | 'full'
  focalX: number
  focalY: number
  scale: number
  headBox?: HeadBox
}
export interface PortraitFrame { width: number; height: number; x: number; y: number; full: boolean }
// Generated from the reviewed source-pixel annotations; never inherit across images.
export const portraitOverrides = reviewed as readonly PortraitOverride[]
export const imageFingerprint = (url: string, image: ImageDimensions) => `${url}|${image.width}x${image.height}`
export const safePortraitUrl = (url?: string | null) => url && (/^https:\/\/lain\.bgm\.tv\/[^\s]+$/.test(url) || /^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(url) && url.length <= 720000) ? url : ''

export function portraitImageStyle(frame: PortraitFrame) {
  return { width: `${frame.width}%`, height: `${frame.height}%`, left: `${(100-frame.width)*frame.x/100}%`, top: `${(100-frame.height)*frame.y/100}%` }
}

function headFrame(box: HeadBox | undefined, image: ImageDimensions): PortraitFrame | undefined {
  if (!box || ![box.x, box.y, box.width, box.height].every(Number.isFinite) ||
      box.x < 0 || box.y < 0 || box.width <= 0 || box.height <= 0 ||
      box.x + box.width > image.width || box.y + box.height > image.height) return
  // Every marked corner lies at radius 44 in a radius-50 circle: 6% breathing room.
  let factor = 88 / Math.hypot(box.width, box.height)
  // CSS and Cytoscape both position by (container - image) * percentage.
  // Avoid the singular case of a dimension exactly equal to the container.
  if (Math.abs(image.width * factor - 100) < .001 || Math.abs(image.height * factor - 100) < .001) factor *= .9999
  const width = image.width * factor, height = image.height * factor
  const left = 50 - (box.x + box.width / 2) * factor
  const top = 50 - (box.y + box.height / 2) * factor
  return { width, height, x: left / (100 - width) * 100, y: top / (100 - height) * 100, full: false }
}

/** Percentages shared by DOM and Cytoscape. Reviewed head frames may need padding beyond the source edges. */
export function portraitFrame(entityUid: string, url: string, image: ImageDimensions | null, mode: PortraitMode = 'auto', characterType?: number | null,
  overrides = portraitOverrides): PortraitFrame {
  const ratio = image && image.width > 0 && image.height > 0 ? image.width / image.height : 1
  const override = image ? overrides.find(o => o.reviewed && o.entityUid === entityUid && o.imageFingerprint === imageFingerprint(url, image)) : undefined
  const full = mode === 'full' || override?.mode === 'full' || (!override && (ratio > 1.65 || (characterType != null && characterType !== 1)))
  if (!full && image) {
    const frame = headFrame(override?.headBox, image)
    if (frame) return frame
  }
  let width: number, height: number
  if (full) {
    height = 92 / Math.sqrt(1 + ratio * ratio); width = height * ratio
  } else {
    const scale = Math.max(1, Math.min(3, override?.scale || (ratio < .55 ? 1.5 : 1)))
    width = Math.max(100, 100 * ratio) * scale; height = Math.max(100, 100 / ratio) * scale
  }
  const focalX = full ? .5 : Math.max(0, Math.min(1, override?.focalX ?? .5))
  const focalY = full ? .5 : Math.max(0, Math.min(1, override?.focalY ?? 0))
  return { width, height, x: focalX * 100, y: focalY * 100, full }
}
