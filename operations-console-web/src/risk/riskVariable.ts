import { session } from '../api/session.ts';
import type { RequestClient } from '../api/session.ts';
import type { ValueType, RuleOperand, OperatorCode } from './riskApiModels.ts';
import { enumLabel, translator } from '../i18n/index.ts';

const t = translator('risk');

export type VariableSource = 'INPUT' | 'CONSTANT' | 'COLLECTION' | 'NODE_OUTPUT' | 'VELOCITY' | 'LIST';
export type VariableUsage = 'RULE_CONDITION' | 'FLOW_CONDITION' | 'ACTION_PARAMETER';
export type VariableStatus = 'ACTIVE' | 'DISABLED' | 'DELETED';
export type NumericUnit = 'SCALAR' | 'MONEY';
export interface VariableMember { value: string | boolean; displayName: string | null }
export interface VariableSummary {
  variableCode: string; variableName: string; sourceType: VariableSource; valueType: ValueType;
  pointCode: string | null; usages: VariableUsage[]; systemManaged: boolean; status: VariableStatus; version: string;
  numericUnit: NumericUnit | null; currencyCode: string | null; currencyVariableCode: string | null;
  formatCode: string; allowedValuesVariableCode: string | null; currentValue: string | boolean | null;
  memberCount: number | null; previewMembers: VariableMember[]; updateTime: string;
}
export interface VariableDetail extends VariableSummary {
  description: string | null; sourceContractCode: string | null; sourceContractVersion: number | null;
  sourceField: string | null; createBy: string; updateBy: string; createTime: string; valuesAsOf: string;
  allowedValues: { variableCode: string; variableName: string; version: string; memberCount: number; previewMembers: VariableMember[] } | null;
}
export interface VariablePage { items: VariableSummary[]; page?: number; size?: number; total?: number; missingCodes?: string[]; valuesAsOf: string }
export interface VariableMembers { variableCode: string; version: string; items: (VariableMember & { sortOrder: number })[]; page: number; size: number; total: number; valuesAsOf: string }
export interface VariableIssue { variableCode: string; code: string; message: string }
export interface VariableProjection { currentVariables?: readonly VariableSummary[]; valuesAsOf?: string | null; variableIssues?: readonly VariableIssue[] }
export interface VariableQueryContext { pointCode: string; inputContractCode: string; inputContractVersion: number; usage: VariableUsage }
export interface VariableReference { consumerType: 'RULE_SET' | 'DECISION_FLOW' | 'VARIABLE'; rootId: string; versionId: string | null; versionNo: number | null; code: string; name: string; scope: string; location: string; variableCode: string }
export interface VariableReferences { items: VariableReference[]; nextCursor: string | null; hasMore: boolean; scannedRows: number }
export interface VariableField { fieldKey: string; valueType: ValueType; required: boolean; dataClassification: string; allowedUsages: VariableUsage[]; numericUnit: NumericUnit | null; currencyFieldKey: string | null; semanticRole: string | null }
export interface VariableContract { contractCode: string; contractVersion: number; pointCode: string | null; sourceType: VariableSource; nodeType: string | null; fields: VariableField[] }
export interface VariableContext { supportedPoints: { pointCode: string; pointName: string }[]; sources: { sourceType: VariableSource; createAllowed: boolean }[]; valueTypes: ValueType[]; formatProfiles: { formatCode: string; label: string; help: string }[]; numericUnits: NumericUnit[]; limits: { memberCount: number; memberBodyBytes: number; literalSetCount: number }; contracts: VariableContract[]; featureCatalogs: FeatureCatalog[] }
/** 风控点的实时累计与名单可选目录，来自 Risk 工程登记。 */
export interface FeatureCatalog { pointCode: string; currencyVariableCode: string; velocityEvents: { eventCode: string; eventName: string; includeCurrentAllowed: boolean }[]; velocityAnchors: { subjectType: string; metrics: { metric: string; windowSeconds: number[]; includeCurrentAllowed: boolean }[] }[]; distinctSubjects: string[]; listSubjects: string[] }
export interface CreateVariable { variableCode: string; variableName: string; description: string | null; sourceType: VariableSource; valueType: ValueType; pointCode: string | null; sourceContractCode: string | null; sourceContractVersion: number | null; sourceField: string | null; usages: VariableUsage[]; formatCode: string; numericUnit: NumericUnit | null; currencyCode: string | null; currencyVariableCode: string | null; allowedValuesVariableCode: string | null; currentValue: string | boolean | null; status: 'ACTIVE' | 'DISABLED' ; velocity?: VelocityParameters | null; list?: ListParameters | null }
export interface UpdateVariable { expectedVersion: string; variableName: string; description: string | null; currentValue: string | boolean | null; status: 'ACTIVE' | 'DISABLED' }
export interface VariableOperand extends RuleOperand { sourceType: 'VARIABLE' | 'LITERAL'; variableCode: string | null; numericUnit: NumericUnit | null; currencyCode: string | null; currencyVariableCode: string | null }
export type VariableFilters = Partial<Record<'keyword' | 'sourceType' | 'valueType' | 'status' | 'pointCode' | 'usage' | 'nodeType' | 'codes' | 'page' | 'size', string | number>>;
/** 来源、主体、统计方式、窗口、值类型、用途与比较方式的显示名是 `risk.enum.<类型>.<值>`，未知值显示原代码。 */
export function variableSourceLabel(sourceType: string): string { return enumLabel(t, 'enum.variableSource', sourceType); }
/** 实时累计参数，创建后不可修改；distinctSubjectType 只用于 DISTINCT_COUNT。 */
export interface VelocityParameters { eventCode: string; anchorSubjectType: string; metric: string; distinctSubjectType: string | null; windowSeconds: number; includeCurrent: boolean }
/** 名单命中参数，创建后不可修改；主体类型随名单。 */
export interface ListParameters { listCode: string; subjectType: string }
export const riskSubjects = ['CLIENT', 'OPERATING_ENTITY', 'CONSUMER', 'IP_ADDRESS', 'CARD_FINGERPRINT', 'CARD_BIN'] as const;
export const velocityMetrics = ['COUNT', 'SUM_AMOUNT', 'DISTINCT_COUNT', 'LAST_AMOUNT', 'SECONDS_SINCE_LAST'] as const;
export const velocityWindows = [600, 3600, 86400, 604800] as const;
export function velocityMetricLabel(metric: string): string { return enumLabel(t, 'enum.velocityMetric', metric); }
/** 已知窗口有固定写法（10 分钟、1 小时…），其他窗口按秒显示。 */
export function velocityWindowLabel(seconds: number): string {
  const key = 'enum.velocityWindow.' + seconds;
  return t.has(key) ? t(key) : t('variable.windowSeconds', { seconds });
}
/** 金额合计、最近一次金额为金额量纲，其余为普通数值。 */
export function velocityIsMoney(metric: string): boolean { return metric === 'SUM_AMOUNT' || metric === 'LAST_AMOUNT'; }
/** 风控点目录；没有目录的风控点不能创建实时累计与名单命中变量。 */
export function featureCatalog(context: Pick<VariableContext, 'featureCatalogs'>, pointCode: string | null | undefined): FeatureCatalog | null { return context.featureCatalogs.find(c => c.pointCode === pointCode) ?? null; }
/** 锚定主体可选的统计方式。 */
export function velocityMetricOptions(catalog: FeatureCatalog, anchor: string) { return catalog.velocityAnchors.find(a => a.subjectType === anchor)?.metrics ?? []; }
/** 当前主体与统计方式可选的窗口。 */
export function velocityWindowOptions(catalog: FeatureCatalog, p: Pick<VelocityParameters, 'anchorSubjectType' | 'metric'>): number[] { return velocityMetricOptions(catalog, p.anchorSubjectType).find(m => m.metric === p.metric)?.windowSeconds ?? []; }
/** 可计入本次：事件每次评估都写入，且统计方式为次数或金额合计。 */
export function velocityAllowsCurrent(catalog: FeatureCatalog, p: Pick<VelocityParameters, 'eventCode' | 'anchorSubjectType' | 'metric'>): boolean {
  return !!catalog.velocityEvents.find(e => e.eventCode === p.eventCode)?.includeCurrentAllowed && !!velocityMetricOptions(catalog, p.anchorSubjectType).find(m => m.metric === p.metric)?.includeCurrentAllowed;
}
/** 风控点默认参数：首个事件、首个主体及其首个统计方式与窗口。 */
export function defaultVelocity(catalog: FeatureCatalog): VelocityParameters {
  return normalizeVelocity(catalog, { eventCode: catalog.velocityEvents[0]?.eventCode ?? '', anchorSubjectType: catalog.velocityAnchors[0]?.subjectType ?? '', metric: '', distinctSubjectType: null, windowSeconds: 0, includeCurrent: false });
}
/** 修正互相依赖的参数：事件、主体、统计方式不在目录内时改为首个允许值，窗口按 fallbackWindow 回退；非不同主体数清空去重主体；不可计入本次时取消勾选。 */
export function normalizeVelocity(catalog: FeatureCatalog, p: VelocityParameters): VelocityParameters {
  const eventCode = catalog.velocityEvents.some(e => e.eventCode === p.eventCode) ? p.eventCode : catalog.velocityEvents[0]?.eventCode ?? '';
  const anchorSubjectType = catalog.velocityAnchors.some(a => a.subjectType === p.anchorSubjectType) ? p.anchorSubjectType : catalog.velocityAnchors[0]?.subjectType ?? '';
  const metrics = velocityMetricOptions(catalog, anchorSubjectType), metric = metrics.some(m => m.metric === p.metric) ? p.metric : metrics[0]?.metric ?? '';
  const windows = velocityWindowOptions(catalog, { anchorSubjectType, metric }), windowSeconds = windows.includes(p.windowSeconds) ? p.windowSeconds : fallbackWindow(windows, p.windowSeconds);
  const distinct = metric === 'DISTINCT_COUNT' && p.distinctSubjectType !== anchorSubjectType && catalog.distinctSubjects.includes(p.distinctSubjectType ?? '') ? p.distinctSubjectType : null;
  const next = { eventCode, anchorSubjectType, metric, distinctSubjectType: distinct, windowSeconds, includeCurrent: p.includeCurrent };
  return { ...next, includeCurrent: velocityAllowsCurrent(catalog, next) && p.includeCurrent };
}
/** 原窗口不再允许时取不超过它的最大允许窗口，没有则取最小允许窗口。 */
function fallbackWindow(windows: readonly number[], current: number): number {
  const shorter = windows.filter(w => w <= current);
  return shorter.length ? shorter[shorter.length - 1] : windows[0] ?? 0;
}
/** 页面校验：缺少参数时返回原因，Risk 仍做最终校验。 */
export function velocityProblem(p: VelocityParameters): string | null {
  if (!p.eventCode || !p.anchorSubjectType || !p.metric || !p.windowSeconds) return t('variable.velocity.incomplete');
  if (p.metric === 'DISTINCT_COUNT' && !p.distinctSubjectType) return t('variable.velocity.distinctSubjectRequired');
  return null;
}
/** 口径预览，例如“同一卡指纹 10 分钟内的支付尝试评估次数（不含本次）”。 */
export function velocityPreview(catalog: FeatureCatalog, p: VelocityParameters): string {
  const metric = p.metric === 'DISTINCT_COUNT' ? t('variable.velocity.distinctMetric', { subject: subjectLabel(p.distinctSubjectType ?? '') }) : velocityMetricLabel(p.metric);
  const current = velocityAllowsCurrent(catalog, p) ? (p.includeCurrent ? 'include' : 'exclude') : 'none';
  const event = catalog.velocityEvents.find(e => e.eventCode === p.eventCode)?.eventName ?? p.eventCode;
  return t('variable.velocity.preview', { anchor: subjectLabel(p.anchorSubjectType), window: velocityWindowLabel(p.windowSeconds), event, metric, current });
}
/** 实时累计变量的派生结构字段：数值、格式 NONE、金额类绑定本点币种变量。 */
export function velocityDerived(catalog: FeatureCatalog, p: VelocityParameters): Pick<CreateVariable, 'sourceType' | 'valueType' | 'pointCode' | 'formatCode' | 'numericUnit' | 'currencyCode' | 'currencyVariableCode' | 'allowedValuesVariableCode' | 'currentValue' | 'sourceContractCode' | 'sourceContractVersion' | 'sourceField'> {
  const money = velocityIsMoney(p.metric);
  return { sourceType: 'VELOCITY', valueType: 'NUMBER', pointCode: catalog.pointCode, formatCode: 'NONE', numericUnit: money ? 'MONEY' : 'SCALAR', currencyCode: null, currencyVariableCode: money ? catalog.currencyVariableCode : null, allowedValuesVariableCode: null, currentValue: null, sourceContractCode: null, sourceContractVersion: null, sourceField: null };
}
/** 名单命中变量的派生结构字段：布尔、格式 NONE；风控点未选时为空。 */
export function listDerived(pointCode: string | null): Pick<CreateVariable, 'sourceType' | 'valueType' | 'pointCode' | 'formatCode' | 'numericUnit' | 'currencyCode' | 'currencyVariableCode' | 'allowedValuesVariableCode' | 'currentValue' | 'sourceContractCode' | 'sourceContractVersion' | 'sourceField'> {
  return { sourceType: 'LIST', valueType: 'BOOLEAN', pointCode, formatCode: 'NONE', numericUnit: null, currencyCode: null, currencyVariableCode: null, allowedValuesVariableCode: null, currentValue: null, sourceContractCode: null, sourceContractVersion: null, sourceField: null };
}
export function subjectLabel(value: string): string { return enumLabel(t, 'enum.subject', value); }
export const valueTypes: ValueType[] = ['STRING', 'NUMBER', 'BOOLEAN', 'DATETIME', 'DURATION'];
export function variableTypeLabel(valueType: string): string { return enumLabel(t, 'enum.valueType', valueType); }
export const variableUsages: VariableUsage[] = ['RULE_CONDITION', 'FLOW_CONDITION', 'ACTION_PARAMETER'];
export function usageLabel(usage: string): string { return enumLabel(t, 'enum.variableUsage', usage); }
export const operatorCodes: OperatorCode[] = ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'IN', 'NOT_IN', 'BETWEEN', 'IS_NULL', 'IS_NOT_NULL'];
export function operatorLabel(operator: string): string { return enumLabel(t, 'enum.operator', operator); }

