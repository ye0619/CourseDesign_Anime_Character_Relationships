import { copyFileSync, existsSync, lstatSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { createHash, randomUUID } from 'node:crypto'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
const root = fileURLToPath(new URL('..', import.meta.url))
const output = resolve(root, '.local/source-baselines', new Date().toISOString().replaceAll(/[:.]/g, '-') + '-' + randomUUID().slice(0, 8))
const paths = [], excluded = new Set(['node_modules', 'target', 'dist', '__pycache__'])
function walk(path) {
  const full = resolve(root, path)
  if (!existsSync(full)) return
  const stat = lstatSync(full)
  if (stat.isSymbolicLink()) throw new Error('Snapshot refuses links: ' + path)
  if (stat.isDirectory()) { for (const entry of readdirSync(full)) if (!excluded.has(entry)) walk(path + '/' + entry) }
  else paths.push(path)
}
for (const path of ['backend/src','backend/.mvn','frontend/src','scripts','docs','plans','data/curated','README.md','.gitignore','.env.example','compose.yaml','backend/pom.xml','backend/mvnw','backend/mvnw.cmd','frontend/package.json','frontend/package-lock.json','frontend/vite.config.ts','frontend/tsconfig.json','frontend/index.html','Start-Project.cmd','Stop-Project.cmd']) walk(path)
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const files = paths.sort().map(path => {
  const target = resolve(output, 'source', path)
  mkdirSync(dirname(target), { recursive: true }); copyFileSync(resolve(root, path), target)
  return { path, sha256: hash(readFileSync(target)) }
})
for (const file of files) {
  const restored = resolve(output, 'restore-check', file.path)
  mkdirSync(dirname(restored), { recursive: true }); copyFileSync(resolve(output, 'source', file.path), restored)
  if (hash(readFileSync(restored)) !== file.sha256) throw new Error('Restore mismatch: ' + file.path)
}
writeFileSync(resolve(output, 'manifest.json'), JSON.stringify({createdAt:new Date().toISOString(),scope:'Source only; database restore requires separate acceptance',restoreVerified:true,files},null,2)+'\n')
console.log(JSON.stringify({directory:output,files:files.length,restoreVerified:true}))
