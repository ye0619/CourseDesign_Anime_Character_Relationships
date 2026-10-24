export const identityProviders = ['BANGUMI','MOEGIRL','ACDB','WIKIDATA','PROJECT'] as const
export const identityStatuses: Record<string,string> = { CANDIDATE:'待审核',REVIEWED:'已审核',REJECTED:'已驳回',REVOKED:'已撤回' }
export const identityActions: Record<string,string> = { NAMES:'保存已核验别名',CREATE:'登记候选',REVIEW:'审核通过',REJECT:'驳回候选',REVOKE:'撤回审核',REMAP:'改绑并重新待审',REOPEN:'凭新修订重开',AMEND:'补充候选资料' }
export interface IdentityCandidate {provider:string;providerId:string;entityType:string;canonicalUid:string;sourceUrl:string;sourceRevision:string;licenseLabel:string;evidenceText:string;reason:string;matchMethod?:'MANUAL'|'TITLE_ONLY'}
export interface ReviewedIdentity extends Omit<IdentityCandidate,'reason'|'matchMethod'> {id:number;status:string;version:number;matchMethod:string;reviewReason:string;reviewedBy:number|null;updatedAt:string}
export interface IdentityDecision {expectedVersion:number;decision:string;reason:string;targetUid?:string;newRevision?:string;newEvidence?:string;newSourceUrl?:string;newLicenseLabel?:string}
export interface IdentityEvent {id:number;action:string;actorId:number;reason:string;beforeState:string|null;afterState:string;createdAt:string}
export interface IdentityDetail {identity:ReviewedIdentity;history:IdentityEvent[]}
export function identityChange(event:IdentityEvent):string {
  try {const before=event.beforeState?JSON.parse(event.beforeState):null,after=JSON.parse(event.afterState);return `${before?`${before.canonicalUid}（${identityStatuses[before.status]||before.status}）`:'未登记'} → ${after.canonicalUid}（${identityStatuses[after.status]||after.status}）`} catch {return '历史快照无法解析，请核对操作记录'}
}
