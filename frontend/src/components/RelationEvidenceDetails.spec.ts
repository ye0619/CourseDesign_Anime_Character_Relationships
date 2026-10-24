import { describe, expect, it } from 'vitest'
import { h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import RelationEvidenceDetails from './RelationEvidenceDetails.vue'
import type { RelationEvidence } from '../types/evidence'
const evidence: RelationEvidence = { uid: 'evidence:fixture:one', provider: 'PROJECT', sourceUrl: 'https://example.org/source', sourceName: '核验来源', sourceRevision: 'r1', attribution: 'Fixture author', description: '已审核资料' }
describe('relation evidence details', () => {
  it('keeps old source records readable without invented missing provenance', async () => {
    const html = await renderToString(h(RelationEvidenceDetails, { evidence }))
    expect(html).toContain('https://example.org/source')
    expect(html).toContain('版本 r1')
    expect(html).not.toContain('<dt')
  })
  it('shows actual field, license and raw text as escaped text', async () => {
    const html = await renderToString(h(RelationEvidenceDetails, { evidence: { ...evidence, sourceField: 'infobox.members', rawRelation: '<script>source text</script>', licenseLabel: 'CC0-1.0' } }))
    expect(html).toContain('infobox.members')
    expect(html).toContain('CC0-1.0')
    expect(html).toContain('&lt;script&gt;source text&lt;/script&gt;')
    expect(html).not.toContain('<script>source text')
  })
})