function check(ok: unknown): asserts ok { if (!ok) throw new Error(t('variable.error.invalidResponse')); }
function record(v: unknown, keys: string): Record<string, unknown> { check(v !== null && typeof v === 'object' && !Array.isArray(v)); check(Object.keys(v).sort().join(' ') === keys.split(' ').sort().join(' ')); return v as Record<string, unknown>; }
function text(v: unknown): string { check(typeof v === 'string'); return v; }
function number(v: unknown): number { check(typeof v === 'number' && Number.isSafeInteger(v) && v >= 0); return v; }
function bool(v: unknown): boolean { check(typeof v === 'boolean'); return v; }
function scalar(v: unknown): string | boolean { check(typeof v === 'string' || typeof v === 'boolean'); return v; }
function array<T>(v: unknown, decode: (v: unknown) => T, max = 1000): T[] { check(Array.isArray(v) && v.length <= max); return v.map(decode); }
function nil<T>(v: unknown, decode: (v: unknown) => T): T | null { return v === null ? null : decode(v); }
function one<T extends string>(v: unknown, values: readonly T[]): T { check(typeof v === 'string' && values.includes(v as T)); return v as T; }
const sources = ['INPUT','CONSTANT','COLLECTION','NODE_OUTPUT','VELOCITY','LIST'] as const, types = ['STRING','NUMBER','BOOLEAN','DATETIME','DURATION'] as const, usages = ['RULE_CONDITION','FLOW_CONDITION','ACTION_PARAMETER'] as const;
function code(v: unknown): string { const s=text(v); check(/^[A-Z][A-Z0-9_]{0,63}$/.test(s)); return s; }
function id(v: unknown): string { const s=text(v); check(/^[1-9][0-9]{0,18}$/.test(s) && BigInt(s) <= 9223372036854775807n); return s; }
function validUtcTime(s: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}Z$/.test(s) || s < '1000-') return false;
  const milliseconds = Date.parse(s);
  return Number.isFinite(milliseconds) && new Date(milliseconds).toISOString() === s.slice(0, 23) + 'Z';
}
function time(v: unknown): string { const s=text(v); check(validUtcTime(s)); return s; }
const summaryKeys = 'variableCode variableName sourceType valueType pointCode usages systemManaged status version numericUnit currencyCode currencyVariableCode formatCode allowedValuesVariableCode currentValue memberCount previewMembers updateTime';
export function decodeVariableSummary(value: unknown): VariableSummary { return summary(record(value, summaryKeys)); }
function member(v: unknown): VariableMember { const r=record(v,'value displayName'); return {value:scalar(r.value),displayName:nil(r.displayName,text)}; }
function summary(r: Record<string,unknown>): VariableSummary {
  return { variableCode:code(r.variableCode), variableName:text(r.variableName), sourceType:one(r.sourceType,sources), valueType:one(r.valueType,types), pointCode:nil(r.pointCode,code), usages:array(r.usages,v=>one(v,usages),3), systemManaged:bool(r.systemManaged), status:one(r.status,['ACTIVE','DISABLED','DELETED']), version:id(r.version), numericUnit:nil(r.numericUnit,v=>one(v,['SCALAR','MONEY'])), currencyCode:nil(r.currencyCode,text), currencyVariableCode:nil(r.currencyVariableCode,code), formatCode:code(r.formatCode), allowedValuesVariableCode:nil(r.allowedValuesVariableCode,code), currentValue:nil(r.currentValue,scalar), memberCount:nil(r.memberCount,number), previewMembers:array(r.previewMembers,member,100), updateTime:time(r.updateTime) };
}
export function decodeVariableDetail(v: unknown): VariableDetail {
  const r=record(v,summaryKeys+' description sourceContractCode sourceContractVersion sourceField createBy updateBy createTime valuesAsOf allowedValues');
  return {...summary(r), description:nil(r.description,text), sourceContractCode:nil(r.sourceContractCode,code), sourceContractVersion:nil(r.sourceContractVersion,number), sourceField:nil(r.sourceField,text), createBy:id(r.createBy), updateBy:id(r.updateBy), createTime:time(r.createTime), valuesAsOf:time(r.valuesAsOf), allowedValues:nil(r.allowedValues,v=>{const a=record(v,'variableCode variableName version memberCount previewMembers');return {variableCode:code(a.variableCode),variableName:text(a.variableName),version:id(a.version),memberCount:number(a.memberCount),previewMembers:array(a.previewMembers,member,100)};})};
}
export function decodeVariablePage(v: unknown): VariablePage {
  const batch=!!v&&typeof v==='object'&&'missingCodes' in v, r=record(v,batch?'items missingCodes valuesAsOf':'items page size total valuesAsOf');
  const items=array(r.items,decodeVariableSummary,100);check(new Set(items.map(i=>i.variableCode)).size===items.length);
  return {items,valuesAsOf:time(r.valuesAsOf),...(batch?{missingCodes:array(r.missingCodes,code,100)}:{page:number(r.page),size:number(r.size),total:number(r.total)})};
}
export function decodeVariableIssue(v:unknown):VariableIssue {const r=record(v,'variableCode code message');return {variableCode:code(r.variableCode),code:code(r.code),message:text(r.message)};}
export function decodeVariableQueryContext(v:unknown):VariableQueryContext {const r=record(v,'pointCode inputContractCode inputContractVersion usage');return {pointCode:code(r.pointCode),inputContractCode:code(r.inputContractCode),inputContractVersion:number(r.inputContractVersion),usage:one(r.usage,usages)};}
const subjectCodes=['CLIENT','OPERATING_ENTITY','CONSUMER','IP_ADDRESS','CARD_FINGERPRINT','CARD_BIN','EMAIL_TOKEN','PHONE_TOKEN'] as const, metricCodes=['COUNT','SUM_AMOUNT','DISTINCT_COUNT','LAST_AMOUNT','SECONDS_SINCE_LAST'] as const;
function distinctList<T>(items:T[]):T[] {check(new Set(items).size===items.length);return items;}
/** 风控点目录：事件、主体、统计方式不重复，窗口为已登记秒数且升序。 */
export function decodeFeatureCatalog(v:unknown):FeatureCatalog {const r=record(v,'pointCode currencyVariableCode velocityEvents velocityAnchors distinctSubjects listSubjects');const result={pointCode:code(r.pointCode),currencyVariableCode:code(r.currencyVariableCode),
  velocityEvents:array(r.velocityEvents,v=>{const e=record(v,'eventCode eventName includeCurrentAllowed');return {eventCode:code(e.eventCode),eventName:text(e.eventName),includeCurrentAllowed:bool(e.includeCurrentAllowed)};},20),
  velocityAnchors:array(r.velocityAnchors,v=>{const a=record(v,'subjectType metrics');const metrics=array(a.metrics,v=>{const m=record(v,'metric windowSeconds includeCurrentAllowed');const windows=array(m.windowSeconds,number,4);check(windows.length>0&&windows.every((w,i)=>(velocityWindows as readonly number[]).includes(w)&&(i===0||windows[i-1]<w)));return {metric:one(m.metric,metricCodes),windowSeconds:windows,includeCurrentAllowed:bool(m.includeCurrentAllowed)};},5);distinctList(metrics.map(m=>m.metric));return {subjectType:one(a.subjectType,subjectCodes),metrics};},8),
  distinctSubjects:distinctList(array(r.distinctSubjects,v=>one(v,subjectCodes),8)),listSubjects:distinctList(array(r.listSubjects,v=>one(v,subjectCodes),8))};
  distinctList(result.velocityEvents.map(e=>e.eventCode));distinctList(result.velocityAnchors.map(a=>a.subjectType));return result;}
