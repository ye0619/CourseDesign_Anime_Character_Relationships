import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, get } from '../api/client'
import type { Readiness } from '../types/api'

export const useHealthStore = defineStore('health', () => {
  const status = ref<'loading' | 'ready' | 'not-ready' | 'offline'>('loading')
  const message = ref('正在检查数据服务')
  const checkedAt = ref<string | null>(null)
  const isChecking = ref(false)
  const label = computed(() => ({ loading: '正在连接', ready: '服务已就绪', 'not-ready': '数据服务未就绪', offline: '暂时无法连接' })[status.value])
  let generation = 0

  async function refresh(signal?: AbortSignal) {
    const current = ++generation
    isChecking.value = true
    try {
      const readiness = await get<Readiness>('/health/ready', signal)
      if (current !== generation) return
      status.value = readiness.status === 'READY' ? 'ready' : 'not-ready'
      message.value = readiness.status === 'READY' ? '数据服务已连接，可以继续功能开发。' : '数据服务尚未准备好，请稍后重试。'
      checkedAt.value = readiness.checkedAt
    } catch (error) {
      if (current !== generation || signal?.aborted) return
      status.value = error instanceof ApiError && error.code === 'DEPENDENCIES_UNAVAILABLE' ? 'not-ready' : 'offline'
      message.value = status.value === 'not-ready' ? '数据服务尚未准备好，请稍后重试。' : '暂时无法连接服务，请稍后重试。'
      checkedAt.value = new Date().toISOString()
    } finally {
      if (current === generation) isChecking.value = false
    }
  }

  return { status, message, checkedAt, isChecking, label, refresh }
})
