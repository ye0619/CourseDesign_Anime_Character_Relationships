<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { ElButton } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import { useHealthStore } from '../stores/health'

const health = useHealthStore()
const controller = new AbortController()
const checkedTime = computed(() => health.checkedAt ? new Date(health.checkedAt).toLocaleTimeString('zh-CN', { timeZone: 'Asia/Shanghai' }) : '尚未检查')
const features = [
  { index: '01', title: '作品与人物', text: '从动画出发，了解制作人员、角色与声优。', detail: '作品搜索 · 人物档案', to: null },
  { index: '02', title: '角色关系图谱', text: '查看角色间的亲属、朋友、同伴、对手与师徒关系。', detail: '角色选择 · 关系筛选 · 出处查看', to: '/character-graph' },
  { index: '03', title: '关联与合作', text: '比较两部作品，寻找共同人员和关联路径。', detail: '跨作品对比 · 合作统计', to: null },
]
onMounted(() => health.refresh(controller.signal))
onUnmounted(() => controller.abort())
</script>

<template>
  <section class="hero" aria-labelledby="hero-title">
    <div class="hero-copy">
      <p class="eyebrow"><span></span> 看见作品之间的联系</p>
      <h1 id="hero-title">每一部动画，<br>都是关系的<span>起点。</span></h1>
      <p class="hero-description">连接作品、制作人员与角色，沿着熟悉的名字，<br class="desktop-break">发现下一部值得了解的动画。</p>
      <div class="project-note"><span class="note-dot"></span>项目建设中 · 基础工程已建立，业务功能逐步接入</div>
    </div>
    <div class="hero-illustration" aria-hidden="true">
      <svg viewBox="0 0 480 340" fill="none">
        <circle cx="240" cy="170" r="146" stroke="#e0e6dc" stroke-dasharray="4 7" />
        <circle cx="240" cy="170" r="96" stroke="#e0e6dc" />
        <path d="M100 99L238 173L386 91M238 173L350 276M238 173L108 267M100 99L386 91" stroke="#b5c5ba" stroke-width="1.5" />
        <circle cx="100" cy="99" r="37" fill="#e2e9dc" /><circle cx="386" cy="91" r="31" fill="#f2ddc8" />
        <circle cx="350" cy="276" r="37" fill="#d6e5e3" /><circle cx="108" cy="267" r="28" fill="#eae4d4" />
        <circle cx="238" cy="173" r="56" fill="#2d5550" /><circle cx="238" cy="173" r="66" stroke="#c2d3c9" />
        <text x="238" y="180" text-anchor="middle" fill="white" font-size="20">动画作品</text>
        <text x="100" y="104" text-anchor="middle" fill="#456154" font-size="14">制作人员</text>
        <text x="386" y="96" text-anchor="middle" fill="#8b623f" font-size="14">角色</text>
        <text x="350" y="281" text-anchor="middle" fill="#456862" font-size="14">声优</text>
        <text x="108" y="272" text-anchor="middle" fill="#746743" font-size="14">作品</text>
      </svg>
      <span class="illustration-caption">关系结构示意 · 非实际查询结果</span>
    </div>
  </section>

  <section class="service-status" aria-labelledby="service-title" aria-live="polite">
    <div class="status-icon" :class="health.status" aria-hidden="true">{{ health.status === 'ready' ? '✓' : '↗' }}</div>
    <div class="status-copy"><h2 id="service-title">{{ health.label }}</h2><p>{{ health.message }}</p></div>
    <div class="status-action"><span>最近检查 {{ checkedTime }}</span><ElButton :loading="health.isChecking" @click="health.refresh(controller.signal)">重新检查</ElButton></div>
  </section>

  <section class="feature-section" aria-labelledby="features-title">
    <div class="section-heading"><h2 id="features-title">探索即将展开</h2><span>围绕作品，逐步连接</span></div>
    <div class="feature-grid">
      <article v-for="feature in features" :key="feature.index" class="feature-card">
        <div class="feature-top"><span>{{ feature.index }}</span><span class="development-badge">{{ feature.to ? '可体验' : '开发中' }}</span></div>
        <h3>{{ feature.title }}</h3><p>{{ feature.text }}</p><div class="feature-detail">{{ feature.detail }}</div>
        <RouterLink v-if="feature.to" :to="feature.to" class="feature-open">打开角色图谱 →</RouterLink>
      </article>
    </div>
  </section>
</template>

<style scoped>
.feature-open { display: inline-block; margin-top: 12px; color: #3f6c52; font-size: 11px; }
</style>
