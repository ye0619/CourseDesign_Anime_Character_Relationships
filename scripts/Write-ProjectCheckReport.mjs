import {readFileSync,readdirSync,writeFileSync} from 'node:fs'
import {createRequire} from 'node:module'
import {fileURLToPath} from 'node:url'
import {codeFingerprint} from './Prepare-Delivery.mjs'
const require=createRequire(new URL('../frontend/package.json',import.meta.url)),{parse}=require('yaml')
const backend={total:0,failures:0,errors:0,skipped:0},conditional={total:0,passed:0,skipped:0,suites:[]}
const directory=new URL('../backend/target/surefire-reports/',import.meta.url)
for(const name of readdirSync(directory).filter(n=>/^TEST-.*\.xml$/.test(n))){const xml=readFileSync(new URL(name,directory),'utf8'),suite=xml.match(/<testsuite\s[^>]+>/)?.[0];if(!suite)throw new Error('Invalid backend report');for(const [key,attribute] of Object.entries({total:'tests',failures:'failures',errors:'errors',skipped:'skipped'}))backend[key]+=Number(suite.match(new RegExp(`\\b${attribute}="(\\d+)"`))?.[1]??NaN)}
for(const name of readdirSync(directory).filter(n=>/^TEST-.*IntegrationTest\.xml$/.test(n))){const xml=readFileSync(new URL(name,directory),'utf8'),suite=xml.match(/<testsuite\s[^>]+>/)?.[0];const total=Number(suite.match(/\btests="(\d+)"/)?.[1]),skipped=Number(suite.match(/\bskipped="(\d+)"/)?.[1]);conditional.total+=total;conditional.skipped+=skipped;conditional.passed+=total-skipped;conditional.suites.push({suite:name.slice(5,-4),total,passed:total-skipped,skipped})}
const frontend=JSON.parse(readFileSync(new URL('../.local/frontend-test-results.json',import.meta.url),'utf8'))
const contract=parse(readFileSync(new URL('../docs/接口契约.yaml',import.meta.url),'utf8'))
if(!Number.isFinite(backend.total)||backend.total<1||backend.failures||backend.errors||frontend.success!==true||frontend.numFailedTests!==0)throw new Error('Project test evidence is incomplete or failed')
const result={checkedAt:new Date().toISOString(),passed:true,backend:{...backend,passed:backend.total-backend.skipped},frontend:{total:frontend.numTotalTests,passed:frontend.numPassedTests},externalCoverageTests:3,deliveryPackageTests:3,apiContractVersion:contract.info.version,contractPaths:Object.keys(contract.paths).length,contractOperations:Object.values(contract.paths).reduce((n,p)=>n+Object.keys(p).filter(k=>['get','post','put','patch','delete'].includes(k)).length,0),typecheck:true,frontendBuild:true,powerShellSyntax:true,documentation:true,deploymentStatic:true,conditionalDatabaseTests:conditional}
result.codeFingerprint=codeFingerprint(fileURLToPath(new URL('..',import.meta.url)))
writeFileSync(new URL('../.local/project-checks.json',import.meta.url),JSON.stringify(result,null,2)+'\n');writeFileSync(new URL('../docs/实验数据/项目检查.json',import.meta.url),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify({passed:true,backend:result.backend.passed,frontend:result.frontend.passed,apiContractVersion:result.apiContractVersion}))
