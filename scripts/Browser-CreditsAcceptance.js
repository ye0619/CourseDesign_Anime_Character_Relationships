// Run on the isolated local app page with credits-source-checks.json as the first JSON argument.
async (sourceChecks) => {
  const expected = JSON.parse(sourceChecks), wait = ms => new Promise(resolve => setTimeout(resolve, ms))
  const until = async predicate => { for (let i=0;i<200;i++) {if(predicate())return;await wait(50)} throw Error('Credits UI check timed out') }
  const read = async path => {const response=await fetch('/api/v1'+path), body=await response.json();if(!response.ok)throw Error(body.message||response.status);return body.data}
  const works=[]
  for(const work of expected.works) {
    const page=await read(`/explore/anime/${work.subjectId}/credits?kind=CAST&page=1`)
    let checked=0
    for(const row of work.rows) for(const cast of row.casts) {
      const actual=page.cast.find(item=>item.personUid===`person:bangumi:${cast.personId}` && item.characterUid===`character:bangumi:${row.characterId}` && item.sourceRelation===cast.relation)
      if(!actual)throw Error(`Missing association ${work.subjectId}:${row.characterId}:${cast.personId}`)
      const language=({3:'zh',4:'ja',5:'en',6:'ko'})[cast.relation]||null
      if(actual.language!==language || actual.version!==null || actual.episodeScope!==null)throw Error('Guessed or mismatched source fields')
      checked++
    }
    works.push({subjectId:work.subjectId,totalCharacterRows:page.coverage.total,loadedCharacterRows:page.coverage.returnedRows,people:page.people.length,castCredits:page.cast.length,checkedAssociations:checked,nextPage:page.coverage.nextPage,completeness:page.coverage.completeness})
  }
  const router=document.querySelector('#app').__vue_app__.config.globalProperties.$router, originalRoute=router.currentRoute.value.fullPath
  const realFetch=window.fetch, click=label=>{const button=[...document.querySelectorAll('.online-network button')].find(b=>b.textContent.trim()===label);if(!button)throw Error('Missing button '+label);button.click()}
  try {
    await router.push('/explore/anime/328609/network');await until(()=>document.querySelector('.online-network .tabs'))
    const oldCanvas=document.querySelector('.relation-canvas')?._cyreg?.cy
    click('配音与名册');await until(()=>document.querySelector('.credit-person'))
    if(oldCanvas && !oldCanvas.destroyed())throw Error('Previous canvas was not disposed')
    const panel=document.querySelector('.credits-panel'), input=panel.querySelector('input[type=search]')
    input.value='后藤一里';input.dispatchEvent(new Event('input',{bubbles:true}));await wait(150)
    const names=[...panel.querySelectorAll('.person-heading a')].map(a=>a.textContent)
    if(!names.includes('青山吉能')||!names.includes('邱涵菲'))throw Error('Character search did not retain voice cast')
    const details=panel.innerText
    if(!details.includes('中文')||!details.includes('CV 原声')||!details.includes('来源未提供'))throw Error('Language/unknown fields missing')
    input.value='';input.dispatchEvent(new Event('input',{bubbles:true}));await wait(50)
    click('制作人员');await until(()=>document.querySelector('.positions'))
    const before=document.querySelectorAll('.credits-panel tbody tr').length
    window.fetch=async (request,options)=>String(request).includes('/credits?kind=STAFF&page=2')?new Response(JSON.stringify({code:'TEST_PARTIAL',message:'验收模拟：第二页暂不可用'}),{status:503,headers:{'Content-Type':'application/json'}}):realFetch(request,options)
    click('读取下一页来源资料');await until(()=>document.querySelector('.credits-panel [role=alert]'))
    if(document.querySelectorAll('.credits-panel tbody tr').length!==before)throw Error('Failed page cleared previous staff')
    click('配音与名册');await wait(100)
    if(document.querySelector('.credits-panel [role=alert]')||!document.querySelector('.cast-list'))throw Error('Staff failure polluted cast')
    click('制作人员');await wait(100)
    if(!document.querySelector('.credits-panel [role=alert]'))throw Error('Independent failure state was lost')
    window.fetch=realFetch
    click('重试未完成页');await until(()=>!document.querySelector('.credits-panel [role=alert]')&&!document.querySelector('.credits-panel p[role=status]'))
    if(document.querySelector('.relation-canvas') || !document.querySelector('.credits-panel table'))throw Error('Staff must appear only in a table')
    click('配音与名册');await until(()=>document.querySelector('.cast-list'))
    const ready=await read('/health/ready')
    return {checkedAt:new Date().toISOString(),sourceSampleDate:expected.checkedAt,works,ui:{characterSearchPeople:names,languageAndUnknownFields:true,oldCanvasDisposed:!oldCanvas||oldCanvas.destroyed(),partialPageRetainedPeople:before,independentCategoryFailures:true,retrySucceeded:true,staffTableWithoutCanvas:true},health:ready}
  } finally {window.fetch=realFetch;await router.push(originalRoute)}
}