export function decodeContext(v:unknown):VariableContext {const r=record(v,'supportedPoints sources valueTypes formatProfiles numericUnits limits contracts featureCatalogs');return {
  featureCatalogs:distinctCatalogs(array(r.featureCatalogs,decodeFeatureCatalog,20)),
  supportedPoints:array(r.supportedPoints,v=>{const p=record(v,'pointCode pointName');return {pointCode:code(p.pointCode),pointName:text(p.pointName)};}),
  sources:array(r.sources,v=>{const p=record(v,'sourceType createAllowed');return {sourceType:one(p.sourceType,sources),createAllowed:bool(p.createAllowed)};}), valueTypes:array(r.valueTypes,v=>one(v,types)), numericUnits:array(r.numericUnits,v=>one(v,['SCALAR','MONEY'])),
  formatProfiles:array(r.formatProfiles,v=>{const p=record(v,'formatCode label help');return {formatCode:code(p.formatCode),label:text(p.label),help:text(p.help)};}),
  limits:(()=>{const p=record(r.limits,'memberCount memberBodyBytes literalSetCount');return {memberCount:number(p.memberCount),memberBodyBytes:number(p.memberBodyBytes),literalSetCount:number(p.literalSetCount)};})(),
  contracts:array(r.contracts,v=>{const p=record(v,'contractCode contractVersion pointCode sourceType nodeType fields');return {contractCode:code(p.contractCode),contractVersion:number(p.contractVersion),pointCode:nil(p.pointCode,code),sourceType:one(p.sourceType,sources),nodeType:nil(p.nodeType,text),fields:array(p.fields,v=>{const f=record(v,'fieldKey valueType required dataClassification allowedUsages numericUnit currencyFieldKey semanticRole');return {fieldKey:text(f.fieldKey),valueType:one(f.valueType,types),required:bool(f.required),dataClassification:code(f.dataClassification),allowedUsages:array(f.allowedUsages,v=>one(v,usages),3),numericUnit:nil(f.numericUnit,v=>one(v,['SCALAR','MONEY'])),currencyFieldKey:nil(f.currencyFieldKey,text),semanticRole:nil(f.semanticRole,text)};})};})};}
