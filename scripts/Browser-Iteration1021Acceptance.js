// Run on a loaded world graph. Synthetic density fixtures only change this tab's Vue props.
async () => {
  const el=document.querySelector('.relation-canvas'),cy=el?._cyreg?.cy,component=el?.__vueParentComponent
  if(!cy?.nodes().length||!component)throw Error('Open a populated world graph first')
  const raf=()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))
  const wait=ms=>new Promise(r=>setTimeout(r,ms))
  const overlaps=(a,b)=>a.x1<b.x2&&b.x1<a.x2&&a.y1<b.y2&&b.y1<a.y2
  const original={...component.props},cases=[]
  const check=async name=>{
    const z=cy.scratch('_semanticZoom').overviewZoom,positions=JSON.stringify(cy.nodes().map(n=>n.position()))
    for(const [factor,expected] of [[1,'overview'],[1.6,'middle'],[2.8,'detail']]){
      cy.zoom(z*factor);await raf()
      const bodies=cy.nodes().map(n=>({id:n.id(),box:n.renderedBoundingBox({includeLabels:false,includeOverlays:false,includeUnderlays:false})}))
      const labels=cy.elements().filter(e=>e.style('display')!=='none'&&Number(e.style('text-opacity'))>0&&e.style('label').trim()).map(e=>({id:e.id(),box:e.renderedBoundingBox({includeNodes:false,includeEdges:false,includeLabels:true,includeOverlays:false,includeUnderlays:false})}))
      const collisions=[]
      for(let i=0;i<labels.length;i++){
        for(const n of bodies)if(overlaps(labels[i].box,n.box))collisions.push({label:labels[i].id,node:n.id})
        for(let j=i+1;j<labels.length;j++)if(overlaps(labels[i].box,labels[j].box))collisions.push({label:labels[i].id,otherLabel:labels[j].id})
      }
      const level=cy.scratch('_semanticZoom').level
      cases.push({name,nodes:cy.nodes().length,edges:cy.edges().length,zoomFactor:factor,level,expected,visibleLabels:labels.length,collisions})
    }
    if(positions!==JSON.stringify(cy.nodes().map(n=>n.position())))throw Error('Zoom moved model coordinates')
  }
  try{
    await check('public-world')
    for(const [nodeCount,edgeCount] of [[24,100],[48,400]]){
      const nodes=Array.from({length:nodeCount},(_,i)=>({id:'fixture:'+i,label:'验收角色'+i,role:i<4?'主角':'配角',group:'',description:'Synthetic density fixture',imageUrl:''}))
      const edges=Array.from({length:edgeCount},(_,i)=>{const a=i%nodeCount;let b=(i*7+Math.floor(i/nodeCount)+1)%nodeCount;if(a===b)b=(b+1)%nodeCount;return {id:'fixture-edge:'+i,source:nodes[a].id,target:nodes[b].id,relationType:i%2?'FRIEND':'FAMILY',label:i%2?'朋友':'亲属',description:'Synthetic density fixture',evidence:'fixture',sourceName:'fixture',sourceUrl:'',directed:false}})
      Object.assign(component.props,{nodes,edges,layout:'circular',selectedId:null,selectedEdgeId:null,showEdgeLabels:true,showPortraits:false,resetKey:original.resetKey+1})
      await wait(650);await raf();await check(`${nodeCount}n-${edgeCount}e`)
    }
  }finally{Object.assign(component.props,original);await wait(650);await raf()}
  const realClick=HTMLAnchorElement.prototype.click;let exported
  try{HTMLAnchorElement.prototype.click=function(){exported={png:this.href.startsWith('data:image/png;base64,'),bytes:this.href.length,name:this.download}};[...document.querySelectorAll('.canvas-tools button')].find(b=>b.textContent==='保存图片').click()}finally{HTMLAnchorElement.prototype.click=realClick}
  return {checkedAt:new Date().toISOString(),viewport:{width:innerWidth,height:innerHeight,dpr:devicePixelRatio},source:'Actual Cytoscape renderer label/body bounds; real world plus synthetic 24/100 and 48/400 fixtures; model coordinates checked',cases,exported,passed:cases.every(c=>!c.collisions.length&&c.level===c.expected)&&exported?.png===true}
}
