import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(new URL('../frontend/package.json', import.meta.url))
const { parse } = require('yaml')
const yaml = readFileSync(new URL('../compose.yaml', import.meta.url), 'utf8')
const compose = parse(yaml)
const example = readFileSync(new URL('../.env.example', import.meta.url), 'utf8')
const names = new Set([...example.matchAll(/^([A-Z][A-Z0-9_]*)=/gm)].map(m => m[1]))
for (const [, name] of yaml.matchAll(/\$\{([A-Z][A-Z0-9_]*)/g)) assert(names.has(name), `Missing configuration example: ${name}`)
for (const name of ['mysql', 'neo4j']) {
  const service = compose.services[name]
  assert(service.image && !service.image.endsWith(':latest'))
  assert(service.ports.every(port => port.startsWith('127.0.0.1:')), `${name} must bind to loopback`)
  assert(service.healthcheck.test.length && service.healthcheck.retries > 0)
  assert(service.volumes.length > 0)
  for (const volume of service.volumes) assert(Object.hasOwn(compose.volumes, volume.split(':')[0]))
}
assert(compose.services.mysql.environment.MYSQL_PASSWORD.includes(':?'))
assert(compose.services.neo4j.environment.NEO4J_AUTH.includes(':?'))
console.log('Compose YAML, required variable examples, loopback bindings, health checks and persistent volumes valid (static only; containers NOT started).')
