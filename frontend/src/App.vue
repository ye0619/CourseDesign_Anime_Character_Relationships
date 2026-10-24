<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import { onMounted, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useViewerStore } from './stores/viewer'
const viewer=useViewerStore(), route=useRoute(), menuOpen=shallowRef(false)
watch(()=>route.fullPath,()=>menuOpen.value=false)
onMounted(()=>viewer.load())
</script>

<template>
  <a class="skip-link" href="#main">跳至主要内容</a>
  <div class="app-shell">
    <header class="site-header">
      <RouterLink class="brand" to="/anime" aria-label="映间首页">
        <span class="brand-symbol" aria-hidden="true">映</span>
        <span>映间<small>ANIME CONNECTIONS</small></span>
      </RouterLink>
      <button class="mobile-menu" :aria-expanded="menuOpen" aria-controls="primary-navigation" @click="menuOpen=!menuOpen">菜单</button><nav id="primary-navigation" :class="{ open: menuOpen }" aria-label="主导航"><RouterLink to="/anime">探索动画</RouterLink><RouterLink to="/connections">角色联系探索</RouterLink><RouterLink to="/library">我的片单</RouterLink><RouterLink to="/preferences">观看画像与推荐</RouterLink></nav>
    </header>
    <main id="main" tabindex="-1"><RouterView /></main>
    <footer class="site-footer"><span>记录看过的故事，发现自己的偏好。</span><span>映间 · 角色关系与观看偏好</span></footer>
  </div>
</template>
<style scoped>.mobile-menu{display:none}@media(max-width:850px){.mobile-menu{display:block;margin-left:auto}.site-header{flex-wrap:wrap}#primary-navigation{display:none;width:100%;flex-wrap:wrap}#primary-navigation.open{display:flex}}.nav-more{position:relative;font-size:12px}.nav-more summary{cursor:pointer;padding:10px 0}.nav-more>div{position:absolute;right:0;top:40px;min-width:230px;background:white;z-index:50;padding:16px;border:1px solid #dbe4d6;border-radius:10px;box-shadow:0 8px 30px #243d3914}.nav-more p{font-size:11px;color:#82927c;margin:0 0 12px;white-space:normal}.nav-more a{display:block;padding:10px!important}@media(max-width:550px){.nav-more>div{right:auto;left:0}}</style>


