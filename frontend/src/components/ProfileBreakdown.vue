<script setup lang="ts">
import { computed, shallowRef } from 'vue'
import CharacterPortrait from './CharacterPortrait.vue'
import ReportWorkList from './ReportWorkList.vue'
import type { ViewingReport } from '../types/viewingReport'
const props = defineProps<{ report: ViewingReport }>()
const voiceLimit = shallowRef(20), companyLimit = shallowRef(20), companySort = shallowRef('COUNT')
const voices = computed(() => props.report.voices?.slice(0, voiceLimit.value) || [])
const companies = computed(() => [...(props.report.companies || [])].sort((a,b) => companySort.value === 'RATING'
  ? (b.scores.ratedCount >= 3 ? b.scores.averageRating ?? -1 : -1) - (a.scores.ratedCount >= 3 ? a.scores.averageRating ?? -1 : -1) || b.completed-a.completed
  : b.completed-a.completed).slice(0, companyLimit.value).map(company => ({...company,
  ratingBins: Object.entries(company.scores.distribution).map(([score,count]) => ({score,count,works:company.works.filter(work=>work.status===2&&work.personalRating===Number(score))})),
  statusGroups: Object.entries(company.statuses).map(([status,count])=>({status,count,works:company.works.filter(work=>work.status===Number(status))}))
})))
const statusNames: Record<string, string> = { 1: '想看', 2: '看过', 3: '在看', 4: '搁置', 5: '抛弃' }
const portrait = (id: string, label: string, imageUrl: string) => ({ id, label: label || '未命名', imageUrl, group: '', role: '', description: '' })
</script>
<template>
  <div class="profile-breakdown">
    <section aria-label="声优配音角色"><h3>从声音，找到你看过的角色</h3><p class="muted">仅列出本作明确配音记录。作品按作品编号去重，角色按角色编号去重；同一角色跨作品的演出分别列出。</p>
      <p v-if="!report.voices">重新生成报告，可查看实际配音角色。</p><p v-else-if="!voices.length">当前没有可核对的配音角色资料。可继续补充公共名册。</p>
      <details v-for="voice in voices" :key="voice.uid" class="profile-row"><summary><CharacterPortrait :node="portrait(voice.uid, voice.name, voice.imageUrl)"/><strong>{{ voice.name }}</strong><span>{{ voice.workCount }} 部 · {{ voice.characterCount }} 个角色</span><span>个人均分 {{ voice.scores.averageRating ?? '未评分' }}（{{ voice.scores.ratedCount }} 部）</span></summary>
        <div class="role-grid"><article v-for="role in voice.roles" :key="role.subjectId + ':' + role.characterUid" class="voice-role"><CharacterPortrait :node="portrait(role.characterUid, role.characterName, role.imageUrl)" large/><div><RouterLink :to="{path: `/explore/anime/${role.subjectId}/network`, query: {character: role.characterUid}}">{{ role.characterName || '未命名角色' }}</RouterLink><RouterLink :to="`/explore/anime/${role.subjectId}`">{{ role.title }}</RouterLink><small>个人评分 {{ role.personalRating || '未评分' }}</small></div></article></div>
      </details><button v-if="(report.voices?.length || 0) > voiceLimit" @click="voiceLimit += 20">更多声优</button>
    </section>
    <section aria-label="动画制作公司偏好"><h3>动画制作公司</h3><p class="muted">仅纳入 Bangumi 明确为机构且职责为“动画制作”的记录。联合制作各计一部，同机构同作品只计一次；均分及代表作仅基于看过作品，未评分不计入。缺失资料不推断公司。</p>
      <label>排行<select v-model="companySort"><option value="COUNT">最常观看</option><option value="RATING">评分较高（至少3部已评分）</option></select></label>
      <p v-if="!companies.length">尚无有效动画制作机构样本，请补充资料后重新生成。</p>
      <details v-for="company in companies" :key="company.uid" class="profile-row"><summary><CharacterPortrait :node="portrait(company.uid,company.name,company.imageUrl)"/><strong>{{ company.name }}</strong><span>看过 {{ company.completed }} 部 / 相关 {{ company.allCount }} 部</span><span>均分 {{ company.scores.averageRating ?? '未评分' }} · 评分样本 {{ company.scores.ratedCount }} {{ company.scores.ratedCount < 3 ? '（样本不足）' : '' }}</span></summary>
        <div class="score-bars" aria-label="个人评分分布"><details v-for="bin in company.ratingBins" :key="bin.score" class="score-bin"><summary :title="`${bin.score}分 ${bin.count}部，展开查看作品`"><i :style="{height: `${Math.max(2, bin.count / Math.max(1,company.scores.ratedCount) * 70)}px`}"></i>{{ bin.score }}分<small>{{ bin.count }}部</small></summary><ReportWorkList :works="bin.works"/></details></div>
        <div class="status-groups"><details v-for="group in company.statusGroups" :key="group.status"><summary>{{ statusNames[group.status] }} {{ group.count }}</summary><ReportWorkList :works="group.works"/></details></div>
        <h4>高分代表作</h4><ReportWorkList :works="company.representatives"/><details><summary>核对全部相关作品</summary><ReportWorkList :works="company.works"/></details>
      </details><button v-if="(report.companies?.length || 0) > companyLimit" @click="companyLimit += 20">更多制作公司</button>
    </section>
    <section aria-label="观看行为分析"><h3>观看状态与中途停留</h3><p class="muted">搁置、抛弃是观看行为线索，不能据此认定讨厌某种题材。标签使用现有社区标签，形式由现有标签识别；分别标明资料覆盖。</p>
      <p v-if="!report.behaviors">重新生成报告，可查看搁置和抛弃分析。</p>
      <details v-for="behavior in report.behaviors" :key="behavior.status" class="profile-row" :open="behavior.status >= 4"><summary><strong>{{ behavior.label }}</strong><span>{{ behavior.count }} 部 · {{ behavior.share }}%</span><span>个人均分 {{ behavior.scores.averageRating ?? '未评分' }}（{{ behavior.scores.ratedCount }} 部）</span></summary>
        <p class="muted">标签覆盖 {{ behavior.tagged }}/{{ behavior.count }} 部；制作公司覆盖 {{ behavior.companyCovered }}/{{ behavior.count }} 部。{{ behavior.count < 5 ? '样本较少，仅展示记录。' : '' }}</p>
        <div class="behavior-groups"><section v-for="group in [{name:'类型 / 形式',items:behavior.formats},{name:'标签',items:behavior.tags},{name:'动画制作公司',items:behavior.companies}]" :key="group.name"><h4>{{ group.name }}</h4><details v-for="item in group.items" :key="item.id"><summary>{{ item.label }} · {{ item.count }} 部</summary><ReportWorkList :works="item.works"/></details><p v-if="!group.items.length" class="muted">暂无资料</p></section></div>
        <details><summary>查看全部{{ behavior.label }}作品</summary><ReportWorkList :works="behavior.works"/></details>
      </details>
    </section>
  </div>
