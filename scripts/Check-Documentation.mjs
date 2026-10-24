import assert from 'node:assert/strict'
import {existsSync,readFileSync,readdirSync} from 'node:fs'
import {dirname,relative,resolve,sep} from 'node:path'
import {fileURLToPath} from 'node:url'
import {createRequire} from 'node:module'

const root=fileURLToPath(new URL('..',import.meta.url))
const require=createRequire(new URL('../frontend/package.json',import.meta.url))
const {parse}=require('yaml')
const files=['README.md']
function walk(path){for(const entry of readdirSync(resolve(root,path),{withFileTypes:true})){const name=path+'/'+entry.name;if(entry.isDirectory())walk(name);else if(name.endsWith('.md'))files.push(name)}}
walk('docs')
const errors=[]
let links=0,scriptReferences=0
for(const file of files){
  const content=readFileSync(resolve(root,file),'utf8')
  const prose=content.replace(/^```[^\n]*\n[\s\S]*?^```\s*$/gm,'')
  for(const match of prose.matchAll(/\[[^\]\n]*\]\(([^)\n]+)\)/g)){
    const url=match[1].trim().replace(/^<|>$/g,'')
    if(/^[a-z][a-z0-9+.-]*:/i.test(url)||url.startsWith('#'))continue
    const path=decodeURIComponent(url.split('#')[0].split('?')[0])
    if(!path)continue
    const target=resolve(dirname(resolve(root,file)),path)
    const local=relative(root,target)
    if(local.startsWith('..'+sep)||local==='..'||!existsSync(target))errors.push(file+': missing or outside-workspace link '+url)
    links++
  }
  for(const match of content.matchAll(/\b([A-Z][A-Za-z0-9-]*\.(?:ps1|mjs|cjs|js|py|java|cmd))\b/g)){
    const name=match[1]
    if(!existsSync(resolve(root,'scripts',name))&&!existsSync(resolve(root,name)))errors.push(file+': missing script '+name)
    scriptReferences++
  }
}
const read=name=>readFileSync(resolve(root,name),'utf8')
const contract=parse(read('docs/接口契约.yaml'))
const frontend=JSON.parse(read('frontend/package.json'))
const pom=read('backend/pom.xml')
const readme=read('README.md'),baseline=read('docs/技术基线.md'),model=read('docs/数据模型.md')
try{
  assert(readme.includes(contract.info.version),'README contract version differs from OpenAPI')
  const sql=readdirSync(resolve(root,'backend/src/main/resources/db/migration')).map(n=>Number(n.match(/^V(\d+)__/)?.[1]||0))
  const graph=readdirSync(resolve(root,'backend/src/main/resources/neo4j')).map(n=>Number(n.match(/^V(\d+)__/)?.[1]||0))
  assert(readme.includes('V'+Math.max(...sql))&&model.includes('V'+Math.max(...sql)),'SQL migration version drift')
  const graphVersion='V'+String(Math.max(...graph)).padStart(3,'0')
  assert(readme.includes(graphVersion)&&model.includes(graphVersion),'Neo4j migration version drift')
  const report=read('backend/src/main/java/com/animegraph/viewer/ViewingReportAnalyzer.java').match(/viewing-report\/\d+/)?.[0]
  assert(report&&readme.includes(report)&&read('docs/人员总体报告与资料复用.md').includes(report),'Report version drift')
  for(const dependency of ['vue','cytoscape','echarts'])assert(baseline.includes(frontend.dependencies[dependency]),'Dependency version drift: '+dependency)
  const boot=pom.match(/<artifactId>spring-boot-starter-parent<\/artifactId>\s*<version>([^<]+)/)?.[1]
  assert(boot&&baseline.includes(boot),'Spring Boot version drift')
  const summary=JSON.parse(read('docs/实验数据/项目检查.json'))
  assert(summary.apiContractVersion===contract.info.version,'Published check summary contract drift')
}catch(error){errors.push(error.message)}
if(errors.length){console.error(errors.join('\n'));process.exitCode=1}
else console.log(`Documentation valid: ${files.length} Markdown files, ${links} local links, ${scriptReferences} script references; contract, migrations, report and dependency versions match.`)
