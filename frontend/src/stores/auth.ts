import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { get, mutate, ApiError } from '../api/client'
export interface User { id: number; username: string; displayName: string; enabled: boolean; roles: string[] }
export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const loaded = ref(false)
  const isAdmin = computed(() => user.value?.roles.includes('ADMIN') ?? false)
  async function refresh() {
    try { user.value = await get<User>('/auth/me') }
    catch (error) { if (error instanceof ApiError && error.status === 401) user.value = null; else throw error }
    finally { loaded.value = true }
  }
  async function login(username: string, password: string) { user.value = await mutate<User>('/auth/login', 'POST', new URLSearchParams({ username, password })); loaded.value = true }
  async function logout() { await mutate('/auth/logout', 'POST'); user.value = null }
  return { user, loaded, isAdmin, refresh, login, logout }
})
