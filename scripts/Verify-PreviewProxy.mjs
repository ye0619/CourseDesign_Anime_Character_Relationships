import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const listen = server => new Promise(resolve => server.listen(0, '127.0.0.1', () => resolve(server.address().port)))
const close = server => new Promise(resolve => server.close(resolve))
let requests = 0
const source = createServer((req, res) => {
  requests++; res.writeHead(200, { 'content-type': 'application/json' })
  res.end(JSON.stringify({ marker: 'local-preview-proxy', path: req.url }))
})
const sourcePort = await listen(source), probe = createServer()
const frontendPort = await listen(probe); await close(probe)
const child = spawn(process.execPath, [fileURLToPath(new URL('../frontend/node_modules/vite/bin/vite.js', import.meta.url)), 'preview', '--host', '127.0.0.1', '--port', String(frontendPort)], {
  cwd: fileURLToPath(new URL('../frontend', import.meta.url)), env: { ...process.env, SERVER_PORT: String(sourcePort) }, stdio: 'ignore', windowsHide: true,
})
try {
  let ready = false
  for (let i = 0; i < 50; i++) {
    try { const res = await fetch(`http://127.0.0.1:${frontendPort}/`); assert((await res.text()).includes('<title>映间')); ready = true; break } catch {}
    if (child.exitCode !== null) throw Error('Preview exited before readiness')
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  assert(ready, 'Isolated preview did not start')
  const result = await (await fetch(`http://127.0.0.1:${frontendPort}/api/v1/health/live`)).json()
  assert.equal(result.marker, 'local-preview-proxy'); assert.equal(result.path, '/api/v1/health/live'); assert.equal(requests, 1)
  console.log('Built preview uses SERVER_PORT for its API proxy; isolated loopback source passed. Existing application ports untouched.')
} finally { child.kill(); await close(source) }
