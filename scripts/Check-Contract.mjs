import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'

const require = createRequire(new URL('../frontend/package.json', import.meta.url))
const { parse } = require('yaml')
const contract = parse(readFileSync(new URL('../docs/接口契约.yaml', import.meta.url), 'utf8'))
const errors = []
const verbs = ['get', 'post', 'patch', 'put', 'delete']
const operationIds = new Set()
let operationCount = 0

function inspect(value) {
  if (!value || typeof value !== 'object') return
  if (typeof value.$ref === 'string') {
    if (!value.$ref.startsWith('#/')) errors.push(`External reference is not expected: ${value.$ref}`)
    else {
      const found = value.$ref.slice(2).split('/').reduce((current, part) => current?.[part], contract)
      if (!found) errors.push(`Unresolved reference: ${value.$ref}`)
    }
  }
  Object.values(value).forEach(inspect)
}

if (contract.openapi !== '3.1.0') errors.push('OpenAPI version must be 3.1.0')
for (const [path, pathItem] of Object.entries(contract.paths ?? {})) {
  for (const method of verbs) {
    const operation = pathItem[method]
    if (!operation) continue
    operationCount++
    for (const requirement of operation.security ?? []) {
      for (const name of Object.keys(requirement)) if (!contract.components?.securitySchemes?.[name]) errors.push(`Unknown security scheme ${name}: ${method} ${path}`)
    }
    if (operationIds.has(operation.operationId)) errors.push(`Duplicate operationId: ${operation.operationId}`)
    operationIds.add(operation.operationId)
    for (const match of path.matchAll(/\{(\w+)\}/g)) {
      if (!operation.parameters?.some(parameter => parameter.in === 'path' && parameter.name === match[1] && parameter.required)) {
        errors.push(`Missing required path parameter ${match[1]}: ${method} ${path}`)
      }
    }
    if (!/^S\d{2}$/.test(operation['x-stage'])) errors.push(`Missing implementation stage: ${method} ${path}`)
    if (typeof operation['x-implemented'] !== 'boolean') errors.push(`Missing implementation status: ${method} ${path}`)
  }
}
inspect(contract)
if (errors.length) {
  console.error(errors.join('\n'))
  process.exitCode = 1
} else {
  console.log(`Contract parsed: ${Object.keys(contract.paths).length} paths, ${operationCount} operations; references and path parameters valid.`)
}
