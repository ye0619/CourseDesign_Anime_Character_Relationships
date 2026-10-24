<script setup lang="ts">
import { computed, shallowRef, toRef, watch } from 'vue'
import type { CreditKind } from '../types/credits'
import { useSubjectCredits } from '../composables/useSubjectCredits'
import { creditLanguage, creditNode } from '../utils/subjectCredits'
import { matchesEntityName } from '../utils/entityNames'
import CharacterPortrait from './CharacterPortrait.vue'
import EntityNames from './EntityNames.vue'
const props = withDefaults(defineProps<{ subjectId: string; kind?: CreditKind; source?: 'LOCAL' | 'ONLINE'; uid?: string }>(), { source: 'ONLINE' })
const selectedRole = shallowRef('')
const positions = computed(() => [...new Set(merged.value.people.flatMap(item => item.positions.map(position => position.label)))].sort())
const selectedKind = shallowRef<CreditKind>('CAST'), query = shallowRef(''), visibleCount = shallowRef(60)
const kind = computed(() => props.kind || selectedKind.value)
const { section, merged, loadMore, retry, refresh } = useSubjectCredits(toRef(props, 'subjectId'), kind, toRef(props, 'source'), computed(() => props.uid || `anime:bangumi:${props.subjectId}`))
const filtered = computed(() => merged.value.people.filter(item => (!selectedRole.value || item.positions.some(position => position.label === selectedRole.value)) && matchesEntityName(item.node, query.value,
  [...item.cast.flatMap(credit => { const character = merged.value.characters.get(credit.characterUid); return character ? [character.label, character.originalName || '', ...character.aliases] : [] }), ...item.positions.map(position => position.label)])))
const visible = computed(() => filtered.value.slice(0, visibleCount.value))
const nameNotice=computed(()=>[...new Set(section.value.pages.flatMap(page=>page.nameNotice?[page.nameNotice]:[]))].join(' '))
const source = computed(() => section.value.pages.at(-1)?.source)
const gaps = computed(() => section.value.pages.reduce((count, page) => count + page.coverage.omittedRows + page.coverage.omittedCredits + page.coverage.unknownCastRows, 0))
watch([kind, () => props.subjectId], () => { selectedRole.value = '' })
watch([kind, () => props.subjectId, query, selectedRole], () => { visibleCount.value = 60 })
const personType = (type: number | null) => type === 1 ? '自然人' : type === 2 ? '公司 / 机构' : type === 3 ? '组合' : '来源未提供人员类型'
</script>
<template>
  <div class="credits-panel"><p v-if="nameNotice" class="feedback" role="status">{{ nameNotice }}</p>
    <div class="credits-toolbar"><div v-if="!props.kind" role="group" aria-label="名册类别"><button :aria-pressed="kind === 'CAST'" @click="selectedKind = 'CAST'">配音及出演记录</button><button :aria-pressed="kind === 'STAFF'" @click="selectedKind = 'STAFF'">制作名单</button></div><label v-if="kind === 'STAFF'">按职务查看<select v-model="selectedRole"><option value="">全部职务</option><option v-for="position in positions" :key="position">{{ position }}</option></select></label><label>搜索已加载名册<input v-model="query" type="search" placeholder="人员、别名、角色或职务"></label></div>
    <p class="scope">{{ kind === 'CAST' ? '按人员归集其饰演角色；原声、译配与真人出演保留来源分类。' : '按表格列出本作制作人员、职务与集数，机构与组合保留来源类型。' }}</p>
    <p v-if="section.loading" role="status">正在读取{{ kind === 'CAST' ? '配音及出演' : '制作' }}名册…</p>
    <p v-if="section.error" class="warning" role="alert">{{ section.error }} <button :disabled="section.loading" @click="retry">重试未完成页</button></p>
    <template v-if="section.pages.length">
      <div class="coverage" role="status"><strong>{{ merged.complete ? '来源名册已加载完成' : '当前为部分资料' }}</strong><span>已读取 {{ merged.loadedRows }} / {{ merged.total }} {{ kind === 'CAST' ? '条角色名册' : '条制作名册' }}</span><span>已加载范围：{{ merged.people.length }} 个人员 / 机构实体<template v-if="kind === 'CAST'">，其中来源明确为自然人的配音人员 {{ merged.voicePeople }} 位</template></span></div>
      <p v-if="gaps || merged.stale || section.pages.some(page => page.coverage.capped)" class="warning"><template v-if="gaps">来源存在 {{ gaps }} 处缺失或无法解析的记录，不能据此认定没有相关人员。 </template><template v-if="merged.stale">部分来源暂不可用，当前包含此前缓存。 </template><template v-if="section.pages.some(page => page.coverage.capped)">已到本次100页上限，资料尚不完整。</template></p>
      <p v-if="!filtered.length">{{ query ? '已加载范围内没有匹配项。' : '本页来源未提供可展示的人员记录。' }}</p>
      <div v-if="kind === 'STAFF' && visible.length" class="table-scroll"><table><caption>本作制作人员与职务</caption><thead><tr><th scope="col">人员 / 机构</th><th scope="col">来源类型</th><th scope="col">制作职务与集数</th><th scope="col">名称与来源</th></tr></thead><tbody><tr v-for="item in visible" :key="item.person.id"><td><CharacterPortrait :node="item.node"/><a :href="item.person.sourceUrl" target="_blank" rel="noopener noreferrer">{{ item.person.label }}</a></td><td>{{ personType(item.person.providerType) }}</td><td><ul class="positions"><li v-for="(position,index) in item.positions" :key="index"><strong>{{ position.label }}</strong> · {{ position.episodeScope || '集数未知' }}<p v-if="position.description">{{ position.description }}</p></li><li v-if="!item.positions.length">职务未知</li></ul></td><td><EntityNames :node="item.node"/></td></tr></tbody></table></div>
      <template v-else><article v-for="item in visible" :key="item.person.id" class="credit-person">
        <div class="person-heading"><CharacterPortrait :node="item.node"/><div><a :href="item.person.sourceUrl" target="_blank" rel="noopener noreferrer">{{ item.person.label }}</a><small>{{ personType(item.person.providerType) }}</small></div></div>
        <ul v-if="kind === 'CAST'" class="cast-list"><li v-for="credit in item.cast" :key="credit.id">
          <strong>{{ merged.characters.get(credit.characterUid)?.label || credit.characterUid }}</strong><span class="badge">{{ credit.sourceRelationLabel }}</span>
          <dl><dt>语种</dt><dd>{{ creditLanguage(credit.language) }}<small v-if="credit.languageSource === 'relation'">（来源配音分类）</small></dd><dt>版本</dt><dd>{{ credit.version || '来源未提供' }}</dd><dt>集数</dt><dd>{{ credit.episodeScope || '来源未提供' }}</dd></dl>
          <p v-if="credit.description" class="note">{{ credit.description }}</p>
          <details><summary>饰演证据与角色名称</summary><small>依据本作品角色名册中的演员—角色关联，来源分类：{{ credit.sourceRelationLabel }}。</small><EntityNames v-if="merged.characters.get(credit.characterUid)" :node="creditNode(merged.characters.get(credit.characterUid)!)"/></details>
        </li></ul>
        <ul v-else class="positions"><li v-for="(position, index) in item.positions" :key="index"><strong>{{ position.label }}</strong> · 集数 {{ position.episodeScope || '来源未提供' }}<p v-if="position.description">{{ position.description }}</p></li><li v-if="!item.positions.length">来源未提供可解析的职务。</li></ul>
        <EntityNames :node="item.node"/>
      </article></template>
      <div class="actions"><button v-if="visibleCount < filtered.length" @click="visibleCount += 60">展开已加载人员（还有 {{ filtered.length - visibleCount }} 个）</button><button v-if="merged.nextPage" :disabled="section.loading" @click="loadMore">读取下一页来源资料</button><button :disabled="section.loading" @click="refresh">从第一页重新读取</button></div>
    </template>
    <footer v-if="source"><a v-if="source.sourceUrl" :href="source.sourceUrl" target="_blank" rel="noopener noreferrer">核对本作来源</a><span>{{ source.provider === 'NEO4J' ? 'Neo4j 本地档案' : source.stale ? '包含过期缓存' : source.cached ? '短时缓存' : '在线读取' }}<template v-if="source.fetchedAt"> · {{ source.provider === 'NEO4J' ? '当前页最早记录更新于' : '当前页读取于' }} {{ new Date(source.fetchedAt).toLocaleString('zh-CN') }}</template></span><small>未提供的人员类型、语种、版本和集数保持未知；“CV 原声”不推断为日语。覆盖仅针对当前来源已读取记录，不保证已收录全部演职员。跨页结果并非来源的原子快照。</small></footer>
  </div>
