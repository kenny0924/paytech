import { ApiError, session } from '../api/session.ts';
import type { RequestClient } from '../api/session.ts';
import { requestPage } from '../api/paging.ts';
import type { PagingRequestBody } from '../api/paging.ts';
import type { ConsoleResource, PagingResponseBody } from '../api/types.ts';
import { enumLabel, translator } from '../i18n/index.ts';
export type DictionaryKind = 'rejection-reasons' | 'subject-tags';
export type DictionaryStatus = 'ACTIVE' | 'DISABLED';
export interface DictionaryEntry { code: string; name: string; description: string | null; defaultMessage?: string; status: DictionaryStatus; version: string; createdBy: string; updatedBy: string; createTime: string; updateTime: string }
export interface DictionaryReference { ruleSetId: string; pointCode: string; ruleSetCode: string; ruleSetName: string; ruleSetVersionId: string; versionNo: number; ruleCode: string; ruleName: string; referenceKind: string; actionSequenceNo: number | null; targetBindingCode: string | null; ruleSetStatus: 'CONFIGURABLE' | 'DELETED'; isCurrentConfiguration: boolean; activeEnvironments: string[] }
export interface FlowReasonReference { sourceType:'FLOW_END'; pointCode:string; ruleSetId:null;ruleSetCode:null;ruleSetName:null;ruleSetVersionId:null;versionNo:null;ruleCode:null;ruleName:null;referenceKind:'REJECTION_REASON';actionSequenceNo:null;targetBindingCode:null;ruleSetStatus:null;isCurrentConfiguration:boolean;activeEnvironments:string[];flowId:string;flowCode:string;flowName:string;flowVersionId:string;flowVersionNo:number;nodeKey:string;nodeName:string;flowStatus:'CONFIGURABLE'|'DELETED' }
export type ConfigurationReference = DictionaryReference | FlowReasonReference;
export interface HistoryItem { ruleSetId: string; ruleSetVersionId: string; pointCode: string; ruleSetCode: string; ruleSetName: string; versionNo: number; status: 'DRAFT' | 'FROZEN' | 'RETIRED'; isCurrent: boolean; createdBy: string; changeReason: string; createTime: string }
export interface ReleaseReference { releaseId: string; releaseNo: string; environmentCode: string; status: string; pointCode: string; pointVersionId: string; pointVersionNo: number; ruleSetVersionId: string; versionNo: number; activatedTime: string | null; supersededTime: string | null }
/** 规则集引用中执行发布的状态；显示名是 `risk.enum.ruleSetReleaseStatus.<值>`。 */
export const releaseStatusCodes = ['DRAFT', 'VALIDATING', 'SHADOWING', 'REVIEW_PENDING', 'APPROVED', 'ACTIVE', 'SUPERSEDED', 'REJECTED'];
export function releaseLabel(status: string): string { return enumLabel(translator('risk'), 'enum.ruleSetReleaseStatus', status); }
export function invariant(value: unknown): asserts value { if (!value) throw new Error('invalid Risk response'); }
export function exact(value: unknown, keys: string[]): Record<string, unknown> {
  invariant(value && typeof value === 'object' && !Array.isArray(value)); const row = value as Record<string, unknown>;
  invariant(Object.keys(row).length === keys.length && keys.every(key => Object.hasOwn(row, key))); return row;
}
export function id(value: unknown): string { invariant(typeof value === 'string' && /^[1-9]\d{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n); return value; }
function code(value: unknown): string { invariant(typeof value === 'string' && /^[A-Z][A-Z0-9_]{0,63}$/.test(value)); return value; }
function text(value: unknown, max: number, multiline = false, allowBlank = false): string {
  invariant(typeof value === 'string' && (allowBlank ? value.length > 0 : value.trim().length > 0) && [...value].length <= max && !/[\uD800-\uDFFF]/u.test(value)
    && !(multiline ? /[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x9f]/ : /[\x00-\x1f\x7f-\x9f]/).test(value)); return value;
}
function time(value: unknown): string { invariant(typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}Z$/.test(value) && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0,19) === value.slice(0,19)); return value; }
function positive(value: unknown): number { invariant(typeof value === 'number' && Number.isInteger(value) && value > 0 && value <= 2147483647); return value; }
function bool(value: unknown): boolean { invariant(typeof value === 'boolean'); return value; }
function oneOf<T extends string>(value: unknown, values: readonly T[]): T { invariant(typeof value === 'string' && values.includes(value as T)); return value as T; }
function nullable<T>(value: unknown, decode: (value: unknown) => T): T | null { return value === null ? null : decode(value); }
export function decodeDictionary(kind: DictionaryKind, value: unknown): DictionaryEntry {
  const reason = kind === 'rejection-reasons'; const key = reason ? 'reasonCode' : 'tagCode';
  const row = exact(value, [key, 'name', 'description', ...(reason ? ['defaultMessage'] : []), 'status', 'version', 'createdBy', 'updatedBy', 'createTime', 'updateTime']);
  const name = text(row.name, 100); invariant(name === name.trim());
  return { code: code(row[key]), name, description: nullable(row.description, value => text(value, 500, true, true)), ...(reason ? { defaultMessage: text(row.defaultMessage, 500, true) } : {}),
    status: oneOf(row.status, ['ACTIVE', 'DISABLED']), version: id(row.version), createdBy: id(row.createdBy), updatedBy: id(row.updatedBy), createTime: time(row.createTime), updateTime: time(row.updateTime) };
}
export function decodePage<T>(value: unknown, decoder: (row: unknown) => T): PagingResponseBody<T> {
  const row = exact(value, ['items', 'page', 'size', 'total']); invariant(Array.isArray(row.items) && Number.isSafeInteger(row.total) && (row.total as number) >= 0);
  const page = positive(row.page); const size = positive(row.size); invariant(page <= 100000 && size <= 200 && row.items.length === Math.min(size, Math.max(0, (row.total as number) - (page - 1) * size)));
  return { items: row.items.map(decoder), page, size, total: row.total as number };
}
function decodeHistory(value: unknown): HistoryItem {
  const r = exact(value, ['ruleSetId','ruleSetVersionId','pointCode','ruleSetCode','ruleSetName','versionNo','status','isCurrent','createdBy','changeReason','createTime']);
  return { ruleSetId:id(r.ruleSetId),ruleSetVersionId:id(r.ruleSetVersionId),pointCode:code(r.pointCode),ruleSetCode:code(r.ruleSetCode),ruleSetName:text(r.ruleSetName,255),versionNo:positive(r.versionNo),status:oneOf(r.status,['DRAFT','FROZEN','RETIRED']),isCurrent:bool(r.isCurrent),createdBy:id(r.createdBy),changeReason:text(r.changeReason,500,true),createTime:time(r.createTime) };
}
function decodeRelease(value: unknown): ReleaseReference {
  const r = exact(value,['releaseId','releaseNo','environmentCode','status','pointCode','pointVersionId','pointVersionNo','ruleSetVersionId','versionNo','activatedTime','supersededTime']);
  const result = { releaseId:id(r.releaseId),releaseNo:text(r.releaseNo,64),environmentCode:oneOf(r.environmentCode,['TEST','PRODUCTION']),status:oneOf(r.status,releaseStatusCodes),pointCode:code(r.pointCode),pointVersionId:id(r.pointVersionId),pointVersionNo:positive(r.pointVersionNo),ruleSetVersionId:id(r.ruleSetVersionId),versionNo:positive(r.versionNo),activatedTime:nullable(r.activatedTime,time),supersededTime:nullable(r.supersededTime,time) };
  invariant(result.status !== 'ACTIVE' || (result.activatedTime !== null && result.supersededTime === null)); return result;
}
function decodeReference(value: unknown): DictionaryReference {
  const r=exact(value,['ruleSetId','pointCode','ruleSetCode','ruleSetName','ruleSetVersionId','versionNo','ruleCode','ruleName','referenceKind','actionSequenceNo','targetBindingCode','ruleSetStatus','isCurrentConfiguration','activeEnvironments']);
  invariant(Array.isArray(r.activeEnvironments)); const envs=r.activeEnvironments.map(value=>oneOf(value,['TEST','PRODUCTION'])); invariant(new Set(envs).size===envs.length);
  const result: DictionaryReference={ruleSetId:id(r.ruleSetId),pointCode:code(r.pointCode),ruleSetCode:code(r.ruleSetCode),ruleSetName:text(r.ruleSetName,255),ruleSetVersionId:id(r.ruleSetVersionId),versionNo:positive(r.versionNo),ruleCode:code(r.ruleCode),ruleName:text(r.ruleName,255),referenceKind:oneOf(r.referenceKind,['REJECTION_REASON','SUBJECT_TAG']),actionSequenceNo:nullable(r.actionSequenceNo,positive),targetBindingCode:nullable(r.targetBindingCode,code),ruleSetStatus:oneOf(r.ruleSetStatus,['CONFIGURABLE','DELETED']),isCurrentConfiguration:bool(r.isCurrentConfiguration),activeEnvironments:envs};
  invariant(result.referenceKind==='REJECTION_REASON' ? result.actionSequenceNo===null && result.targetBindingCode===null : result.actionSequenceNo!==null && result.targetBindingCode!==null);
  invariant(result.ruleSetStatus!=='DELETED' || !envs.length); return result;
}
export function decodeReasonReference(value:unknown):ConfigurationReference {
  const ruleKeys=['ruleSetId','pointCode','ruleSetCode','ruleSetName','ruleSetVersionId','versionNo','ruleCode','ruleName','referenceKind','actionSequenceNo','targetBindingCode','ruleSetStatus','isCurrentConfiguration','activeEnvironments'];
  const flowKeys=['flowId','flowCode','flowName','flowVersionId','flowVersionNo','nodeKey','nodeName','flowStatus'];
  const r=exact(value,[...ruleKeys,'sourceType',...flowKeys]);
  if(r.sourceType==='RULE_SET'){invariant(flowKeys.every(k=>r[k]===null));return decodeReference(Object.fromEntries(ruleKeys.map(k=>[k,r[k]])));}
  invariant(r.sourceType==='FLOW_END'&&r.referenceKind==='REJECTION_REASON'&&['ruleSetId','ruleSetCode','ruleSetName','ruleSetVersionId','versionNo','ruleCode','ruleName','actionSequenceNo','targetBindingCode','ruleSetStatus'].every(k=>r[k]===null));
  invariant(Array.isArray(r.activeEnvironments)&&r.activeEnvironments.length===0);
  const nodeKey=text(r.nodeKey,64);invariant(/^[A-Za-z][A-Za-z0-9_-]{0,63}$/.test(nodeKey));
  return {sourceType:'FLOW_END',pointCode:code(r.pointCode),ruleSetId:null,ruleSetCode:null,ruleSetName:null,ruleSetVersionId:null,versionNo:null,ruleCode:null,ruleName:null,referenceKind:'REJECTION_REASON',actionSequenceNo:null,targetBindingCode:null,ruleSetStatus:null,isCurrentConfiguration:bool(r.isCurrentConfiguration),activeEnvironments:[],flowId:id(r.flowId),flowCode:code(r.flowCode),flowName:text(r.flowName,100),flowVersionId:id(r.flowVersionId),flowVersionNo:positive(r.flowVersionNo),nodeKey,nodeName:text(r.nodeName,100),flowStatus:oneOf(r.flowStatus,['CONFIGURABLE','DELETED'])};
}
const prefix=(kind:DictionaryKind)=>`/decision-dictionaries/v1/${kind}`;
const path=(kind:DictionaryKind,code:string)=>`${prefix(kind)}/${encodeURIComponent(code)}`;
export function dictionaryWriteOutcome(error:unknown): 'UNKNOWN'|'CONFLICT'|'REJECTED' {
  if (error instanceof ApiError && error.status===409) return 'CONFLICT';
  return error instanceof ApiError && [400,401,403,404,413,422,429].includes(error.status) ? 'REJECTED' : 'UNKNOWN';
}
export function hasDictionaryAction(resources: readonly ConsoleResource[],kind: DictionaryKind,action:'CREATE'|'UPDATE'|'STATUS') {
  return resources.some(row=>row.resourceType==='BUTTON' && row.resourceCode===`OPERATIONS_RISK_${kind==='rejection-reasons'?'REJECTION_REASON':'SUBJECT_TAG'}_${action}`);
}
export function createRiskDictionaryApi(client:RequestClient=session) { return {
  list:(kind:DictionaryKind,paging:PagingRequestBody,filters:Readonly<Record<string,string>>,signal?:AbortSignal)=>requestPage(prefix(kind),paging,signal,client,filters,value=>decodePage(value,row=>decodeDictionary(kind,row))),
  detail:(kind:DictionaryKind,code:string,signal?:AbortSignal)=>client.request(path(kind,code),{signal},value=>{ const row=decodeDictionary(kind,value);invariant(row.code===code);return row; }),
  write:(kind:DictionaryKind,operation:'CREATE'|'UPDATE'|'STATUS',entryCode:string,body:Record<string,unknown>,actorId:string)=>client.request(operation==='CREATE'?prefix(kind):`${path(kind,entryCode)}${operation==='STATUS'?'/status':''}`,
    {method:operation==='UPDATE'?'PUT':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)},value=>{
      const row=exact(value,['dictionaryType','code','version','status','changedBy','changedTime']);
      invariant(row.dictionaryType===(kind==='rejection-reasons'?'REJECTION_REASON':'SUBJECT_TAG') && row.code===entryCode && id(row.changedBy)===actorId);
      invariant(BigInt(id(row.version))===(operation==='CREATE'?1n:BigInt(id(body.expectedVersion))+1n));
      oneOf(row.status,['ACTIVE','DISABLED']);time(row.changedTime); if(operation!=='UPDATE') invariant(row.status===(operation==='CREATE'?'ACTIVE':body.status)); return row;
    }),
  references:(kind:DictionaryKind,code:string,paging:PagingRequestBody,scope:string,signal?:AbortSignal)=>requestPage(`${path(kind,code)}/references`,paging,signal,client,{scope},value=>decodePage<ConfigurationReference>(value,kind==='rejection-reasons'?decodeReasonReference:decodeReference)),
  history:(root:string,paging:PagingRequestBody,maxVersionNo:number,signal?:AbortSignal)=>requestPage(`/decision-rule-sets/v1/rule-sets/${encodeURIComponent(root)}/versions`,paging,signal,client,{maxVersionNo:String(maxVersionNo)},value=>{
    const page=decodePage(value,decodeHistory);let last=maxVersionNo+1;for(const row of page.items){invariant(row.ruleSetId===root && row.versionNo<last);last=row.versionNo;}return page;
  }),
  releases:(root:string,paging:PagingRequestBody,filters:Readonly<Record<string,string>>,signal?:AbortSignal)=>{
    const query=new URLSearchParams({...filters,page:String(paging.page),size:String(paging.size)});
    return client.request(`/decision-rule-sets/v1/rule-sets/${encodeURIComponent(root)}/release-references?${query}`,{signal},value=>{
      const r=exact(value,['ruleSetId','inUse','references']);invariant(id(r.ruleSetId)===root);const refs=decodePage(r.references,decodeRelease);invariant(refs.page===paging.page && refs.size===paging.size);
      const inUse=bool(r.inUse);for(const row of refs.items){invariant(row.status!=='ACTIVE'||inUse);for(const [key,val] of Object.entries(filters)) invariant(row[key as keyof ReleaseReference]===val);} return {ruleSetId:root,inUse,references:refs};
    });
  },
}; }
export const riskDictionaryApi=createRiskDictionaryApi();
