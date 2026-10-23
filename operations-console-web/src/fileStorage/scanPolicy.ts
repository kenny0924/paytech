import { ApiError } from '../api/session.ts';
import { accessLevels, decodeMutation, instanceCodePattern, scanPolicyCodePattern, sourceSystemPattern, defaultSourceSystem, storageAdminPath } from './fileStorage.ts';
import type { AccessLevel, StorageMutation } from './fileStorage.ts';

/** 检查策略列表路径；修订、激活与停用都挂在策略代码下。 */
export const scanPolicyPath = `${storageAdminPath}/scan-policies`;
export const policyPath = (policyCode: string) => `${scanPolicyPath}/${encodeURIComponent(policyCode)}`;
export const revisionPath = (policyCode: string, revisionNo: number) => `${policyPath(policyCode)}/revisions/${revisionNo}`;

export const policyStatuses = ['ACTIVE', 'DRAFT', 'RETIRED'] as const;
export const revisionStatuses = ['DRAFT', 'ACTIVE', 'RETIRED'] as const;
export type PolicyStatus = typeof policyStatuses[number];
export type RevisionStatus = typeof revisionStatuses[number];
export const policyStatusLabels: Readonly<Record<PolicyStatus, string>> = { ACTIVE: '启用', DRAFT: '草稿', RETIRED: '已停用' };
export const revisionStatusLabels: Readonly<Record<RevisionStatus, string>> = { DRAFT: '草稿', ACTIVE: '生效中', RETIRED: '已停用' };
export const scanTypeLabels: Readonly<Record<string, string>> = { FORMAT: '格式检查' };
/** 最大字节数范围：1 字节到 100 MiB，与浏览器直传和单次 PUT 的能力一致。 */
export const maxSizeRange = { min: 1, max: 100 * 1024 * 1024 } as const;
const mib = 1024 * 1024;

export interface ScanPolicySummary {
  policyCode: string; activeRevisionNo: number | null; draftRevisionNo: number | null; allowedMimeTypes: string[]; maxSizeBytes: string;
  routeCount: number; status: PolicyStatus; updateTime: string;
}
export interface ScanPolicyList { items: ScanPolicySummary[]; supportedMimeTypes: string[] }
export interface ScanPolicyRequirement { scannerCode: string; scanType: string; minimumScannerVersion: string; acceptedResultCodes: string[] }
export interface ScanPolicyRevision {
  revisionNo: number; status: RevisionStatus; allowedMimeTypes: string[]; maxSizeBytes: string; requirements: ScanPolicyRequirement[];
  version: number; createTime: string; updateTime: string;
}
export interface ScanPolicyRouteRef { sourceSystem: string; accessLevel: AccessLevel; instanceCode: string; bucketName: string }
export interface ScanPolicyDetail { policyCode: string; routes: ScanPolicyRouteRef[]; revisions: ScanPolicyRevision[] }

const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const exactFields = (v: Record<string, unknown>, fields: readonly string[]) =>
  Object.keys(v).length === fields.length && fields.every(field => field in v);
const text = (v: unknown): v is string => typeof v === 'string' && v.trim().length > 0;
const integer = (v: unknown, min: number) => Number.isSafeInteger(v) && Number(v) >= min;
const optionalInteger = (v: unknown) => v === null || integer(v, 1);
const instant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
const oneOf = (values: readonly string[]) => (v: unknown) => typeof v === 'string' && values.includes(v);
const texts = (v: unknown, nonEmpty: boolean) => Array.isArray(v) && (!nonEmpty || v.length > 0) && v.every(text)
  && new Set(v).size === v.length;
/** 十进制字节数：Wire 上是字符串。 */
const byteCount = (v: unknown) => typeof v === 'string' && /^[1-9][0-9]{0,15}$/.test(v);
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

function decodeSummary(value: unknown): ScanPolicySummary {
  require(object(value) && exactFields(value, ['policyCode', 'activeRevisionNo', 'draftRevisionNo', 'allowedMimeTypes', 'maxSizeBytes',
    'routeCount', 'status', 'updateTime']));
  const v = value as unknown as ScanPolicySummary;
  require(scanPolicyCodePattern.test(String(v.policyCode)) && optionalInteger(v.activeRevisionNo) && optionalInteger(v.draftRevisionNo)
    && texts(v.allowedMimeTypes, false) && byteCount(v.maxSizeBytes) && integer(v.routeCount, 0) && oneOf(policyStatuses)(v.status)
    && instant(v.updateTime) && (v.status !== 'ACTIVE' || v.activeRevisionNo !== null)
    && (v.status !== 'DRAFT' || (v.activeRevisionNo === null && v.draftRevisionNo !== null)));
  return v;
}

