import { shallowRef, watch, type Ref } from 'vue'
import { get } from '../api/client'

export function useExploreRequest<T>(url: Readonly<Ref<string>>, retainFor?: Readonly<Ref<string>>) {
  const data = shallowRef<T>(), pending = shallowRef(false), error = shallowRef(''), attempt = shallowRef(0)
  let retainedKey: string | undefined
  watch([url, attempt], async ([path], _previous, onCleanup) => {
    const controller = new AbortController(); let current = true
    onCleanup(() => { current = false; controller.abort() })
    pending.value = true; error.value = ''
    if (!retainFor || retainedKey !== retainFor.value) data.value = undefined
    retainedKey = retainFor?.value
    try { const response = await get<T>(path, controller.signal); if (current) data.value = response }
    catch (cause) { if (current) error.value = cause instanceof Error ? cause.message : '暂时无法获取资料，请重试' }
    finally { if (current) pending.value = false }
  }, { immediate: true })
  return { data, pending, error, retry: () => { attempt.value++ } }
}
