// Read-only public samples and isolated UI navigation; never changes accounts.
async () => {
  const wait = ms => new Promise(resolve => setTimeout(resolve, ms))
  const until = async predicate => { for (let i = 0; i < 160; i++) { if (predicate()) return; await wait(50) } throw Error('Name UI check timed out') }
  const read = async path => { const response = await fetch('/api/v1' + path); const body = await response.json(); if (!response.ok) throw Error(body.message || response.status); return body.data }
  const records = new Map(), works = []
  const record = node => records.set(node.id, { uid: node.id, kind: node.category, displayName: node.label, originalName: node.originalName,
    sourceChinese: node.nameAssertions?.find(n => n.kind === 'SOURCE_CHINESE')?.name || '', aliasCount: node.aliases?.length || 0, sourceUrl: node.sourceUrl })
  for (const id of [328609, 214265, 400602, 55770]) {
    const detail = await read(`/explore/anime/${id}`), roster = await read(`/explore/anime/${id}/network?kind=CHARACTERS`)
    roster.nodes.forEach(record)
    records.set(detail.entity.uid, { uid: detail.entity.uid, kind: 'ANIME', displayName: detail.entity.nameCn || detail.entity.name,
      originalName: detail.entity.name, sourceChinese: detail.entity.nameCn || '', aliasCount: null, sourceUrl: `https://bgm.tv/subject/${id}` })
    works.push({ id, title: detail.entity.nameCn || detail.entity.name, loadedCharacters: roster.nodes.length, totalCharacters: roster.total })
  }
  const staff = await read('/explore/anime/328609/network?kind=STAFF')
  staff.nodes.filter(n => n.category === 'PERSON').forEach(record)
  const preferred = await read('/explore/anime/328609/network?kind=CHARACTERS')
  const band = preferred.nodes.find(n => n.id === 'character:bangumi:125172')
  if (band?.label !== '结束乐队' || !band.aliases.includes('纽带乐队') || !band.aliases.includes('結束バンド')) throw Error('Preferred entity/aliases mismatch')
  const router = document.querySelector('#app').__vue_app__.config.globalProperties.$router, originalRoute = router.currentRoute.value.fullPath
  const matches = []
  try {
    await router.push('/explore/anime/328609/network')
    await until(() => document.querySelector('.character-search input'))
    const input = document.querySelector('.character-search input')
    for (const query of ['结束乐队', '纽带乐队', '結束バンド']) {
      input.value = query; input.dispatchEvent(new Event('input', { bubbles: true })); await wait(80)
      const buttons = [...document.querySelectorAll('.character-index button')]
      if (buttons.length !== 1 || !buttons[0].innerText.includes('结束乐队')) throw Error(`Alias did not resolve: ${query}`)
      matches.push({ query, results: buttons.length, displayName: buttons[0].querySelector('strong').textContent })
    }
    document.querySelector('.character-index button').click()
    await until(() => document.querySelector('.selected-character-title')?.innerText.includes('结束乐队'))
    await wait(300)
    const cy = document.querySelector('.relation-canvas')?._cyreg?.cy
    const selected = cy?.nodes('.active').first()
    if (selected?.data('originalId') !== band.id || selected?.data('label') !== band.label) throw Error('Canvas/inspector identity mismatch')
    const evidence = document.querySelector('.entity-names')
    evidence.open = true
    if (!evidence.innerText.includes('项目首选名') || !evidence.innerText.includes('纽带乐队')) throw Error('Name evidence missing')
    const all = [...records.values()], groups = {}
    for (const kind of ['CHARACTER', 'PERSON', 'ANIME']) {
      const subset = all.filter(n => n.kind === kind)
      groups[kind] = { sampled: subset.length, missingSourceChinese: subset.filter(n => !n.sourceChinese).length }
    }
    return { checkedAt: new Date().toISOString(), source: 'Sequential live public Bangumi names; first pages only; no bulk crawl or account writes',
      works, totalUniqueEntities: all.length, groups, preferredEntity: band, aliasSearch: matches, canvasIdentityConfirmed: true,
      translationAccuracy: 'Not automatically inferred; missing Chinese names and name variation do not prove mistranslation', records: all }
  } finally { await router.push(originalRoute) }
}