</template>
<style scoped>
.credits-panel{padding:0 24px 24px;color:#557568;min-width:0;font-size:13px;line-height:1.8}.credits-toolbar{display:flex;justify-content:space-between;align-items:end;flex-wrap:wrap;gap:16px}.credits-toolbar button{min-height:40px;font-size:12px}.credits-toolbar button[aria-pressed=true]{background:#386e5e;color:white}.credits-toolbar label{font-size:11px;color:#718779}.credits-toolbar input{min-width:220px;font-size:12px}.scope{padding:14px;background:#f3f7f4;font-size:12px}.coverage{display:flex;flex-wrap:wrap;gap:6px 20px;padding:14px 0;font-size:12px}.coverage strong{color:#365f53}.warning{background:#fcf5e8;color:#896d3d;padding:12px}.credit-person{padding:20px 0;border-top:1px solid #e2ebe5;overflow-wrap:anywhere}.person-heading{display:flex;align-items:center;gap:12px}.person-heading a{font-size:16px;color:#356c5b;font-weight:600}.person-heading small{display:block;font-size:10px;color:#819488}.cast-list,.positions{list-style:none;margin:14px 0 0;padding:0}.cast-list{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,280px),1fr));gap:12px}.cast-list>li{padding:14px;background:#f7f9f5;border:1px solid #e3ebe3;border-radius:10px}.cast-list strong{color:#3b6656}.badge{display:inline-block;margin-left:9px;font-size:10px;background:#e5ede3;color:#5b7a65;padding:1px 6px;border-radius:4px}dl{display:grid;grid-template-columns:35px minmax(0,1fr);gap:3px 8px;font-size:11px;margin:10px 0}dt{color:#8a9a8d}dd{margin:0}.note{white-space:pre-line;font-size:11px}details{font-size:11px;color:#7b8e80}summary{cursor:pointer}.positions li{padding:7px 0}.positions p{font-size:12px}.actions{display:flex;flex-wrap:wrap;gap:12px;margin:20px 0}button{font-size:12px}.credits-panel footer{display:flex;flex-wrap:wrap;gap:8px 20px;padding-top:16px;border-top:1px solid #e2ebe5;font-size:11px;color:#7c9081}footer small{flex-basis:100%;font-size:11px}footer a{color:#427a6b}@media(max-width:520px){.credits-panel{padding:0 18px 18px}.credits-toolbar{display:block}.credits-toolbar label{display:block;margin-top:12px}.credits-toolbar input{min-width:0;width:100%}.coverage{display:grid;gap:3px}.person-heading small{overflow-wrap:anywhere}}
</style>