/** 策略列表严格解码：策略代码不重复，可识别类型非空且不重复。 */
export function decodeScanPolicyList(value: unknown): ScanPolicyList {
  require(object(value) && exactFields(value, ['items', 'supportedMimeTypes']) && Array.isArray(value.items)
    && texts(value.supportedMimeTypes, true));
  const items = (value as { items: unknown[] }).items.map(decodeSummary);
  require(new Set(items.map(item => item.policyCode)).size === items.length);
  return { items, supportedMimeTypes: [...(value as { supportedMimeTypes: string[] }).supportedMimeTypes] };
}

function decodeRequirement(value: unknown): ScanPolicyRequirement {
  require(object(value) && exactFields(value, ['scannerCode', 'scanType', 'minimumScannerVersion', 'acceptedResultCodes']));
  const v = value as Record<string, unknown>;
  const version = typeof v.minimumScannerVersion === 'number' && integer(v.minimumScannerVersion, 0)
    ? String(v.minimumScannerVersion) : v.minimumScannerVersion;
  require(text(v.scannerCode) && text(v.scanType) && text(version) && texts(v.acceptedResultCodes, true));
  return { scannerCode: v.scannerCode as string, scanType: v.scanType as string, minimumScannerVersion: version as string,
    acceptedResultCodes: v.acceptedResultCodes as string[] };
}

function decodeRevision(value: unknown): ScanPolicyRevision {
  require(object(value) && exactFields(value, ['revisionNo', 'status', 'allowedMimeTypes', 'maxSizeBytes', 'requirements', 'version',
    'createTime', 'updateTime']) && Array.isArray(value.requirements));
  const v = value as unknown as ScanPolicyRevision;
  require(integer(v.revisionNo, 1) && oneOf(revisionStatuses)(v.status) && texts(v.allowedMimeTypes, true) && byteCount(v.maxSizeBytes)
    && integer(v.version, 1) && instant(v.createTime) && instant(v.updateTime));
  return { ...v, requirements: v.requirements.map(decodeRequirement) };
}

function decodeRouteRef(value: unknown): ScanPolicyRouteRef {
  require(object(value) && exactFields(value, ['sourceSystem', 'accessLevel', 'instanceCode', 'bucketName']));
  const v = value as unknown as ScanPolicyRouteRef;
  require((v.sourceSystem === defaultSourceSystem || sourceSystemPattern.test(String(v.sourceSystem))) && oneOf(accessLevels)(v.accessLevel)
    && instanceCodePattern.test(String(v.instanceCode)) && text(v.bucketName));
  return v;
}

/** 策略详情：必须是请求的策略；修订号不重复、按倒序；最多一个生效修订和一个草稿。 */
export function decodeScanPolicyDetail(value: unknown, policyCode: string): ScanPolicyDetail {
  require(object(value) && exactFields(value, ['policyCode', 'routes', 'revisions']) && value.policyCode === policyCode
    && Array.isArray(value.routes) && Array.isArray(value.revisions));
  const v = value as { routes: unknown[]; revisions: unknown[] };
  const revisions = v.revisions.map(decodeRevision);
  require(revisions.every((revision, index) => index === 0 || revisions[index - 1].revisionNo > revision.revisionNo)
    && revisions.filter(revision => revision.status === 'ACTIVE').length <= 1
    && revisions.filter(revision => revision.status === 'DRAFT').length <= 1);
  return { policyCode, routes: v.routes.map(decodeRouteRef), revisions };
}

/**
 * 策略写回执：objectCode 为 {策略代码}/{修订号}
 * <li>已知修订号时必须一致；新建修订、停用等修订号由 Platform 决定时只校验策略代码与正整数修订号。</li>
 */
export function decodePolicyMutation(value: unknown, policyCode: string, revisionNo?: number): StorageMutation {
  const objectCode = object(value) && typeof value.objectCode === 'string' ? value.objectCode : '';
  const match = /^(.+)\/([1-9][0-9]{0,8})$/.exec(objectCode);
  require(!!match && match[1] === policyCode && (revisionNo === undefined || Number(match[2]) === revisionNo));
  return decodeMutation(value, objectCode);
}

/** 格式检查无法识别的允许类型；这些类型的文件不会通过检查，页面需要提示。 */
export const unsupportedMimeTypes = (allowed: readonly string[], supported: readonly string[]) =>
  allowed.filter(type => !supported.includes(type));

/** 允许类型的短名：application/pdf → PDF，image/jpeg → JPEG，text/csv → CSV。 */
export const mimeTypeLabel = (mimeType: string) => (mimeType.split('/')[1] ?? mimeType).toUpperCase();

