import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/anime' },
    { path: '/library', component: () => import('../views/LibraryPage.vue') },
    { path: '/curated', redirect: route => ({ path: '/anime', query: route.query }) },
    { path: '/preferences', component: () => import('../views/PreferencesPage.vue') },
    { path: '/anime', name: 'home', component: () => import('../views/ExploreAnimePage.vue') },
    { path: '/explore/anime/:id', component: () => import('../views/ExploreAnimeDetailPage.vue') },
    { path: '/explore/anime/:id/network', component: () => import('../views/OnlineNetworkPage.vue') },
    { path: '/people', component: () => import('../views/CatalogPage.vue') },
    { path: '/characters', component: () => import('../views/CatalogPage.vue') },
    { path: '/anime/:uid', component: () => import('../views/EntityDetailPage.vue'), beforeEnter: route => /^anime:bangumi:([1-9]\d*)$/.test(String(route.params.uid)) ? `/explore/anime/${String(route.params.uid).split(':').at(-1)}` : true },
    { path: '/:collection(people|characters)/:uid', component: () => import('../views/EntityDetailPage.vue') },
    { path: '/login', redirect: '/library' },
    { path: '/graph', component: () => import('../views/GraphPage.vue') },
    { path: '/community', component: () => import('../views/CommunityPage.vue') },
    { path: '/universes', component: () => import('../views/UniversePage.vue') },
    { path: '/universes/:uid', component: () => import('../views/UniverseDetailPage.vue') },
    { path: '/connections', component: () => import('../views/CharacterConnectionPage.vue') },
    { path: '/compare', component: () => import('../views/ComparePage.vue') },
    { path: '/analysis', component: () => import('../views/AnalysisPage.vue') },
    { path: '/maintenance/identities', component: () => import('../views/IdentityMaintenancePage.vue') },
    { path: '/admin/:pathMatch(.*)*', redirect: '/library' },
    { path: '/character-graph', name: 'character-graph', component: () => import('../views/CharacterGraphPage.vue') },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundPage.vue') },
  ],
})



