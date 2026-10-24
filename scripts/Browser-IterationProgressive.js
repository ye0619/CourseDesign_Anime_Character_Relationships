// Development-only, synthetic reserved IDs. Restores fetch and route. No account writes.
async () => {
  const router = document.querySelector('#app').__vue_app__.config.globalProperties.$router
  const oldRoute = router.currentRoute.value.fullPath, realFetch = window.fetch
  const wait = ms => new Promise(resolve => setTimeout(resolve, ms))
  const until = async predicate => { for (let i = 0; i < 150; i++) { if (predicate()) return; await wait(40) } throw Error('Progressive UI check timed out') }
  const id = 2147483001, base = 2147479000
  const uid = i => `character:bangumi:${base + i}`
  let early = false, late = false
  const json = data => new Response(JSON.stringify({ code: 'OK', data }), { headers: { 'content-type': 'application/json' } })
  const meta = () => ({ fetchedAt: new Date().toISOString(), cached: false, stale: false, truncated: false })
  window.fetch = async (input, options) => {
    const path = String(input)
    if (path.includes(`/explore/anime/${id}`)) {
      if (!path.includes('/network')) return json({ entity: { nameCn: '本地渐进加载回归', name: 'local', sourceId: String(id) }, localEntity: null, meta: meta() })
      return json({ subjectId: id, kind: 'CHARACTERS', nodes: Array.from({ length: 24 }, (_, i) => ({ id: uid(i), label: `样本${i}`, originalName: '', role: '主角', group: '', description: '本地模拟', category: 'CHARACTER', roles: [], sourceUrl: '', imageUrl: '' })), edges: [], page: 1, pageSize: 60, total: 24, meta: meta() })
    }
    const match = path.match(/explore\/characters\/(21474790\d\d)\/relations/)
    if (match) {
      const i = Number(match[1]) - base
      while (!(i < 4 ? early : late)) { if (options?.signal?.aborted) throw new DOMException('Aborted', 'AbortError'); await wait(30) }
      const edges = [1, 2, 3].map(offset => ({ id: `sample-${i}-${offset}`, source: uid(i), target: uid((i + offset) % 24), relationType: 'FRIEND', label: '模拟朋友', directed: false, description: '', evidence: '本地模拟', sourceName: '本地模拟' }))
      return json({ characterId: String(base + i), nodes: [], edges, meta: meta() })
    }
    return realFetch(input, options)
  }
  const cy = () => document.querySelector('.relation-canvas')?._cyreg?.cy
  try {
    await router.push(`/explore/anime/${id}/network`)
    await until(() => cy()?.nodes().length > 0)
    const scale = document.querySelector('.graph-filter-bar select'); scale.value = '24'; scale.dispatchEvent(new Event('change',{bubbles:true}))
    const check = [...document.querySelectorAll('.visibility-controls label')].find(label => label.textContent.includes('显示暂无连线角色'))?.querySelector('input')
    check.checked = true; check.dispatchEvent(new Event('change', { bubbles: true }))
    early = true
    await until(() => cy()?.nodes().length === 24 && cy()?.edges().length === 12)
    await wait(200)
    const selected = cy().nodes().first(), manual = { x: selected.position('x') + 55, y: selected.position('y') + 35 }, selectedId = selected.id()
    selected.position(manual); selected.emit('dragfree')
    const before = JSON.stringify(cy().nodes().map(n => ({ ...n.position() })))
    late = true
    await until(() => cy()?.edges().length === 72)
    await wait(250)
    const preserved = cy().getElementById(selectedId).position(), after = JSON.stringify(cy().nodes().map(n => ({ ...n.position() })))
    if (Math.hypot(preserved.x - manual.x, preserved.y - manual.y) > .001) throw Error('Late topology overwrote dragged position')
    if (before === after) throw Error('Same-node late topology never recalculated layout')
    const overview = cy().scratch('_semanticZoom').overviewZoom
    const nodes = cy().nodes().map(n => ({ id: n.id(), ...n.position() }))
    for (const factor of [1.6, 2.8, 1]) { cy().zoom(overview * factor); await wait(80) }
    if (JSON.stringify(nodes) !== JSON.stringify(cy().nodes().map(n => ({ id: n.id(), ...n.position() })))) throw Error('Zoom moved dragged layout')
    // Force an actual degenerate unprotected component through the recovery path.
    const state = document.querySelector('.relation-canvas').__vueParentComponent?.setupState
    let repairs = null, repairCheck = 'SKIPPED_RELEASE_INTERNALS_UNAVAILABLE'
    if (state?.manualPositions && state?.recordRepairs) {
      state.manualPositions.clear(); cy().nodes().positions((n, i) => ({ x: i * 160, y: 29.5 })); state.recordRepairs()
      repairs = cy().scratch('_layoutDiagnostics')
      if (!repairs?.length || repairs.some(r => !['valid', 'small'].includes(r.after.reason))) throw Error('Degenerate component did not recover')
      repairCheck = 'PASSED_DEVELOPMENT_INTERNALS'
    }
    const result = { checkedAt: new Date().toISOString(), source: 'Browser-local synthetic graph; no Bangumi requests for reserved IDs', nodes: cy().nodes().length, earlyEdges: 12, lateEdges: cy().edges().length, manualPreserved: true, topologyRecalculated: true, zoomPreservedPositions: true, repairs, repairCheck }
    await router.push('/preferences'); await wait(100)
    if (document.querySelectorAll('.relation-canvas canvas').length) throw Error('Canvas retained after unmount')
    return { ...result, unmountReleasedCanvas: true }
  } finally { early = true; late = true; window.fetch = realFetch; await router.push(oldRoute) }
}