function distinctCatalogs(items:FeatureCatalog[]):FeatureCatalog[] {distinctList(items.map(c=>c.pointCode));return items;}
function decodeMembers(v:unknown):VariableMembers {const r=record(v,'variableCode version items page size total valuesAsOf');return {variableCode:code(r.variableCode),version:id(r.version),items:array(r.items,v=>{const m=record(v,'value displayName sortOrder');return {value:scalar(m.value),displayName:nil(m.displayName,text),sortOrder:number(m.sortOrder)};},100),page:number(r.page),size:number(r.size),total:number(r.total),valuesAsOf:time(r.valuesAsOf)};}
function decodeReferences(v:unknown):VariableReferences {const r=record(v,'items nextCursor hasMore scannedRows');const result={items:array(r.items,v=>{const x=record(v,'consumerType rootId versionId versionNo code name scope location variableCode');return {consumerType:one(x.consumerType,['RULE_SET','DECISION_FLOW','VARIABLE'] as const),rootId:id(x.rootId),versionId:nil(x.versionId,id),versionNo:nil(x.versionNo,number),code:code(x.code),name:text(x.name),scope:text(x.scope),location:text(x.location),variableCode:code(x.variableCode)};},100),nextCursor:nil(r.nextCursor,text),hasMore:bool(r.hasMore),scannedRows:number(r.scannedRows)};check(result.hasMore===(result.nextCursor!==null));return result;}
const root='/decision-variables/v1', enc=encodeURIComponent;
const query=(filters:Record<string,string|number|undefined>)=>{const q=new URLSearchParams();for(const [k,v] of Object.entries(filters))if(v!==undefined&&v!=='')q.set(k,String(v));return q.size?'?'+q:'';};
const write=(method:string,body:unknown):RequestInit=>({method,headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
export function createVariableApi(client:RequestClient=session) {return {
  context:(signal?:AbortSignal)=>client.request(root+'/authoring-context',{signal},decodeContext),
  list:(filters:VariableFilters={},signal?:AbortSignal)=>client.request(root+'/variables'+query(filters),{signal},decodeVariablePage),
  detail:(variableCode:string,signal?:AbortSignal)=>client.request(root+'/variables/'+enc(variableCode),{signal},v=>{const r=decodeVariableDetail(v);check(r.variableCode===variableCode);return r;}),
  create:(body:CreateVariable)=>client.request(root+'/variables',write('POST',{...body,velocity:body.velocity??null,list:body.list??null}),v=>{const r=decodeVariableDetail(v);check(r.variableCode===body.variableCode&&r.version==='1');return r;}),
  update:(variableCode:string,body:UpdateVariable)=>client.request(root+'/variables/'+enc(variableCode),write('PUT',body),v=>{const r=decodeVariableDetail(v);check(r.variableCode===variableCode&&BigInt(r.version)===BigInt(body.expectedVersion)+1n);return r;}),
  remove:(variableCode:string,expectedVersion:string)=>client.request(root+'/variables/'+enc(variableCode)+query({expectedVersion}),{method:'DELETE'},v=>{const r=record(v,'variableCode status version');check(r.variableCode===variableCode&&r.status==='DELETED'&&BigInt(id(r.version))===BigInt(expectedVersion)+1n);return {variableCode,status:'DELETED' as const,version:id(r.version)};}),
  members:(variableCode:string,page=1,keyword='',expectedVersion?:string,signal?:AbortSignal)=>client.request(root+'/variables/'+enc(variableCode)+'/members'+query({page,size:100,keyword,expectedVersion}),{signal},v=>{const r=decodeMembers(v);check(r.variableCode===variableCode&&r.page===page&&(!expectedVersion||r.version===expectedVersion));return r;}),
  replaceMembers:(variableCode:string,expectedVersion:string,members:VariableMember[])=>client.request(root+'/variables/'+enc(variableCode)+'/members',write('PUT',{expectedVersion,members}),v=>{const r=record(v,'variableCode version memberCount updateTime');check(r.variableCode===variableCode&&BigInt(id(r.version))===BigInt(expectedVersion)+1n&&r.memberCount===members.length);return {variableCode,version:id(r.version),memberCount:number(r.memberCount),updateTime:time(r.updateTime)};}),
  references:(variableCode:string,cursor?:string,scope='ALL',signal?:AbortSignal)=>client.request(root+'/variables/'+enc(variableCode)+'/references'+query({scope,cursor,size:20}),{signal},v=>{const r=decodeReferences(v);check(r.items.every(i=>i.variableCode===variableCode));return r;}),
};}
export const variableApi=createVariableApi();

export function emptyOperand(role:'LEFT'|'RIGHT'='LEFT'):VariableOperand {return {operandRole:role,valueSequence:1,sourceType:'VARIABLE',fieldCode:null,valueType:'STRING',literalValue:null,variableCode:null,numericUnit:null,currencyCode:null,currencyVariableCode:null};}
export function variableOperand(variable:VariableSummary,role:'LEFT'|'RIGHT'='LEFT'):VariableOperand {return {...emptyOperand(role),variableCode:variable.variableCode,valueType:variable.valueType};}
export function literalOperand(type:ValueType,role:'LEFT'|'RIGHT'='RIGHT',value:string|boolean=''):VariableOperand {return {...emptyOperand(role),sourceType:'LITERAL',valueType:type,literalValue:type==='BOOLEAN'?(typeof value==='boolean'?value:false):value,numericUnit:type==='NUMBER'?'SCALAR':null};}
export function decodeVariableOperand(v:unknown):VariableOperand {const r=record(v,'operandRole valueSequence sourceType fieldCode valueType literalValue variableCode numericUnit currencyCode currencyVariableCode');check(r.fieldCode===null);const result:VariableOperand={operandRole:one(r.operandRole,['LEFT','RIGHT']),valueSequence:number(r.valueSequence),sourceType:one(r.sourceType,['VARIABLE','LITERAL']),fieldCode:null,valueType:one(r.valueType,types),literalValue:nil(r.literalValue,scalar),variableCode:nil(r.variableCode,code),numericUnit:nil(r.numericUnit,v=>one(v,['SCALAR','MONEY'])),currencyCode:nil(r.currencyCode,text),currencyVariableCode:nil(r.currencyVariableCode,code)};check(result.valueSequence>0&&result.valueSequence<=100&&(result.sourceType==='VARIABLE'?result.variableCode!==null&&result.literalValue===null&&result.numericUnit===null&&result.currencyCode===null&&result.currencyVariableCode===null:result.variableCode===null&&result.literalValue!==null));return result;}
export function normalizeLiteral(value:string|boolean,type:ValueType,format='NONE'):string|boolean {
  const invalid=()=>{throw new Error(t('variable.error.invalidValue'));};
  if(type==='BOOLEAN')return typeof value==='boolean'?value:invalid();
  if(typeof value!=='string')return invalid();
  if(type==='NUMBER') {if(!/^-?(0|[1-9]\d{0,15})(\.\d{1,8})?$/.test(value))return invalid();const [whole,fraction='']=value.split('.');return `${BigInt(whole!)===0n&&/^0*$/.test(fraction)?'0':whole}.${fraction.padEnd(8,'0')}`;}
  if(type==='DURATION')return /^(0|[1-9]\d*)$/.test(value)&&BigInt(value)<=9223372036854775807n?value:invalid();
  if(type==='DATETIME')return validUtcTime(value)?value:invalid();
  if(!value||[...value].length>512||/[\p{Cc}\p{Cs}]/u.test(value))return invalid();
  if(format==='BCP47'){if(!/^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$/.test(value))return invalid();try{return Intl.getCanonicalLocales(value)[0]??invalid();}catch{return invalid();}}
  const patterns:Record<string,RegExp>={TOKEN:/^[A-Z][A-Z0-9_]{0,63}$/,UPPER_ALPHA_2:/^[A-Z]{2}$/,UPPER_ALPHA_3:/^[A-Z]{3}$/,DIGITS_4:/^\d{4}$/,POSITIVE_ID:/^[1-9]\d{0,18}$/};
  if(patterns[format]&&!patterns[format]!.test(value))return invalid();
  if(format==='POSITIVE_ID'&&BigInt(value)>9223372036854775807n)return invalid();
  return value;
}
export function prepareOperands(operator:string,operands:readonly RuleOperand[]):VariableOperand[] {
  const left=operands.filter(o=>o.operandRole==='LEFT'),right=operands.filter(o=>o.operandRole==='RIGHT');
  const unary=['IS_NULL','IS_NOT_NULL'].includes(operator),set=['IN','NOT_IN'].includes(operator);
  if(left.length!==1||(unary?right.length!==0:set?right.length<1||right.length>100:right.length!==(operator==='BETWEEN'?2:1)))throw new Error(t('variable.error.operandsIncomplete'));
  if(!(operatorCodes as string[]).includes(operator))throw new Error(t('variable.error.invalidOperator'));
  const prepared=[...left,...right].map(o=>{const r=decodeVariableOperand(o);return {...r,literalValue:r.sourceType==='LITERAL'?normalizeLiteral(r.literalValue!,r.valueType):null};});
  if(left[0]!.valueSequence!==1||right.some((o,i)=>o.valueSequence!==i+1))throw new Error(t('variable.error.operandSequence'));
  if(right.some(o=>o.valueType!==left[0]!.valueType))throw new Error(t('variable.error.typeMismatch'));
  if(['GT','GTE','LT','LTE','BETWEEN'].includes(operator)&&!['NUMBER','DATETIME','DURATION'].includes(left[0]!.valueType))throw new Error(t('variable.error.rangeType'));
  if(set&&right.length>1&&right.some(o=>o.sourceType!=='LITERAL'))throw new Error(t('variable.error.setOperand'));
  for(const o of prepared){
    if(o.sourceType!=='LITERAL')continue;
    if(o.valueType!=='NUMBER'){
      if(o.numericUnit!==null||o.currencyCode!==null||o.currencyVariableCode!==null)throw new Error(t('variable.error.moneyOnNonNumber'));
    }else if(o.numericUnit==='SCALAR'){
      if(o.currencyCode!==null||o.currencyVariableCode!==null)throw new Error(t('variable.error.currencyOnScalar'));
    }else if(o.numericUnit==='MONEY'){
      if((o.currencyCode===null)===(o.currencyVariableCode===null)||o.currencyCode!==null&&!/^[A-Z]{3}$/.test(o.currencyCode))throw new Error(t('variable.error.moneyCurrency'));
    }else throw new Error(t('variable.error.numericUnit'));
  }
  return prepared;
}

/** 已选项仍可展示，但用途或作用域改变后不能继续作为候选项。 */
export function matchesVariableFilters(variable:VariableSummary,filters:VariableFilters):boolean {
  for(const key of ['sourceType','valueType','status'] as const)if(filters[key]&&variable[key]!==filters[key])return false;
  if(filters.pointCode&&variable.pointCode!==null&&variable.pointCode!==filters.pointCode)return false;
  if(filters.usage&&!variable.usages.some(usage=>usage===filters.usage))return false;
  return variable.sourceType!=='NODE_OUTPUT'||!filters.nodeType||filters.nodeType==='RULE_SET';
}

export function parsePastedMembers(value:string,type:ValueType,format:string):VariableMember[] {
  const result=deduplicatePastedMembers(value,type,format);
  if(result.duplicateRows.length)throw new Error(t('variable.error.duplicateRow', { row: result.duplicateRows[0]! }));
  return result.members;
}

/** 仅由用户显式去重动作调用；保留首次出现的成员与展示名称。 */
export function deduplicatePastedMembers(value:string,type:ValueType,format:string):{members:VariableMember[];duplicateRows:number[]} {
  if(value==='')return {members:[],duplicateRows:[]};
  const lines=value.replaceAll('\r\n','\n').split('\n');if(lines.at(-1)==='')lines.pop();
  const seen=new Set<string>(),members:VariableMember[]=[],duplicateRows:number[]=[];
  lines.forEach((line,index)=>{
    const [raw,...names]=line.split('\t');
    try{
      const memberValue=normalizeLiteral(type==='BOOLEAN'?(raw==='true'?true:raw==='false'?false:raw!):raw!,type,format);
      const displayName=names.join('\t')||null;
      if(displayName!==null&&([...displayName].length>255||/[\p{Cc}\p{Cs}]/u.test(displayName)))throw new Error(t('variable.error.displayName'));
      const key=typeof memberValue+':'+String(memberValue);
      if(seen.has(key)){duplicateRows.push(index+1);return;}
      seen.add(key);members.push({value:memberValue,displayName});
    }catch(e){throw new Error(t('variable.error.rowProblem', { row: index+1, problem: e instanceof Error?e.message:t('variable.error.rowInvalid') }));}
  });
  if(members.length>1000)throw new Error(t('variable.error.tooManyMembers'));
  return {members,duplicateRows};
}
