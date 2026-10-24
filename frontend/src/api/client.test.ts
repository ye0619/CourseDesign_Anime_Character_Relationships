import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, get, mutate } from './client'

afterEach(() => vi.unstubAllGlobals())

describe('API response handling', () => {
  it('gets a fresh CSRF token before every mutation, including after session rotation', async () => {
    const ok = (data: unknown) => new Response(JSON.stringify({ code: 'OK', message: '', data, requestId: 'request' }))
    const fetchMock = vi.fn().mockResolvedValueOnce(ok({ headerName: 'X-CSRF-TOKEN', token: 'before-login' })).mockResolvedValueOnce(ok({ id: 1 })).mockResolvedValueOnce(ok({ headerName: 'X-CSRF-TOKEN', token: 'after-login' })).mockResolvedValueOnce(ok(null))
    vi.stubGlobal('fetch', fetchMock)
    await mutate('/viewer/bind', 'POST', { username: 'sai' })
    await mutate('/viewer/sync', 'POST', { active: false })
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/session/csrf')
    expect(fetchMock.mock.calls[1]?.[1].headers['X-CSRF-TOKEN']).toBe('before-login')
    expect(fetchMock.mock.calls[3]?.[1].headers['X-CSRF-TOKEN']).toBe('after-login')
    expect(fetchMock.mock.calls[3]?.[1].body).toBe('{"active":false}')
  })

  it('preserves the failure status, readiness data and request ID', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'DEPENDENCIES_UNAVAILABLE', message: '数据服务尚未就绪',
      data: { status: 'NOT_READY' }, requestId: 'test-request',
    }), { status: 503 })))
    await expect(get('/health/ready')).rejects.toMatchObject({
      status: 503, code: 'DEPENDENCIES_UNAVAILABLE', data: { status: 'NOT_READY' }, requestId: 'test-request',
    })
  })

  it('rejects HTML from an unavailable proxy instead of treating it as data', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>Error</html>', { status: 502 })))
    await expect(get('/health/ready')).rejects.toBeInstanceOf(ApiError)
  })

  it('uses the same origin and unwraps successful responses', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'OK', message: '', data: { status: 'UP' }, requestId: 'test-request',
    })))
    vi.stubGlobal('fetch', fetchMock)
    await expect(get('/health/live')).resolves.toEqual({ status: 'UP' })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/health/live', expect.objectContaining({ credentials: 'same-origin' }))
  })
})
