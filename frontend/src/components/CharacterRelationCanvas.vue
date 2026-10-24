<script setup lang="ts">
import { computed, onMounted, onUnmounted, shallowRef, watch } from 'vue'
import cytoscape, { type Core, type Layouts, type Position, type StylesheetJson } from 'cytoscape'
import fcose from 'cytoscape-fcose'
import type { CharacterEdge, CharacterNode } from '../types/characterGraph'
import CharacterPortrait from './CharacterPortrait.vue'
import type { CharacterPath } from '../utils/characterAnalysis'
import { characterElements, characterLayoutOptions, packCharacterComponents, separateCharacterBodies, edgeKey, nodeKey, type CharacterLayout } from '../utils/characterLayout'
import { focusedPositions } from '../utils/focusNetwork'
import { componentQuality, repairLayout, topologyFingerprint } from '../utils/layoutQuality'
import { usePortraitFrames, usePortraitMode } from '../composables/usePortraitFrames'
import { detailLevel, placeLabels, projectRelations, type DetailLevel, type ScreenLabel } from '../utils/semanticZoom'

cytoscape.use(fcose)
const props = defineProps<{ nodes: CharacterNode[]; edges: CharacterEdge[]; selectedId: string | null; selectedEdgeId: string | null; layout: CharacterLayout; resetKey: number; showEdgeLabels: boolean; showPortraits: boolean; path: CharacterPath | null; focusOnSelection?: boolean; audience?: 'characters' | 'staff' }>()
const emit = defineEmits<{ selectNode: [id: string]; selectEdge: [id: string]; clearSelection: [] }>()
const portraitMode = usePortraitMode()
const portraits = usePortraitFrames(() => props.nodes, portraitMode)
const container = shallowRef<HTMLDivElement>(), error = shallowRef(''), imageError = shallowRef(''), zoomPercent = shallowRef(100)
let cy: Core | undefined, observer: ResizeObserver | undefined, activeLayout: Layouts | undefined, hovered: string | null = null
let renderTimer: ReturnType<typeof setTimeout> | undefined
let positioning = false, focusFrame=0, focusGeneration=0
function stopFocus(){cancelAnimationFrame(focusFrame);focusGeneration++}
const savedPositions = new Map<string, Position>()
const manualPositions = new Map<string, Position>()
let topology = '', pendingLayout = false, resizeFrame = 0, lastRect = ''
let viewportWidth = 0, viewportHeight = 0
let overviewZoom = 1, semanticFrame = 0, hoveredEdge: string | null = null
const zoomDetail = shallowRef<DetailLevel>('overview')
const depthView=shallowRef(true), dark=shallowRef(false)
let theme:MediaQueryList|undefined
const themeChanged=()=>{dark.value=!!theme?.matches;applyDepth()}
function applyDepth(){if(!cy)return;const enabled=depthView.value&&props.nodes.length<=100;cy.nodes().toggleClass('depth-on',enabled);cy.nodes().toggleClass('dark-node',dark.value);cy.edges().toggleClass('dark-edge',dark.value);cy.nodes().filter(node=>node.data('organization')).addClass('organization-node');cy.nodes().filter(node=>node.data('local')).addClass('local-node')}
const expandedGroup = shallowRef<CharacterEdge[]>([])
const detailNames = { overview: '总览', middle: '中景', detail: '细节' }
const activeRelation = computed(() => props.edges.find(edge => edge.id === props.selectedEdgeId))
let textContext: CanvasRenderingContext2D | null = null
function pruneExpandedGroup() {
  const ids = new Set(expandedGroup.value.map(edge => edge.id))
  const remaining = props.edges.filter(edge => ids.has(edge.id))
  expandedGroup.value = remaining.length > 1 ? remaining : []
}
const pointerEvents = ['pointerdown', 'pointermove', 'mousedown', 'touchstart', 'wheel'] as const
function viewportReady() {
  const el = container.value
  return props.layout !== 'roster' && !!el && el.clientWidth > 0 && el.clientHeight > 0 && el.getClientRects().length > 0
}
// Source messages can move the canvas without changing its size. Invalidate the
// native hit-test rectangle before Cytoscape handles the pointer, in CSS pixels.
function refreshViewport() {
  if (!cy || !viewportReady()) return
  const r = container.value!.getBoundingClientRect(), rect = `${r.x},${r.y},${r.width},${r.height}`
  if (rect !== lastRect) { lastRect = rect; cy.resize() }
  const width = cy.width(), height = cy.height()
  const changed = viewportWidth > 0 && (width !== viewportWidth || height !== viewportHeight)
  viewportWidth = width; viewportHeight = height
  if (changed) queueViewportAdjustment()
}
function queueViewportAdjustment() {
  cancelAnimationFrame(resizeFrame)
  resizeFrame = requestAnimationFrame(() => {
    if (!viewportReady()) return
    refreshViewport()
    if (pendingLayout) runLayout()
    else if (props.focusOnSelection && props.selectedId) centerSelection()
    else fit()
  })
}
function recordRepairs(retry = true) {
  if (!cy) return
  const repairs = repairLayout(cy, new Set(manualPositions.keys()), retry)
  if (repairs.length) {
    cy.scratch('_layoutDiagnostics', repairs)
    error.value = repairs.some(r => r.recovery === 'circle') ? '检测到布局退化，异常分组已改用二维环形排列。' : '检测到布局退化，已重新计算异常分组。'
  }
}
const roster = computed(() => [...props.nodes].sort((a, b) => (a.role === '主角' ? -1 : 0) - (b.role === '主角' ? -1 : 0) || a.label.localeCompare(b.label, 'zh-CN')))
const style: StylesheetJson = [
  { selector: 'node', style: {
    width: 'data(size)', height: 'data(size)', shape: node => node.data('shape'), 'background-color': 'data(color)',
    'border-width': 3, 'border-color': 'data(color)', label: 'data(label)', color: '#40594f',
    'font-family': 'Microsoft YaHei, sans-serif', 'font-size': 13, 'text-valign': 'bottom', 'text-margin-y': 9,
    'text-wrap': 'ellipsis', 'text-max-width': '104px', 'text-background-color': '#fcfdf9',
    'text-background-opacity': .9, 'text-background-padding': '3px', 'text-events': 'yes', 'min-zoomed-font-size': 8,
    'overlay-opacity': 0,
  } },
  { selector:'node.depth-on',style:{'background-fill':'linear-gradient','background-gradient-stop-colors':['#f8fcfa','#afc7bb'],'background-gradient-direction':'to-bottom','border-width':2,'border-color':'#d4e3db',ghost:'yes','ghost-offset-x':2,'ghost-offset-y':7,'ghost-opacity':.14,'underlay-color':'#94abb9','underlay-opacity':.06,'underlay-padding':8}},
  {selector:'edge.dark-edge',style:{color:'#dce9e2','text-background-color':'#1d2d26'}},
  { selector:'node.local-node',style:{'border-style':'dashed','border-color':'#b08b51'}},
  { selector:'node.organization-node',style:{'shape':'round-rectangle','border-width':4,'border-color':'#94abb9','background-color':'#dbe8ee'}},
  { selector:'node.dark-node',style:{'background-color':'#3b574b','background-gradient-stop-colors':['#719687','#2a4238'],color:'#dce9e2','text-background-color':'#1d2d26'}},
  { selector: 'edge', style: {
    width: 1.5, 'line-color': 'data(color)', 'target-arrow-color': 'data(color)', 'target-arrow-shape': edge => edge.data('arrow'),
    'arrow-scale': .85, 'curve-style': 'bezier', 'control-point-step-size': 48,
    'line-style': edge => edge.data('line'), opacity: .7, label: '', 'font-size': 11, color: '#40594f',
    'text-rotation': 'none', 'text-background-color': '#fcfdf9', 'text-background-opacity': .95,
    'text-background-padding': '4px', 'text-events': 'yes', 'min-zoomed-font-size': 9, 'overlay-opacity': 0,
  } },
  { selector: 'edge.labels-on', style: { label: 'data(label)' } },
  { selector: 'node.portraits-on', style: { 'background-image': node => node.data('portraitImage') || 'none', 'background-fit': 'none',
    'background-width': node => `${node.data('portraitWidth') || 65}%`, 'background-height': node => `${node.data('portraitHeight') || 65}%`,
    'background-position-x': node => `${node.data('portraitX') ?? 50}%`, 'background-position-y': node => `${node.data('portraitY') ?? 50}%`,
    'background-clip': 'node', 'background-image-crossorigin': 'anonymous' } },
  { selector: '.faded', style: { opacity: .1, 'text-opacity': .15 } },
  { selector: 'node.neighbor', style: { opacity: 1, 'border-color': '#bed3c2', 'border-width': 3, 'min-zoomed-font-size': 0 } },
  { selector: 'node.active', style: { opacity: 1, 'border-color': '#d6a66a', 'border-width': 5, 'font-weight': 700, 'underlay-color':'#d6a66a','underlay-padding':13,'underlay-opacity':.16, 'min-zoomed-font-size': 0, 'z-index': 20 } },
  { selector: 'edge.active', style: { opacity: 1, width: 2.8, label: 'data(label)', 'min-zoomed-font-size': 0, 'z-index': 10 } },
  { selector: 'node.path-node', style: { 'border-color': '#6c9db2', 'border-width': 4 } },
  { selector: 'edge.path-edge', style: { 'line-color': '#5c91ad', 'target-arrow-color': '#5c91ad', width: 4 } },
]

