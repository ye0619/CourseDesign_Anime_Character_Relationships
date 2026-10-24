<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import ProfileBreakdown from './ProfileBreakdown.vue'
import { mutate } from '../api/client'
import { usePrivateResource } from '../composables/usePrivateResource'
import type { ReportResult, ReportPerson, WorkCoverage } from '../types/viewingReport'
const props = defineProps<{ scope: string }>()
const { data, error, pending, refresh } = usePrivateResource<ReportResult>(() => '/viewer/report', () => props.scope,
  result => !!result.task && ['RUNNING', 'PENDING'].includes(result.task.status))
const busy = shallowRef(false), actionError = shallowRef(''), role = shallowRef(''), limit = shallowRef(20)
const view = shallowRef<'OVERALL' | 'ROLE'>('OVERALL')
const current = computed(() => data.value?.report)
const modern = computed(() => ['viewing-report/2', 'viewing-report/3', 'viewing-report/4'].includes(data.value?.report?.version || ''))
const roles = computed(() => [...new Set(current.value?.personnel.map(person => person.role) || [])].sort())
const candidates = computed(() => view.value === 'OVERALL' ? current.value?.overall || [] : current.value?.personnel || [])
const filtered = computed(() => candidates.value.filter(person => !role.value || (view.value === 'ROLE' ? person.role === role.value : person.roles?.includes(role.value))))
const rows = computed(() => filtered.value.slice(0, limit.value))
const coverage = computed(() => (data.value?.report?.coverage || []))
const coverageById = computed(() => new Map((data.value?.report?.coverage || []).map(work => [work.subjectId, work])))
const coverageGroups = computed(() => [
  { label: '制作资料', counts: current.value?.coverageSummary?.staff },
  { label: '配音资料', counts: current.value?.coverageSummary?.cast },
])
const statusLabels: Record<string, string> = { COMPLETE: '完整', PARTIAL: '部分', NOT_FETCHED: '未获取', NOT_VERIFIED: '未核验', FRESH: '6小时内', STALE: '已过期' }
watch(() => props.scope, () => { busy.value = false; actionError.value = ''; role.value = ''; limit.value = 20; view.value = 'OVERALL' })
watch(view, () => { role.value = ''; limit.value = 20 })
watch(role, () => { limit.value = 20 })
watch(() => data.value?.report?.version, value => { if (value && !['viewing-report/2', 'viewing-report/3', 'viewing-report/4'].includes(value)) { view.value = 'ROLE' } })
function sectionText(section: WorkCoverage['staff']) {
  return statusLabels[section.collectionStatus] + ' / ' + statusLabels[section.freshness]
}
function sourceText(work?: WorkCoverage) {
  return work?.source === 'NEO4J_LOCAL' ? '已保存资料' : '已保存公共名册'
}
function workLink(id: number) {
  return { path: '/explore/anime/' + id }
}
function personRoles(person: ReportPerson) { return view.value === 'OVERALL' ? person.roles?.join('、') : person.role }
async function run(cancel = false, supplement = true) {
  const owner = props.scope
  busy.value = true; actionError.value = ''
  try {
    await mutate(cancel ? '/viewer/report/jobs/current' : `/viewer/report/jobs?supplement=${supplement}`, cancel ? 'DELETE' : 'POST')
    if (owner === props.scope) await refresh()
  } catch (cause) { if (owner === props.scope) actionError.value = cause instanceof Error ? cause.message : '操作失败' }
  finally { if (owner === props.scope) busy.value = false }
}
function printReport() { window.print() }
</script>
<template>
  <section class="panel report-panel">
    <div class="report-heading">
      <div><h2>你的演职员观看报告</h2><p>看过谁参与的作品，以及这些作品的个人评分。</p></div>
      <div class="actions print-hide">
        <button :disabled="busy" @click="run(false, false)">生成已有资料报告</button>
        <button :disabled="busy" @click="run()">继续补充报告资料</button>
        <button v-if="data?.task && ['PENDING', 'RUNNING'].includes(data.task.status)" :disabled="busy" @click="run(true)">取消补充</button>
        <button v-if="data?.report" @click="printReport">打印 / 保存报告</button>
      </div>
    </div>
    <p v-if="error || actionError" class="feedback error" role="alert">{{ actionError || error }} <button @click="refresh">重试读取</button></p>
    <p v-if="pending && !data" role="status">正在读取报告…</p>
    <p v-if="data?.task" class="notice" role="status">{{ ({ PENDING: '等待生成', RUNNING: '正在补充资料', PARTIAL: '报告已生成，部分覆盖尚待补充或核验', COMPLETE: '报告已生成', FAILED: '生成失败', CANCELLED: '已取消补充' } as Record<string, string>)[data.task.status] }} · 本批补充 {{ data.task.processed }}/{{ data.task.total }} 部 {{ data.task.error }}</p>
    <p class="muted">偏好统计使用已看作品，行为分析使用全部观看状态；优先复用已保存制作、配音资料，缺失时补充公共名册。运行中的报告任务在服务重启后需重新发起，已保存报告保留。</p>
    <p v-if="!data?.report">生成后可查看人员总体与职务统计。资料不足时会保留覆盖说明。</p>
    <template v-if="data?.report">
      <ProfileBreakdown :report="data.report"/>
      <p v-if="!modern" class="notice">当前为旧版按职务报告；重新生成后可查看人员总体和分组覆盖。</p>
      <div class="report-filters print-hide">
        <label>统计视角<select v-model="view"><option value="OVERALL" :disabled="!modern">人员总体（跨职务去重）</option><option value="ROLE">按职务</option></select></label>
        <label>职务筛选<select v-model="role"><option value="">全部职务</option><option v-for="item in roles" :key="item">{{ item }}</option></select></label>
      </div>
      <p v-if="current" class="notice">全已看片单 {{ current.completed }} 部，个人已评分 {{ current.rated }} 部；取得资料 {{ current.metadataConsidered }} 部，两类名册均完整 {{ current.metadataCovered }} 部。{{ data.collectionTruncated ? '片单本身存在截断，这些分母仅代表已同步范围。' : '' }}生成于 {{ new Date(data.report.generatedAt).toLocaleString('zh-CN') }}。</p>
      <p class="muted">接触次数不直接代表喜爱；评分样本不足3部时不显示校正评分。以下完整性与新鲜度均为生成时状态，过期不会改写已采集完整性。</p>
      <p v-if="view === 'OVERALL' && role" class="muted">筛出担任过“{{ role }}”的人员，部数仍是该人员跨职务去重的总体作品数。按职务视角可核对该职务的作品数。</p>
      <div v-if="modern" class="coverage-summary">
        <div v-for="group in coverageGroups" :key="group.label">
          <strong>{{ group.label }}</strong>
          <p>完整 {{ group.counts?.collection.COMPLETE || 0 }} · 部分 {{ group.counts?.collection.PARTIAL || 0 }} · 未获取 {{ group.counts?.collection.NOT_FETCHED || 0 }} · 未核验 {{ group.counts?.collection.NOT_VERIFIED || 0 }} 部</p>
          <p class="muted">6小时内 {{ group.counts?.freshness.FRESH || 0 }} · 已过期 {{ group.counts?.freshness.STALE || 0 }} · 未核验 {{ group.counts?.freshness.NOT_VERIFIED || 0 }} 部</p>
        </div>
      </div>
      <p v-if="current?.completed === 0" class="notice">当前片单没有已看作品。</p>
      <p v-else-if="!rows.length" class="notice">当前范围或职务没有可统计人员，可调整筛选或补充资料。</p>
      <details v-for="person in rows" :key="person.uid + ':' + person.role" class="person-stat">
        <summary><strong>{{ person.name }}</strong><span>{{ personRoles(person) }} · {{ person.workCount }} 部</span><span>评分样本 {{ person.ratedCount }} · 均分 {{ person.averageRating ?? '未评分' }} · 校正 {{ person.adjustedRating ?? '样本不足' }}</span></summary>
        <ul><li v-for="work in person.works" :key="work.subjectId"><RouterLink :to="workLink(work.subjectId)">{{ work.title }}</RouterLink> · 个人评分 {{ work.personalRating || '未评分' }} · {{ sourceText(coverageById.get(work.subjectId)) }}</li></ul>
      </details>
      <button v-if="filtered.length > rows.length" class="print-hide" @click="limit += 20">再展开20项</button>
      <p v-if="modern" class="muted">当前视角已展示 {{ rows.length }}/{{ filtered.length }} 项；完整排名项数 {{ view === 'OVERALL' ? current?.overallTotal : current?.personnelTotal }}，每种视角最多返回500项。</p>
      <details v-if="modern" class="work-coverage"><summary>逐作品核对资料覆盖与版本（{{ coverage.length }}部）</summary>
        <ul><li v-for="work in coverage" :key="work.subjectId"><RouterLink :to="workLink(work.subjectId)">作品 {{ work.subjectId }}</RouterLink> · {{ sourceText(work) }}<br>制作：{{ sectionText(work.staff) }}（{{ work.staff.records }}条）；配音：{{ sectionText(work.cast) }}（{{ work.cast.records }}条）<br>来源获取：{{ work.staff.fetchedAt ? new Date(work.staff.fetchedAt).toLocaleString('zh-CN') : '制作未知' }} / {{ work.cast.fetchedAt ? new Date(work.cast.fetchedAt).toLocaleString('zh-CN') : '配音未知' }}<br><small>输入版本 {{ work.version || '无已获取资料' }}</small></li></ul>
      </details>
      <details><summary>统计口径与范围</summary><p v-for="line in data.report.methodology" :key="line">{{ line }}</p></details>
    </template>
  </section>
