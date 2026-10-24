import {createHash,randomUUID} from 'node:crypto'
import {copyFileSync,existsSync,lstatSync,mkdirSync,readFileSync,readdirSync,writeFileSync,realpathSync} from 'node:fs'
import {dirname,resolve,relative,sep} from 'node:path'
import {fileURLToPath} from 'node:url'

const jar='backend/target/animegraph-backend-0.1.0-SNAPSHOT.jar'
const singles=['README.md','.env.example','.gitignore','compose.yaml','Start-Project.cmd','Stop-Project.cmd','修改计划10.21.md','backend/pom.xml','backend/mvnw','backend/mvnw.cmd','frontend/package.json','frontend/package-lock.json','frontend/index.html','frontend/tsconfig.json','frontend/vite.config.ts',jar]
const trees=['backend/src','backend/.mvn','frontend/src','frontend/dist','scripts','docs','plans','data/curated','data/samples']
const forbidden=/^(?:\.env(?!\.example$).*|\.local|\.git|\.agents|\.codex|node_modules|raw|normalized)$/i
export function safeRelative(path){return typeof path==='string'&&path.length>0&&!path.includes('\\')&&!path.includes(':')&&!path.startsWith('/')&&path.split('/').every(p=>p&&p!=='.'&&p!=='..'&&!forbidden.test(p))&&!/\.(pem|key|p12|pfx|jks)$/i.test(path)}
export const sha256=bytes=>createHash('sha256').update(bytes).digest('hex')
function rejectSymlinks(root,path){let current=root;for(const part of path.split('/')){current=resolve(current,part);if(lstatSync(current).isSymbolicLink())throw new Error(`Delivery symlink is not allowed: ${path}`)}}
export function collectFiles(root){
  const files=[]
  function add(path){if(!safeRelative(path))throw new Error(`Excluded or unsafe delivery path: ${path}`);rejectSymlinks(root,path);const full=resolve(root,path),stat=lstatSync(full);if(!stat.isFile())throw new Error(`Expected regular delivery file: ${path}`);files.push(path)}
  function walk(path){if(!existsSync(resolve(root,path)))return;rejectSymlinks(root,path);for(const entry of readdirSync(resolve(root,path),{withFileTypes:true})){const name=`${path}/${entry.name}`;if(forbidden.test(entry.name))continue;if(entry.isSymbolicLink())throw new Error(`Delivery symlink is not allowed: ${name}`);if(entry.isDirectory())walk(name);else if(!path.startsWith('data/samples')||name.endsWith('.json'))add(name)}}
  for(const path of singles)if(existsSync(resolve(root,path)))add(path)
  for(const path of trees)walk(path)
  return [...new Set(files)].sort()
}
export function codeFingerprint(root){return sha256(collectFiles(root).filter(p=>/^(backend\/src\/|backend\/\.mvn\/|frontend\/src\/|scripts\/|data\/curated\/)/.test(p)||['backend/pom.xml','frontend/package.json','frontend/package-lock.json','frontend/vite.config.ts','frontend/tsconfig.json'].includes(p)).map(p=>`${p}\0${sha256(readFileSync(resolve(root,p)))}`).join('\n'))}
export function attestArtifacts(root){const checks=JSON.parse(readFileSync(resolve(root,'.local/project-checks.json'),'utf8'));if(!checks.passed||checks.codeFingerprint!==codeFingerprint(root))throw new Error('Project checks do not match current source');const artifacts=collectFiles(root).filter(p=>p===jar||p.startsWith('frontend/dist/')).map(path=>({path,sha256:sha256(readFileSync(resolve(root,path)))}));if(!artifacts.some(a=>a.path===jar)||!artifacts.some(a=>a.path==='frontend/dist/index.html'))throw new Error('Build artifacts missing');const stamp={builtAt:new Date().toISOString(),codeFingerprint:checks.codeFingerprint,artifacts};writeFileSync(resolve(root,'.local/artifact-build.json'),JSON.stringify(stamp,null,2)+'\n');return stamp}
function secretsFromEnvironment(root){
  if(!existsSync(resolve(root,'.env')))return []
  return readFileSync(resolve(root,'.env'),'utf8').split(/\r?\n/).flatMap(line=>{const match=line.match(/^([A-Z][A-Z0-9_]*(?:PASSWORD|TOKEN|SECRET|CREDENTIAL)[A-Z0-9_]*)=(.*)$/);if(!match)return [];const value=match[2].trim().replace(/^(['"])(.*)\1$/,'$2');return value.length>=12?[value]:[]})
}
export function verifyFolder(root){
  const manifest=JSON.parse(readFileSync(resolve(root,'delivery-manifest.json'),'utf8'))
  if(manifest.schemaVersion!==2||!Array.isArray(manifest.files)||manifest.files.length===0)throw new Error('Invalid delivery manifest')
  const known=new Set()
  for(const file of manifest.files){if(!safeRelative(file.path)||known.has(file.path)||!Number.isSafeInteger(file.bytes)||!(/^[a-f0-9]{64}$/).test(file.sha256))throw new Error('Invalid or duplicate manifest path');known.add(file.path);rejectSymlinks(root,file.path);const full=resolve(root,file.path);const bytes=readFileSync(full);if(bytes.length!==file.bytes||sha256(bytes)!==file.sha256)throw new Error(`Delivery integrity mismatch: ${file.path}`)}
  function scan(path){for(const item of readdirSync(path,{withFileTypes:true})){const full=resolve(path,item.name),name=relative(root,full).split(sep).join('/');if(item.isSymbolicLink())throw new Error(`Symlink detected: ${name}`);if(item.isDirectory())scan(full);else if(name!=='delivery-manifest.json'&&!known.has(name))throw new Error(`Unlisted delivery file: ${name}`)}}
  scan(root);return manifest
}
export function prepare(root,output){
  const checks=JSON.parse(readFileSync(resolve(root,'.local/project-checks.json'),'utf8'))
  if(!checks.passed)throw new Error('Current project checks must pass before packaging')
  if(checks.codeFingerprint!==codeFingerprint(root))throw new Error('Source files changed since project checks; run Test-Project.ps1 again')
  const stamp=JSON.parse(readFileSync(resolve(root,'.local/artifact-build.json'),'utf8'))
  if(stamp.codeFingerprint!==checks.codeFingerprint||!Array.isArray(stamp.artifacts)||!stamp.artifacts.some(a=>a.path===jar))throw new Error('Build stamp does not match project checks')
  for(const artifact of stamp.artifacts)if(!safeRelative(artifact.path)||sha256(readFileSync(resolve(root,artifact.path)))!==artifact.sha256)throw new Error('Built artifact changed since packaging checks')
  const files=collectFiles(root)
  if(!files.includes(jar)||!files.includes('frontend/dist/index.html'))throw new Error('Current JAR and frontend build are required')
  const secrets=secretsFromEnvironment(root),records=[]
  if(existsSync(output))throw new Error('Delivery folder already exists; refusing overwrite')
  for(const path of files){const bytes=readFileSync(resolve(root,path));if(!/\.(jar|png|jpg|jpeg|webp|woff2?|ico)$/i.test(path)&&secrets.some(secret=>bytes.includes(Buffer.from(secret))))throw new Error(`Private configuration value found in delivery file: ${path}`);records.push({path,bytes:bytes.length,sha256:sha256(bytes)})}
  mkdirSync(output,{recursive:true})
  for(const file of records){const target=resolve(output,file.path);mkdirSync(dirname(target),{recursive:true});copyFileSync(resolve(root,file.path),target)}
  const manifest={schemaVersion:2,builtAt:new Date().toISOString(),apiContractVersion:checks.apiContractVersion,backend:jar,backendSha256:records.find(f=>f.path===jar).sha256,frontend:'frontend/dist',indexSha256:records.find(f=>f.path==='frontend/dist/index.html').sha256,checks,scope:'Local source and build review package. External databases, credentials, runtimes and npm dependencies are not bundled. New-machine and Docker acceptance remain outstanding.',samplePolicy:'Public sample JSON metadata only; no raw external image cache or database directory',files:records}
  writeFileSync(resolve(output,'delivery-manifest.json'),JSON.stringify(manifest,null,2)+'\n');verifyFolder(output);return manifest
}
const root=fileURLToPath(new URL('..',import.meta.url))
if(process.argv[1]&&realpathSync(resolve(process.argv[1]))===realpathSync(fileURLToPath(import.meta.url))){
  if(process.argv[2]==='--attest'){const stamp=attestArtifacts(root);console.log(JSON.stringify({buildArtifactsStamped:stamp.artifacts.length}));}
  else if(process.argv[2]==='--verify'){const manifest=verifyFolder(resolve(process.argv[3]));console.log(JSON.stringify({passed:true,files:manifest.files.length,apiContractVersion:manifest.apiContractVersion}));}
  else{const output=resolve(root,'.local/deliveries',new Date().toISOString().replaceAll(/[:.]/g,'-')+'-'+randomUUID().slice(0,8));const manifest=prepare(root,output);writeFileSync(resolve(root,'.local/delivery-manifest.json'),JSON.stringify({...manifest,packageDirectory:output},null,2)+'\n');console.log(JSON.stringify({packageDirectory:output,files:manifest.files.length,apiContractVersion:manifest.apiContractVersion}));}
}