function applyEmphasis() {
  if (!cy) return
  cy.batch(() => {
    const all = cy!.elements()
    all.removeClass('faded neighbor active labels-on path-node path-edge')
    cy!.nodes().toggleClass('portraits-on', props.showPortraits)
    if (props.showEdgeLabels) cy!.edges().addClass('labels-on')
    const nodeId = hovered || (props.selectedId ? nodeKey(props.selectedId) : null)
    const item = nodeId ? cy!.getElementById(nodeId) : props.selectedEdgeId ? cy!.getElementById(edgeKey(props.selectedEdgeId)) : undefined
    if (!item?.length) {
      if (props.path) {
        let elements = cy!.collection()
        for (const id of props.path.nodeIds) elements = elements.union(cy!.getElementById(nodeKey(id)))
        for (const id of props.path.edgeIds) elements = elements.union(cy!.getElementById(edgeKey(id)))
        if (elements.length) { all.difference(elements).addClass('faded'); elements.nodes().addClass('active path-node'); elements.edges().addClass('active path-edge') }
      }
      return
    }
    const nearby = nodeId ? item.closedNeighborhood() : cy!.collection().union(item).union(item.connectedNodes())
    all.difference(nearby).addClass('faded')
    nearby.nodes().addClass('neighbor'); nearby.edges().addClass('active'); item.addClass('active')
  })
  scheduleSemanticStyles()
}
function rememberPositions() {
  cy?.nodes().forEach(n => { savedPositions.set(n.data('originalId'), { ...n.position() }) })
  // Preserve filtered-out coordinates while bounding reuse across legacy works.
  const currentIds = new Set(props.nodes.map(node => node.id))
  for (const id of savedPositions.keys()) {
    if (savedPositions.size <= 1000) break
    if (!currentIds.has(id)) savedPositions.delete(id)
  }
}
function applyPortraits() {
  if (!cy) return
  cy.batch(() => cy!.nodes().forEach(node => {
    const portrait = portraits.value.get(node.data('originalId'))
    if (portrait) node.data({ portraitImage: portrait.url, portraitWidth: portrait.frame.width, portraitHeight: portrait.frame.height,
      portraitX: portrait.frame.x, portraitY: portrait.frame.y })
  }))
}
function updateZoom() {
  if (!cy || positioning) return
  zoomPercent.value = Math.round(cy.zoom() * 100)
  scheduleSemanticStyles()
}
function scheduleSemanticStyles() {
  cancelAnimationFrame(semanticFrame)
  semanticFrame = requestAnimationFrame(applySemanticStyles)
}
function applySemanticStyles() {
  if (!cy || positioning || !viewportReady()) return
  zoomDetail.value = detailLevel(cy.zoom(), overviewZoom, zoomDetail.value)
  const priority = new Set(props.path?.edgeIds || [])
  if (props.selectedEdgeId) priority.add(props.selectedEdgeId)
  if (hoveredEdge) priority.add(hoveredEdge)
  const projected = projectRelations(props.edges, zoomDetail.value, priority)
  const displayed = new Map(projected.map(item => [item.id, item]))
  // Expand/collapse parallel edges before asking the renderer for their curves.
  // Otherwise a label is positioned against the previous parallel-edge midpoint.
  cy.batch(() => cy!.edges().forEach(edge => { edge.style('display', displayed.has(edge.data('originalId')) ? 'element' : 'none') }))
  const candidates: ScreenLabel[] = []
  const obstacles = cy.nodes().map(node => ({ ...node.renderedPosition(), width: node.renderedOuterWidth(), height: node.renderedOuterHeight() }))
  textContext ??= document.createElement('canvas').getContext('2d')
  const measuredWidth = (text: string, font: number, max = Infinity, bold = false) => {
    if (textContext) textContext.font = `${bold ? '700 ' : ''}${font}px Microsoft YaHei, sans-serif`
    return Math.min(max, textContext?.measureText(text).width ?? text.length * font) + 10
  }
  const z = cy.zoom(), pan = cy.pan()
  const nodeFont = Math.max(11, Math.min(28, 12 / z)) * z
  const edgeFont = Math.max(11, Math.min(28, 11 / z)) * z
  const focus = hovered || (props.selectedId ? nodeKey(props.selectedId) : null)
  for (const node of cy.nodes()) {
    const important = node.id() === focus || !!props.path?.nodeIds.includes(node.data('originalId'))
    if (zoomDetail.value !== 'overview' || important || node.data('role') === '主角' || node.data('degree') >= 3) {
      const p = node.renderedPosition()
      candidates.push({ id: node.id(), text: node.data('label') as string, position: { x: p.x, y: p.y + node.renderedHeight() / 2 + 10 + 6 * z + nodeFont / 2 }, width: measuredWidth(node.data('label'), nodeFont, Math.max(104 * z, 104), node.hasClass('active')), height: nodeFont + 6 * z, priority: important ? 4 : 2 })
    }
  }
  for (const item of projected) {
    const edge = cy.getElementById(edgeKey(item.id))
    if (!edge.length || !props.showEdgeLabels && !priority.has(item.id)) continue
    // A parallel edge hidden at overview has no renderer midpoint until it is
    // drawn again. Use its endpoint midpoint for the first detail frame.
    const midpoint = edge.midpoint(), source = edge.source().position(), target = edge.target().position()
    const p = midpoint && Number.isFinite(midpoint.x) && Number.isFinite(midpoint.y) ? midpoint : { x: (source.x + target.x) / 2, y: (source.y + target.y) / 2 }
    // A relation hidden by semantic aggregation has no rendered endpoints yet.
    // Keep the first expanded frame finite until Cytoscape draws it again.
    const finite = (point: Position | undefined) => point && Number.isFinite(point.x) && Number.isFinite(point.y)
    const renderedSource = edge.sourceEndpoint(), renderedTarget = edge.targetEndpoint(), controls = edge.controlPoints()
    const sourcePoint = finite(renderedSource) ? renderedSource : source, targetPoint = finite(renderedTarget) ? renderedTarget : target
    const alternatives = [0.4, 0.6, 0.3, 0.7].map(t => {
      const c = controls?.length === 1 ? controls[0]! : null
      const q = c ? { x: (1-t)**2*sourcePoint.x + 2*(1-t)*t*c.x + t*t*targetPoint.x, y: (1-t)**2*sourcePoint.y + 2*(1-t)*t*c.y + t*t*targetPoint.y } : { x: sourcePoint.x*(1-t)+targetPoint.x*t, y: sourcePoint.y*(1-t)+targetPoint.y*t }
      return { x: q.x*z+pan.x, y: q.y*z+pan.y }
    })
    candidates.push({ id: edge.id(), text: item.label, position: { x: p.x * z + pan.x, y: p.y * z + pan.y }, candidates: alternatives, width: measuredWidth(item.label, edgeFont), height: edgeFont + 10, priority: priority.has(item.id) ? 5 : edge.hasClass('active') ? 1 : 0 })
  }
  const budget = zoomDetail.value === 'overview' ? Math.max(8, Math.min(48, cy.nodes().length + 4)) : zoomDetail.value === 'middle' ? Math.max(16, Math.min(120, cy.nodes().length * 3)) : candidates.length
  const captions = placeLabels(candidates, budget, obstacles, { x: 0, y: 0, width: cy.width(), height: cy.height() })
  cy.batch(() => {
    cy!.nodes().forEach(node => { node.style({ label: node.data('label'), 'text-opacity': captions.has(node.id()) ? node.hasClass('faded') ? .15 : 1 : 0,
      'text-events': captions.has(node.id()) ? 'yes' : 'no', 'text-margin-y': 10 / z + 6,
      'font-size': Math.max(11, Math.min(28, 12 / cy!.zoom())), 'text-max-width': Math.max(104, 104 / cy!.zoom()), 'min-zoomed-font-size': 0 }) })
    cy!.edges().forEach(edge => {
      const item = displayed.get(edge.data('originalId'))
      const point = captions.get(edge.id()), midpoint = edge.midpoint()
      edge.data('memberIds', item?.members || [])
      edge.style({ display: item ? 'element' : 'none', label: item?.label || ' ', 'text-opacity': captions.has(edge.id()) ? 1 : 0, 'text-events': captions.has(edge.id()) ? 'yes' : 'no',
        'text-margin-x': point && midpoint ? (point.x - pan.x) / z - midpoint.x : 0, 'text-margin-y': point && midpoint ? (point.y - pan.y) / z - midpoint.y : 0,
        opacity: edge.hasClass('faded') ? .1 : edge.hasClass('active') ? 1 : zoomDetail.value === 'overview' && !item?.primary ? .22 : .7,
        'font-size': Math.max(11, Math.min(28, 11 / cy!.zoom())), 'min-zoomed-font-size': 0 })
    })
  })
  cy.scratch('_semanticZoom', { level: zoomDetail.value, overviewZoom, projectedEdges: projected.length, labels: captions.size, obstacles, placements: candidates.filter(c => captions.has(c.id)).map(c => ({ ...c, position: captions.get(c.id) })) })
  cy.forceRender()
}
function resetCaptionSizing() {
  cy?.nodes().removeStyle('label text-opacity text-events font-size text-max-width min-zoomed-font-size text-margin-y')
  cy?.edges().removeStyle('display opacity label text-opacity text-events font-size min-zoomed-font-size text-margin-x text-margin-y')
}
function fit() {
  if (cy?.nodes().length && viewportReady()) {
    refreshViewport();stopFocus()
    positioning = true
    try { cy.nodes().stop(true,false);cy.stop(true,false); resetCaptionSizing(); cy.fit(undefined, cy.width() < 500 ? 24 : 45); overviewZoom = cy.zoom(); cy.maxZoom(Math.max(3, overviewZoom * 3.2)); zoomDetail.value = 'overview' }
    finally { positioning = false; updateZoom() }
  }
}
function centerSelection() {
  if (!cy || !props.selectedId || !viewportReady()) { pendingLayout = true; return }
  refreshViewport()
  stopFocus();activeLayout?.stop();cy.nodes().stop(true,false);cy.stop(true,false)
  const previous=new Map(cy.nodes().map(node=>[node.id(),{...node.position()}] as const)),oldPan={...cy.pan()},oldZoom=cy.zoom()
  positioning = true; resetCaptionSizing()
  const positions = focusedPositions(props.nodes, props.edges, props.selectedId)
  cy.nodes().positions(node => node.data('originalId')===props.selectedId ? {x:0,y:0} : manualPositions.get(node.data('originalId')) || positions.get(node.data('originalId')) || node.position())
  recordRepairs(false)
  cy.fit(undefined, cy.width() < 500 ? 24 : 45); overviewZoom = cy.zoom(); cy.maxZoom(Math.max(3, overviewZoom * 3.2)); zoomDetail.value = 'overview'
  const selected = cy.getElementById(nodeKey(props.selectedId))
  if (selected.length) {
    cy.fit(selected.closedNeighborhood(), cy.width() < 500 ? 28 : 80)
    cy.zoom(Math.min(1.05, cy.zoom()))
    const position = selected.position(), zoom = cy.zoom()
    cy.pan({ x: cy.width() / 2 - position.x * zoom, y: cy.height() / 2 - position.y * zoom })
  }
  const targetPan={...cy.pan()},targetZoom=cy.zoom(), targets=new Map(cy.nodes().map(node=>[node.id(),{...node.position()}] as const))
  const finish=()=>{positioning=false;rememberPositions();applyEmphasis();updateZoom()}
  if(props.nodes.length<=100&&!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    cy.nodes().positions(node=>previous.get(node.id())||node.position());cy.viewport({zoom:oldZoom,pan:oldPan});applyEmphasis()
    const generation=focusGeneration,instance=cy,started=performance.now()
    const frame=(now:number)=>{
      if(generation!==focusGeneration||cy!==instance)return
      const progress=Math.min(1,(now-started)/320),ease=1-(1-progress)**3
      instance.batch(()=>instance.nodes().positions(node=>{const end=targets.get(node.id())||node.position(),start=previous.get(node.id())||end;return {x:start.x+(end.x-start.x)*ease,y:start.y+(end.y-start.y)*ease}}))
      instance.viewport({zoom:oldZoom+(targetZoom-oldZoom)*ease,pan:{x:oldPan.x+(targetPan.x-oldPan.x)*ease,y:oldPan.y+(targetPan.y-oldPan.y)*ease}})
      if(progress<1)focusFrame=requestAnimationFrame(frame);else finish()
    }
    focusFrame=requestAnimationFrame(frame)
  } else finish()
}
function runLayout() {
  if (!cy?.nodes().length || props.layout === 'roster') return
  if (!viewportReady()) { pendingLayout = true; return }
  pendingLayout = false
  if (props.focusOnSelection && props.selectedId) { centerSelection(); return }
  refreshViewport()
  stopFocus();activeLayout?.stop(); error.value = ''
  positioning = true; resetCaptionSizing()
  try {
    const pinned = cy.nodes().filter(n => manualPositions.has(n.data('originalId'))).nodes()
    pinned.positions(n => manualPositions.get(n.data('originalId'))!); pinned.lock()
    const options = characterLayoutOptions(props.layout, cy.nodes().length)
    if (props.layout === 'network' && pinned.length) Object.assign(options, { fixedNodeConstraint: pinned.map(node => ({ nodeId: node.id(), position: { ...node.position() } })) })
    try { activeLayout = cy.layout(options); activeLayout.run() }
    finally { pinned.unlock() }
    if (props.layout === 'network') recordRepairs()
    if (!manualPositions.size) { separateCharacterBodies(cy); if (props.layout === 'network') packCharacterComponents(cy, cy.width() / cy.height()) }
  }
  catch { error.value = '网络布局暂不可用，已改用环形布局。'; cy.layout(characterLayoutOptions('circular')).run() }
  finally { positioning = false }
  fit()
  rememberPositions(); applyEmphasis(); updateZoom()
}
function render(reuse = true) {
  if (!cy) return
  activeLayout?.stop(); if (reuse) rememberPositions(); else { savedPositions.clear(); manualPositions.clear(); expandedGroup.value = [] }; hovered = null; hoveredEdge = null
  pruneExpandedGroup()
  const nextTopology = topologyFingerprint(props.nodes, props.edges)
  const canReuse = reuse && topology === nextTopology && props.nodes.every(n => savedPositions.has(n.id))
  topology = nextTopology
  if(canReuse) {
    // Selection and metadata updates keep the existing renderer objects and coordinates.
    // Recreating animated nodes with the same IDs can interrupt the animation pool.
    cy.batch(()=>{for(const element of characterElements(props.nodes,props.edges,savedPositions)) {
      if(props.focusOnSelection && element.group==='nodes')element.data.size+=20
      cy!.getElementById(String(element.data.id)).data(element.data)
    }})
    applyPortraits();applyDepth();applyEmphasis();updateZoom();return
  }
  stopFocus();cy.nodes().stop(true,false);cy.stop(true,false)
  cy.batch(() => {
    cy!.elements().remove()
    const elements = characterElements(props.nodes, props.edges, savedPositions)
    if (props.focusOnSelection) for (const item of elements) if (item.group === 'nodes') item.data.size += 20
    cy!.add(elements)
  })
  applyPortraits();applyDepth()
  if (!viewportReady()) { pendingLayout = true; applyEmphasis(); return }
  if (props.focusOnSelection && props.selectedId) { centerSelection(); return }
  const healthy = cy.elements().components().every(c => ['valid', 'small'].includes(componentQuality(c).reason) || c.nodes().some(n => manualPositions.has(n.data('originalId'))))
  if (canReuse && healthy) { fit(); applyEmphasis(); updateZoom() } else runLayout()
}
function revealSelection() {
  if (!cy) return
  if (props.focusOnSelection && props.selectedId) { centerSelection(); return }
  if (props.focusOnSelection && !props.selectedEdgeId) { runLayout(); return }
  applyEmphasis()
  const item = props.selectedId ? cy.getElementById(nodeKey(props.selectedId)).closedNeighborhood() : props.selectedEdgeId ? cy.getElementById(edgeKey(props.selectedEdgeId)).union(cy.getElementById(edgeKey(props.selectedEdgeId)).connectedNodes()) : undefined
  if (!item?.length) return
  // Selection changes only styling and camera, never the layout or dragged positions.
  cy.stop()
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) cy.fit(item, 40)
  else cy.animate({ fit: { eles: item, padding: 40 } }, { duration: 220 })
}
function changeZoom(factor: number) { if (cy) { refreshViewport(); cy.zoom({ level: Math.max(cy.minZoom(), Math.min(cy.maxZoom(), cy.zoom() * factor)), renderedPosition: { x: cy.width() / 2, y: cy.height() / 2 } }) } }
function saveImage() {
  if (!cy) return
  imageError.value = ''
  applySemanticStyles()
  try { const link = document.createElement('a'); link.href = cy.png({ full: false, scale: 2, bg: '#fcfdf9' }); link.download = props.audience === 'staff' ? '制作分工图谱.png' : '角色关系图谱.png'; link.click() }
  catch { imageError.value = '图片暂时无法生成，可关闭头像后重试保存。' }
}
onMounted(() => {
  if (!container.value) return
  cy = cytoscape({ container: container.value, style, elements: [], minZoom: .08, maxZoom: 3, boxSelectionEnabled: false, autounselectify: true })
  cy.on('tap', 'node', event => emit('selectNode', event.target.data('originalId')))
  cy.on('tap', 'edge', event => {
    const ids = new Set<string>(event.target.data('memberIds') || [])
    expandedGroup.value = ids.size > 1 ? props.edges.filter(edge => ids.has(edge.id)) : []
    emit('selectEdge', event.target.data('originalId'))
  })
  cy.on('tap', event => { if (event.target === cy) { expandedGroup.value = []; emit('clearSelection') } })
  cy.on('mouseover', 'node', event => { hovered = event.target.id(); applyEmphasis() })
  cy.on('mouseout', 'node', () => { hovered = null; applyEmphasis() })
  cy.on('mouseover', 'edge', event => { hoveredEdge = event.target.data('originalId'); scheduleSemanticStyles() })
  cy.on('mouseout', 'edge', () => { hoveredEdge = null; scheduleSemanticStyles() })
  cy.on('dragfree', 'node', event => { manualPositions.set(event.target.data('originalId'), { ...event.target.position() }); rememberPositions() })
  cy.on('zoom', updateZoom)
  cy.on('pan position', scheduleSemanticStyles)
  // CSS/browser zoom can change the renderer size before ResizeObserver fires.
  // Keep the selected center aligned using the core's actual CSS-pixel bounds.
  cy.on('resize', () => {
    if (!cy || !viewportReady()) return
    const width = cy.width(), height = cy.height()
    const changed = viewportWidth > 0 && (width !== viewportWidth || height !== viewportHeight)
    viewportWidth = width; viewportHeight = height
    if (changed && !positioning && props.focusOnSelection && props.selectedId) {
      const node = cy.getElementById(nodeKey(props.selectedId))
      if (node.length) { const p = node.position(), z = cy.zoom(); cy.pan({ x: width / 2 - p.x * z, y: height / 2 - p.y * z }) }
    }
  })
  for (const event of pointerEvents) container.value.addEventListener(event, refreshViewport, { capture: true, passive: true })
  theme=window.matchMedia('(prefers-color-scheme: dark)');theme.addEventListener('change',themeChanged);themeChanged()
  observer = new ResizeObserver(queueViewportAdjustment); observer.observe(container.value)
  render()
})
// Batch progressive source updates and never run a layout for obsolete props.
watch(() => [props.nodes, props.edges], () => {
  pruneExpandedGroup()
  scheduleSemanticStyles()
  clearTimeout(renderTimer)
  renderTimer = setTimeout(() => render(), 150)
})
watch(() => props.layout, runLayout, { flush: 'post' })
watch(() => [props.selectedId, props.selectedEdgeId], () => { if (props.selectedId) expandedGroup.value = []; revealSelection() }, { flush: 'post' })
watch(() => props.showEdgeLabels, applyEmphasis)
watch(() => props.showPortraits, applyEmphasis)
watch(depthView,applyDepth)
watch(portraits, applyPortraits)
watch(() => props.path, () => { applyEmphasis(); if (cy && props.path?.nodeIds.length) { const elements = cy.collection().union(props.path.nodeIds.map(id => cy!.getElementById(nodeKey(id))).reduce((all, item) => all.union(item), cy.collection())); cy.fit(elements, 70) } }, { flush: 'post' })
watch(() => props.resetKey, () => render(false))
onUnmounted(() => {
  stopFocus();clearTimeout(renderTimer); cancelAnimationFrame(resizeFrame); cancelAnimationFrame(semanticFrame); observer?.disconnect()
  for (const event of pointerEvents) container.value?.removeEventListener(event, refreshViewport, true)
  theme?.removeEventListener('change',themeChanged);activeLayout?.stop();cy?.nodes().stop();cy?.destroy(); savedPositions.clear(); manualPositions.clear()
})
</script>