</template>
<style scoped>
.report-heading{display:flex;justify-content:space-between;align-items:start;gap:16px}.report-heading h2{margin:0}.report-heading p{font-size:13px;color:#70836c}.report-panel .actions{flex-wrap:wrap}.report-filters{display:flex;gap:16px;flex-wrap:wrap;margin:20px 0}.report-filters>label{min-width:180px;flex:1}.coverage-summary{display:grid;grid-template-columns:1fr 1fr;gap:16px;background:#f4f7f1;padding:16px;border-radius:8px;font-size:12px;line-height:1.8}.person-stat{padding:12px 0;border-bottom:1px solid #e1e8d9;font-size:12px}.person-stat summary{cursor:pointer;display:flex;flex-wrap:wrap;gap:10px;line-height:1.8;min-height:36px}.person-stat strong{font-size:14px;color:#40594f}.person-stat ul,.work-coverage ul{line-height:2}.work-coverage li{margin:12px 0}.work-coverage small,.report-panel>details small{overflow-wrap:anywhere}.report-panel>details:last-child,.work-coverage{margin-top:20px;font-size:12px;line-height:1.8}@media(max-width:800px){.report-heading{flex-direction:column}.coverage-summary{grid-template-columns:1fr}}@media print{.print-hide{display:none}.person-stat{break-inside:avoid}.report-panel{border:0}}
</style>