/** 字节数展示：整 MiB 显示为 MB，其余按 KB / B。 */
export function byteSizeLabel(bytes: string | number): string {
  const value = Number(bytes);
  if (!Number.isFinite(value) || value < 0) return '—';
  if (value >= mib) return `${Number.isInteger(value / mib) ? value / mib : (value / mib).toFixed(1)} MB`;
  if (value >= 1024) return `${Number.isInteger(value / 1024) ? value / 1024 : (value / 1024).toFixed(1)} KB`;
  return `${value} B`;
}

/** 表单中的最大大小以 MB 录入（最多两位小数），提交时换算为字节字符串。 */
export function megabytesToBytes(megabytes: number | null | undefined): string {
  const bytes = Math.round(Number(megabytes) * mib);
  if (!Number.isSafeInteger(bytes) || bytes < maxSizeRange.min || bytes > maxSizeRange.max) throw new Error('最大大小须在 1 字节到 100 MB 之间');
  return String(bytes);
}
export const bytesToMegabytes = (bytes: string) => Math.round(Number(bytes) / mib * 100) / 100;

export interface PolicyFormValues { policyCode?: string; allowedMimeTypes?: string[]; maxSizeMegabytes?: number; reason?: string }

/** 允许类型：去重并按可识别类型的顺序排列；为空或含不可识别类型时拒绝。 */
function allowedTypes(values: PolicyFormValues, supported: readonly string[]): string[] {
  const selected = [...new Set(values.allowedMimeTypes ?? [])];
  if (!selected.length) throw new Error('至少选择一种允许类型');
  if (unsupportedMimeTypes(selected, supported).length) throw new Error('只能选择格式检查可识别的类型');
  return supported.filter(type => selected.includes(type));
}

/** 新建策略请求体：同时建修订 1（草稿）。 */
export function createPolicyBody(values: PolicyFormValues, supported: readonly string[]) {
  return { policyCode: (values.policyCode ?? '').trim(), allowedMimeTypes: allowedTypes(values, supported),
    maxSizeBytes: megabytesToBytes(values.maxSizeMegabytes), reason: (values.reason ?? '').trim() };
}

/** 修改草稿修订请求体。 */
export function updateRevisionBody(values: PolicyFormValues, revision: ScanPolicyRevision, supported: readonly string[]) {
  return { allowedMimeTypes: allowedTypes(values, supported), maxSizeBytes: megabytesToBytes(values.maxSizeMegabytes),
    expectedVersion: revision.version, reason: (values.reason ?? '').trim() };
}

/** 废弃、激活、停用共用的请求体：期望版本 + 原因。 */
export const versionedReasonBody = (revision: ScanPolicyRevision, reason: string) => ({ expectedVersion: revision.version, reason: reason.trim() });

/** 当前生效修订与草稿修订。 */
export function revisionSlots(detail: ScanPolicyDetail) {
  return { active: detail.revisions.find(revision => revision.status === 'ACTIVE') ?? null,
    draft: detail.revisions.find(revision => revision.status === 'DRAFT') ?? null };
}

/** 新建修订的前置：需要有生效修订作模板且没有草稿；返回不满足的原因，满足时返回 null。 */
export function newRevisionBlocker(detail: ScanPolicyDetail): string | null {
  const { active, draft } = revisionSlots(detail);
  if (draft) return `已有草稿 r${draft.revisionNo}，先激活或废弃后才能新建修订`;
  if (!active) return '没有生效修订可作模板';
  return null;
}

/** 停用的前置：需要有生效修订，且没有写入规则引用该策略。 */
export function retireBlocker(detail: ScanPolicyDetail): string | null {
  if (!revisionSlots(detail).active) return '没有生效修订';
  if (detail.routes.length) return `被 ${detail.routes.length} 条写入规则引用，不能停用`;
  return null;
}

/** 激活影响说明。 */
export function activationNotice(detail: ScanPolicyDetail, draft: ScanPolicyRevision): string {
  const active = revisionSlots(detail).active;
  return active
    ? `激活 r${draft.revisionNo} 后：之后的新上传按 r${draft.revisionNo} 检查，r${active.revisionNo} 改为已停用；已开始、尚未完成的上传仍按 r${active.revisionNo} 检查。`
    : `激活 r${draft.revisionNo} 后，写入规则可以选择该策略，之后的新上传按 r${draft.revisionNo} 检查。`;
}

/** 写入规则可选的策略：只列有生效修订的策略。 */
export const selectablePolicies = (items: readonly ScanPolicySummary[]) => items.filter(item => item.activeRevisionNo !== null);
