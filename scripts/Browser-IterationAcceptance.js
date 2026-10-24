// Run this async function on the development app with the graph already loaded.
// CSS zoom and DPR emulation are recorded as simulations, not OS/browser hardware tests.
async () => {
  const wait = ms => new Promise(resolve => setTimeout(resolve, ms))
  const until = async predicate => { for (let i = 0; i < 100; i++) { if (predicate()) return; await wait(40) } throw Error('Iteration UI check timed out') }
  const el = document.querySelector('.relation-canvas'), cy = el?._cyreg?.cy
  if (!cy?.nodes().length) throw Error('Open a populated online graph first')
  const coordinates = () => JSON.stringify(cy.nodes().map(n => ({ id: n.id(), ...n.position() })))
  const initial = coordinates(), z = cy.scratch('_semanticZoom').overviewZoom, levels = []
  for (const [factor, expected] of [[1, 'overview'], [1.6, 'middle'], [2.8, 'detail'], [1, 'overview']]) {
    cy.zoom(Math.min(cy.maxZoom(), z * factor))
    await until(() => cy.scratch('_semanticZoom')?.level === expected)
    levels.push({ ...cy.scratch('_semanticZoom'), visibleEdgeLabels: cy.edges().filter(e => e.style('text-opacity') !== '0' && e.style('display') !== 'none').length })
  }
  if (coordinates() !== initial) throw Error('Zoom changed model coordinates')
  const root = document.documentElement, oldZoom = root.style.zoom, stage = el.parentElement, oldPadding = stage.style.paddingTop
  const hits = []
  try {
    for (const scale of [.8, 1, 1.25, 1.5, 2]) {
      root.style.zoom = String(scale)
      await wait(200)
      el.dispatchEvent(new PointerEvent('pointermove', { bubbles: true, clientX: 0, clientY: 0 }))
      await wait(100)
      stage.style.paddingTop = '57px'
      // Prime the old rectangle, then move the graph without a size change.
      cy.renderer().findContainerClientCoords()
      stage.style.paddingTop = '103px'
      const node = cy.nodes().first(), p = node.renderedPosition(), r = el.getBoundingClientRect()
      const sx = r.width / el.clientWidth, sy = r.height / el.clientHeight
      const x = r.left + p.x * sx, y = r.top + p.y * sy
      el.dispatchEvent(new PointerEvent('pointermove', { bubbles: true, clientX: x, clientY: y }))
      let tap
      const capture = event => { tap = { id: event.target.id(), error: Math.hypot(event.renderedPosition.x - p.x, event.renderedPosition.y - p.y) } }
      cy.one('tap', 'node', capture)
      const down = new MouseEvent('mousedown', { bubbles: true, clientX: x, clientY: y, button: 0, buttons: 1 })
      const up = new MouseEvent('mouseup', { bubbles: true, clientX: x, clientY: y, button: 0 })
      Object.defineProperty(down, 'which', { value: 1 }); Object.defineProperty(up, 'which', { value: 1 })
      el.querySelector('canvas').dispatchEvent(down); window.dispatchEvent(up)
      await until(() => tap != null)
      if (tap.id !== node.id() || tap.error > 4) throw Error(`Pointer mismatch: ${JSON.stringify(tap)}`)
      await wait(250)
      const centered = cy.getElementById(node.id()).renderedPosition(), centerError = Math.hypot(centered.x - cy.width() / 2, centered.y - cy.height() / 2)
      if (centerError > 1) throw Error(`Center error ${centerError}`)
      hits.push({ cssZoom: scale, dpr: devicePixelRatio, hitErrorCssPx: tap.error, centerErrorCssPx: centerError })
      stage.style.paddingTop = oldPadding
    }
  } finally { root.style.zoom = oldZoom; stage.style.paddingTop = oldPadding }
  const picture = document.querySelector('.portrait-control select'), previousMode = picture.value
  picture.value = 'full'; picture.dispatchEvent(new Event('change', { bubbles: true })); await wait(100)
  const fullFrames = cy.nodes().filter(n => n.data('portraitImage')).map(n => ({ id: n.id(), diagonalPercent: Math.hypot(n.data('portraitWidth'), n.data('portraitHeight')) }))
  if (fullFrames.some(f => f.diagonalPercent > 92.001)) throw Error('Full image extends outside circular safe area')
  let exported
  const realClick = HTMLAnchorElement.prototype.click
  try {
    HTMLAnchorElement.prototype.click = function () { exported = { name: this.download, png: this.href.startsWith('data:image/png;base64,'), bytes: this.href.length } }
    ;[...document.querySelectorAll('.canvas-tools button')].find(b => b.textContent === '保存图片').click()
  } finally { HTMLAnchorElement.prototype.click = realClick; picture.value = previousMode; picture.dispatchEvent(new Event('change', { bubbles: true })) }
  if (!exported?.png) throw Error('PNG export failed')
  return { checkedAt: new Date().toISOString(), url: location.pathname, source: 'Real graph, synthetic native mouse events and CSS zoom; DPR configured separately by browser emulation',
    nodes: cy.nodes().length, rawEdges: cy.edges().length, levels, zoomPreservedCoordinates: true, hits, fullFrames, exported }
}
