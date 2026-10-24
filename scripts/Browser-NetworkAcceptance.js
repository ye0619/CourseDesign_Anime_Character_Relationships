// Run this async function in DevTools on the local application. It only mocks
// reserved test IDs in this tab, restores fetch/route, and never changes accounts.
async () => {
  const router = document.querySelector('#app').__vue_app__.config.globalProperties.$router
  const originalRoute = router.currentRoute.value.fullPath, realFetch = window.fetch
  const tasks = [], heaps = []
  const observer = new PerformanceObserver(list => tasks.push(...list.getEntries().map(e => e.duration)))
  const wait = ms => new Promise(resolve => setTimeout(resolve, ms))
  const until = async predicate => { for (let i = 0; i < 120; i++) { if (predicate()) return; await wait(50) } throw Error('UI wait timed out') }
  const meta = () => ({ fetchedAt: new Date().toISOString(), cached: false, stale: false, truncated: false, notice: '本地模拟' })
  const json = data => new Response(JSON.stringify({ code: 'OK', data }), { headers: { 'content-type': 'application/json' } })
  window.fetch = async (input, options) => {
    const path = String(input)
    if (path.includes('/explore/anime/2147483000')) {
      if (!path.includes('/network')) return json({ entity: { nameCn: '本地模拟大图', name: 'local', sourceId: '2147483000' }, localEntity: null, meta: {} })
      const page = Number(new URL(path, location.origin).searchParams.get('page') || 1)
      const nodes = Array.from({ length: 60 }, (_, i) => ({ id: `character:bangumi:${2147480000 + (page - 1) * 60 + i}`, label: `模拟角色${(page - 1) * 60 + i}`, originalName: '', role: '配角', group: '', description: '本地模拟', category: 'CHARACTER', roles: [], sourceUrl: '', imageUrl: '' }))
      return json({ subjectId: 2147483000, kind: 'CHARACTERS', nodes, edges: [], page, pageSize: 60, total: 240, meta: meta() })
    }
    if (/explore\/characters\/2147480[0-9]+\/relations/.test(path)) {
      const id = Number(path.match(/characters\/(\d+)/)[1])
      const edges = Array.from({ length: 12 }, (_, i) => {
        const target = 2147480000 + (id - 2147480000 + i + 1) % 240
        return { id: `local-${id}-${target}`, source: `character:bangumi:${id}`, target: `character:bangumi:${target}`, label: '模拟关系', relationType: 'FRIEND', directed: false, description: '本地模拟', evidence: '本地模拟', sourceName: '本地模拟' }
      })
      return json({ characterId: String(id), nodes: [], edges, meta: meta() })
    }
    return realFetch(input, options)
  }
  const loaded = () => Number(document.querySelector('.network-stage-footer')?.innerText.match(/已加载 (\d+)/)?.[1] || 0)
  const busy = () => [...document.querySelectorAll('.network-feedback')].some(e => e.innerText.includes('正在'))
  const cy = () => document.querySelector('.relation-canvas')?._cyreg?.cy
  try {
    observer.observe({ entryTypes: ['longtask'] })
    await router.push('/explore/anime/2147483000/network')
    await until(() => loaded() === 60 && !busy())
    for (let page = 2; page <= 4; page++) {
      document.querySelector('.network-stage-footer button').click()
      await until(() => loaded() === page * 60 && !busy())
    }
    const scale = document.querySelector('.graph-filter-bar select')
    scale.value = '1000'; scale.dispatchEvent(new Event('change', { bubbles: true }))
    await until(() => cy()?.nodes().length === 240)
    await wait(100)
    const nodes = cy().nodes()
    let overlaps = 0
    nodes.forEach((a, i) => nodes.slice(i + 1).forEach(b => {
      if (Math.hypot(a.position('x') - b.position('x'), a.position('y') - b.position('y')) < (a.width() + b.width()) / 2) overlaps++
    }))
    const large = { nodes: nodes.length, edges: cy().edges().length, overlaps, rawRelationships: 240 * 12, maxLongTaskMs: Math.max(0, ...tasks) }
    if (large.edges !== 400 || overlaps) throw Error(`Unexpected bounded graph: ${JSON.stringify(large)}`)
    const button = document.querySelector('.character-index button')
    button.click(); await wait(200)
    const selected = cy().nodes('.active').first(), p = selected.renderedPosition()
    const centerError = Math.hypot(p.x - cy().width() / 2, p.y - cy().height() / 2)
    if (centerError > 1) throw Error('Selected node not centered')
    let exported
    const realClick = HTMLAnchorElement.prototype.click
    try {
      HTMLAnchorElement.prototype.click = function () { exported = { name: this.download, png: this.href.startsWith('data:image/png;base64,'), bytes: this.href.length } }
      ;[...document.querySelectorAll('.canvas-tools button')].find(b => b.textContent === '保存图片').click()
    } finally { HTMLAnchorElement.prototype.click = realClick }
    if (!exported?.png) throw Error('PNG export failed')
    for (let i = 0; i < 12; i++) {
      await router.push('/preferences'); await wait(50)
      if (document.querySelectorAll('canvas').length) throw Error('Canvas retained after unmount')
      await router.push('/explore/anime/2147483000/network')
      await until(() => loaded() === 60 && !busy() && cy()?.nodes().length > 0)
      heaps.push(performance.memory?.usedJSHeapSize ?? null)
    }
    return { checkedAt: new Date().toISOString(), source: 'Browser-local synthetic sources; no Bangumi load test; PNG download intercepted', large, centerError, exported, cycles: 12, canvasCount: document.querySelectorAll('canvas').length, graphCount: document.querySelectorAll('.relation-canvas').length, heaps, note: 'Heap samples include garbage collection; bounded observations are not proof of no leaks or long-duration capacity.' }
  } finally {
    window.fetch = realFetch; observer.disconnect(); await router.push(originalRoute)
  }
}