<template>
  <div class="relation-canvas-wrapper">
    <p v-if="activeRelation" class="notice" role="status">选中关系：{{ nodes.find(n => n.id === activeRelation?.source)?.label }} {{ activeRelation.directed ? '→' : '—' }} {{ nodes.find(n => n.id === activeRelation?.target)?.label }} · {{ activeRelation.label }}{{ activeRelation.ended ? '（已结束）' : '' }}。{{ activeRelation.description }}</p>
    <div v-if="layout !== 'roster'" class="canvas-reading-note"><template v-if="audience === 'staff'"><span><i></i> 制作人员</span><span><i class="support"></i> 方形节点为职务</span></template><template v-else><span><i></i> 主角</span><span><i class="support"></i> 其他定位见角色索引</span></template><span>{{ focusOnSelection && selectedId ? '已围绕选中节点排列 · 适应画布可看全图' : '大小代表直接关联人数' }}</span></div>
    <div v-show="layout !== 'roster'" ref="container" class="relation-canvas" aria-hidden="true"></div>
    <div v-if="expandedGroup.length" class="relation-group" aria-label="聚合关系明细"><strong>这组连线包含 {{ expandedGroup.length }} 条来源关系</strong><button v-for="edge in expandedGroup" :key="edge.id" @click="emit('selectEdge', edge.id)">{{ nodes.find(n => n.id === edge.source)?.label }} {{ edge.directed ? '→' : '—' }} {{ nodes.find(n => n.id === edge.target)?.label }} · {{ edge.label }}{{ edge.ended ? ' · 已结束' : '' }}</button><button @click="expandedGroup = []">收起</button></div>
    <div v-if="layout === 'roster'" class="canvas-roster" :aria-label="audience === 'staff' ? '制作名册' : '角色名册'"><button v-for="node in roster" :key="node.id" :aria-pressed="selectedId === node.id" @click="emit('selectNode', node.id)"><CharacterPortrait :node="node"/><span><strong>{{ node.label }}</strong><small>{{ node.role || '未标注定位' }}</small></span></button></div>
    <p v-if="error" class="canvas-error" role="status">{{ error }} <button @click="runLayout">重试</button></p>
    <p v-if="imageError" class="canvas-error" role="alert">{{ imageError }} <button @click="saveImage">重试保存</button></p>
    <div class="canvas-tools" aria-label="图谱视图工具"><template v-if="layout !== 'roster'"><button aria-label="放大图谱" @click="changeZoom(1.2)">＋</button><output aria-label="缩放比例">{{ zoomPercent }}%</output><button aria-label="缩小图谱" @click="changeZoom(1 / 1.2)">−</button><button @click="fit">适应画布</button><button @click="runLayout">重新布局</button><button @click="saveImage">保存图片</button></template><label class="depth-control"><input v-model="depthView" type="checkbox">空间层次{{ nodes.length>100?'（大图自动简化）':'' }}</label><label class="portrait-control">图片<select v-model="portraitMode"><option value="auto">头像取景</option><option value="full">完整图片</option></select></label></div>
    <p class="canvas-hint">{{ layout === 'roster' ? '点击节点查看档案及关系' : `${detailNames[zoomDetail]} · 放大逐步展开关系 · 点击聚合连线查看明细 · 拖动空白处平移` }}</p>
  </div>
