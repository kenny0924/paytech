import { defaultPaging, pageSizeOptions } from '../api/paging.ts';
import type { PagingRequestBody } from '../api/paging.ts';

export type RiskView = 'list' | 'detail' | 'edit' | 'create' | 'history' | 'version' | 'references';
export interface RiskNavigation {
  view: RiskView;
  rootId?: string; versionId?: string; maxVersionNo?: number;
  historyPage?: number; historySize?: number; refPage?: number; refSize?: number;
  environmentCode?: string; releaseStatus?: string; refVersionId?: string;
  selectedRuleSetCode: string | null;
  filters: Readonly<Record<string, string>>;
  paging: PagingRequestBody;
}
const codePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
export function ruleSetCodeError(value: string): string {
  const code = value.trim();
  return !code || codePattern.test(code) ? '' : '编码须为 1–64 位大写字母、数字或下划线，并以字母开头。';
}
export function parseRiskNavigation(search: string): RiskNavigation {
  const query = new URLSearchParams(search);
  const one = (key: string) => { const values = query.getAll(key); return values.length === 1 ? values[0]! : ''; };
  const pointCode = one('pointCode') || 'CHECKOUT_PREPARE';
  const filters: Record<string, string> = { pointCode: codePattern.test(pointCode) ? pointCode : 'CHECKOUT_PREPARE' };
  const ruleSetCode = one('ruleSetCode').trim();
  if (ruleSetCode) filters.ruleSetCode = ruleSetCode;
  if (['DRAFT', 'FROZEN', 'RETIRED'].includes(one('status'))) filters.status = one('status');
  const page = Number(one('page')); const size = Number(one('size'));
  const paging = { page: Number.isInteger(page) && page >= 1 && page <= 100000 ? page : defaultPaging.page,
    size: pageSizeOptions.includes(size) ? size : defaultPaging.size };
  const selected = one('selectedRuleSetCode');
  const requested = one('view');
  let view: RiskView = !query.has('tab') && codePattern.test(selected)
    && ['detail', 'edit', 'create', 'history', 'version', 'references'].includes(requested) ? requested as RiskView : 'list';
  const extension: Partial<RiskNavigation> = {};
  if (['history','version','references'].includes(view)) {
    const id = (value:string) => /^[1-9]\d{0,18}$/.test(value) && BigInt(value)<=9223372036854775807n;
    const number = (key:string,fallback:number,max:number) => {const value=Number(one(key));return Number.isInteger(value)&&value>0&&value<=max?value:fallback;};
    if(!id(one('rootId')) || (view==='version'&&!id(one('versionId')))) view='list';
    else {
      extension.rootId=one('rootId'); if(id(one('versionId')))extension.versionId=one('versionId');
      extension.maxVersionNo=number('maxVersionNo',1,2147483647);
      extension.historyPage=number('historyPage',1,100000);extension.historySize=pageSizeOptions.includes(Number(one('historySize')))?Number(one('historySize')):10;
      extension.refPage=number('refPage',1,100000);extension.refSize=pageSizeOptions.includes(Number(one('refSize')))?Number(one('refSize')):10;
      if(['TEST','PRODUCTION'].includes(one('environmentCode'))) extension.environmentCode=one('environmentCode');
      if(['DRAFT','VALIDATING','SHADOWING','REVIEW_PENDING','APPROVED','ACTIVE','SUPERSEDED','REJECTED'].includes(one('releaseStatus'))) extension.releaseStatus=one('releaseStatus');
      if(id(one('refVersionId')))extension.refVersionId=one('refVersionId');
    }
  }
  return { ...extension, view, selectedRuleSetCode: view === 'list' ? null : selected, filters, paging };
}
export function withRiskView(current: RiskNavigation, view: RiskView, selectedRuleSetCode: string | null = null): RiskNavigation {
  if (view === 'list' || view === 'detail' || view === 'edit' || view === 'create') return { view, selectedRuleSetCode: view === 'list' ? null : selectedRuleSetCode, filters: current.filters, paging: current.paging };
  return { ...current, view, selectedRuleSetCode };
}
export function riskNavigationSearch(state: RiskNavigation): string {
  const query = new URLSearchParams(state.filters);
  query.set('page', String(state.paging.page)); query.set('size', String(state.paging.size));
  query.set('view', state.view);
  if (state.view !== 'list' && state.selectedRuleSetCode) query.set('selectedRuleSetCode', state.selectedRuleSetCode);
  for(const key of ['rootId','versionId','maxVersionNo','historyPage','historySize','refPage','refSize','environmentCode','releaseStatus','refVersionId'] as const) if(state[key]!==undefined) query.set(key,String(state[key]));
  return `?${query}`;
}
