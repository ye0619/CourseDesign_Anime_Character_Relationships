<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
const username = ref(''), password = ref(''), error = ref(''), loading = ref(false)
const auth = useAuthStore(), route = useRoute(), router = useRouter()
async function submit() { loading.value = true; error.value = ''; try { await auth.login(username.value, password.value); password.value = ''; const redirect = String(route.query.redirect || '/admin/imports'); await router.push(redirect.startsWith('/admin/') ? redirect : '/anime') } catch(e) { error.value = e instanceof Error ? e.message : '登录失败' } finally { loading.value = false } }
</script>
<template><section class="login-panel panel"><p class="eyebrow">映间 · 管理入口</p><h1>登录管理账户</h1><p class="muted">公开查询无需登录。管理账户由系统管理员创建。</p><form class="stack" @submit.prevent="submit"><label>用户名<input v-model="username" autocomplete="username" required maxlength="50"></label><label>密码<input v-model="password" type="password" autocomplete="current-password" required maxlength="72"></label><p v-if="error" class="error" role="alert">{{ error }}</p><button class="primary" :disabled="loading">{{ loading ? '正在登录…' : '登录' }}</button></form></section></template>
