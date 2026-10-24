// Read-only browser acceptance: compare source-independent controls and source isolation.
async () => {
  const router=document.querySelector('#app').__vue_app__.config.globalProperties.$router
  const originalRoute=router.currentRoute.value.fullPath, realFetch=window.fetch, requests=[]
  const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms))
  const until=async predicate=>{for(let i=0;i<600;i++){if(predicate())return;await wait(50)}throw Error('Source parity UI timed out')}
  const click=text=>{const button=[...document.querySelectorAll('.online-network .tabs button')].find(button=>button.textContent===text);if(!button)throw Error('Missing tab '+text);button.click()}
  const values=selector=>[...document.querySelectorAll(selector)].map(element=>element.textContent.trim())
  window.fetch=(input,options)=>{requests.push(String(input));return realFetch(input,options)}
  const results=[]
  try {
    for(const source of ['LOCAL','ONLINE']) {
      const requestStart=requests.length
      await router.push('/explore/anime/9717?source='+source)
      await until(()=>document.querySelector('.online-network header p')?.textContent.includes(source==='LOCAL'?'Neo4j':'Bangumi'))
      click('角色关系')
      await until(()=>document.querySelector('.character-graph-workspace')&&!document.querySelector('.work-graph-panel > p[role=status]')?.textContent.includes('正在'))
      const tabs=values('.online-network .tabs button'), layouts=values('.segmented button')
      const filters=[...document.querySelectorAll('.graph-filter-bar > label')].map(element=>element.firstChild.textContent)
      const visibility=values('.visibility-controls label').map(value=>value.replace(/（\d+）/,''))
      const graph=document.querySelector('.graph-panel-footer').innerText
      const node=document.querySelector('.character-index button');node.click();await wait(100)
      if(!document.querySelector('.selected-character-title'))throw Error('Missing shared character inspector')
      click('制作人员');await until(()=>document.querySelector('.credits-panel tbody tr'))
      const staffHeads=values('.credits-panel th'), staffRows=document.querySelectorAll('.credits-panel tbody tr').length
      if(document.querySelectorAll('.online-network canvas').length)throw Error('Production table must have no canvas')
      const search=document.querySelector('.credits-panel input[type=search]')
      const firstName=document.querySelector('.credits-panel tbody tr td a').textContent
      search.value=firstName;search.dispatchEvent(new Event('input',{bubbles:true}));await wait(50)
      if(!document.querySelector('.credits-panel tbody tr')?.textContent.includes(firstName))throw Error('Staff search failed')
      search.value='';search.dispatchEvent(new Event('input',{bubbles:true}))
      click('配音与名册');await until(()=>document.querySelector('.cast-list'))
      const castSearch=document.querySelector('.credits-panel input[type=search]').placeholder
      const sourceReads=requests.slice(requestStart)
      if(source==='LOCAL' && sourceReads.some(path=>/\/explore\/(?:characters\/|anime\/\d+\/(?:network|credits))/.test(path)))throw Error('Local mode made online relationship requests')
      results.push({source,tabs,layouts,filters,visibility,graph,staffHeads,staffRows,staffCanvas:0,castSearch,sourceReads})
    }
    const invariantKeys=['tabs','layouts','filters','visibility','staffHeads','castSearch']
    for(const key of invariantKeys)if(JSON.stringify(results[0][key])!==JSON.stringify(results[1][key]))throw Error('Different source presentation: '+key)
    return {checkedAt:new Date().toISOString(),samePresentation:true,localReadIsolation:true,results}
  } finally {window.fetch=realFetch;await router.push(originalRoute)}
}