</template>

<style scoped>
.depth-control{display:flex;align-items:center;gap:6px;font-size:11px}.depth-control input{width:auto}.relation-canvas-wrapper{position:relative;min-width:0;background:radial-gradient(circle,#dce6dc .7px,transparent .8px) 0 0/22px 22px,#fcfdf9}
.relation-canvas{width:100%;height:620px;min-width:0;touch-action:none}
.canvas-reading-note{display:flex;align-items:center;gap:16px;flex-wrap:wrap;padding:14px 20px 0;color:#7b8d78;font-size:10px}
.canvas-reading-note span{display:inline-flex;align-items:center;gap:5px}.canvas-reading-note i{width:8px;height:8px;border-radius:50%;background:#5a8879}.canvas-reading-note .support{background:#94abb9}
.canvas-tools{display:flex;justify-content:center;align-items:center;gap:4px;flex-wrap:wrap;padding:10px 12px;background:#ffffffd9;border-top:1px solid #e6ecdf}
.canvas-tools button{min-height:38px;font-size:11px;padding:8px 12px;border:0;background:transparent}.canvas-tools button:hover{background:#edf3e7}.canvas-tools output{min-width:45px;text-align:center;color:#7d8e76;font-size:10px;font-variant-numeric:tabular-nums}
.portrait-control{display:flex;flex-direction:row;align-items:center;gap:7px;font-size:11px}.portrait-control select{font-size:11px;width:auto;padding:7px}
.relation-group{display:flex;gap:6px;flex-wrap:wrap;padding:12px 18px;border-top:1px solid #e6ecdf;background:#f2f7f2;font-size:11px}.relation-group strong{width:100%}.relation-group button{font-size:11px;padding:6px 9px}
.canvas-hint{margin:0;padding:8px 15px 14px;color:#8b9785;font-size:10px;text-align:center;background:#ffffffd9;line-height:1.8}
.canvas-error{padding:12px 20px;font-size:12px;background:#fff4da;color:#8b6e35}
.canvas-roster{display:grid;grid-template-columns:repeat(auto-fill,minmax(155px,1fr));gap:12px;max-height:620px;overflow:auto;padding:20px;scrollbar-width:thin}.canvas-roster button{display:flex;align-items:center;gap:10px;text-align:left;min-height:76px;background:#fff;border-color:#e0e8d8;min-width:0}.canvas-roster button[aria-pressed=true]{background:#edf4e7;border-color:#8fab80}.canvas-roster button>span{min-width:0}.canvas-roster strong{display:block;font-size:12px;font-weight:550;overflow-wrap:anywhere}.canvas-roster small{display:block;font-size:10px;color:#8c9c7e;margin-top:6px}
@media(prefers-color-scheme:dark){.relation-canvas-wrapper{background:radial-gradient(ellipse at 50% 45%,#2b4337,transparent 75%),#17241e}.canvas-tools,.canvas-hint{background:#1d2d26;color:#bdcec3}.canvas-reading-note{color:#bdcec3}}
@media(max-width:650px){.relation-canvas{height:480px}.canvas-reading-note{gap:9px;padding:12px 14px 0;font-size:9px}.canvas-tools button{min-height:40px;padding:8px 10px}.canvas-hint{font-size:9px}}
</style>