</template>
<style scoped>
.profile-breakdown>section{margin:28px 0;padding-bottom:18px;border-bottom:1px solid #dce5df}.profile-breakdown h3{font-size:19px;margin-bottom:10px;color:var(--text,#40594f)}.muted{font-size:12px;line-height:1.8}.profile-row{border-bottom:1px solid #dce5df;padding:12px 0}.profile-row>summary{display:flex;align-items:center;gap:12px;flex-wrap:wrap;cursor:pointer;font-size:12px;min-height:44px}.profile-row>summary strong{font-size:14px}.role-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(215px,1fr));gap:18px;padding:20px 0}.voice-role{display:flex;align-items:center;gap:14px}.voice-role a,.voice-role small{display:block;font-size:12px;margin:6px 0}.voice-role a:first-child{font-weight:600}.behavior-groups{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:20px;font-size:12px}.behavior-groups summary{cursor:pointer;padding:7px 0}.score-bars{display:flex;gap:10px;align-items:end;padding:18px 0;overflow:auto}.score-bin>summary{cursor:pointer;display:grid;gap:5px;font-size:11px;min-width:27px;text-align:center}.score-bin i{background:#5a8879;width:20px;justify-self:center;border-radius:3px 3px 0 0}.status-groups{display:flex;gap:18px;flex-wrap:wrap;font-size:12px}.status-groups summary{cursor:pointer}.score-bin[open]{min-width:200px}label{font-size:12px;max-width:300px}@media(max-width:700px){.behavior-groups{grid-template-columns:1fr}}
</style>
