import { ApiError } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';
import { enumLabel, translator } from '../i18n/index.ts';

const t = translator('channel');

/** 管理接口前缀；浏览器经 Gateway 访问时由会话加上 /api/operations。 */
export const externalApiBase = '/external-api-admin/v1';
export const standardApiPath = `${externalApiBase}/standard-apis`;
/** 渠道协议与沙箱验证的管理路径：路径与字段名保留 integrations / integrationCode，值即渠道编码。 */
export const integrationPath = `${externalApiBase}/integrations`;
export const standardApiRoute = '/external-api/standard-apis';
export const manageStandardApis = 'OPERATIONS_EXTERNAL_STANDARD_API_MANAGE';
/**
 * 渠道详情「协议配置 / 沙箱验证」写操作的按钮：接入方页面并入渠道后沿用标准 API 页的「维护配置」按钮，
 * 两者在 Platform 绑定同一外部 API 管理权限。
 */
export const manageProtocol = manageStandardApis;
export const listSizes = [10, 20, 50, 100];
export const codePattern = /^[A-Z][A-Z0-9_]{0,63}$/;

export type FieldLocation = 'HEADER' | 'BODY' | 'QUERY' | 'PATH';
export type FieldDataType = 'STRING' | 'BOOLEAN' | 'INTEGER' | 'DECIMAL' | 'OBJECT' | 'ARRAY';
export type ScriptStage = 'REQUEST' | 'RESPONSE';
export const fieldLocations: FieldLocation[] = ['HEADER', 'BODY', 'QUERY', 'PATH'];
export const fieldDataTypes: FieldDataType[] = ['STRING', 'BOOLEAN', 'INTEGER', 'DECIMAL', 'OBJECT', 'ARRAY'];
/** 字段类型与脚本阶段文案（channel.enum.dataType / scriptStage），调用时翻译。 */
export const dataTypeLabel = (type: string) => enumLabel(t, 'enum.dataType', type);
export const stageLabel = (stage: string) => enumLabel(t, 'enum.scriptStage', stage);

export interface ApiField {
  fieldLocation: FieldLocation; fieldPath: string; fieldName: string; dataType: FieldDataType; required: boolean;
  description: string | null;
}
export interface IntegrationReference { integrationCode: string; integrationName: string }
export interface StandardApiSummary {
  standardApiCode: string; standardApiName: string; requestFieldCount: number; responseFieldCount: number;
  implementedBy: IntegrationReference[]; version: number; updateTime: string;
}
export interface StandardApiDetail {
  standardApiCode: string; standardApiName: string; description: string | null; requestFields: ApiField[];
  responseFields: ApiField[]; implementations: IntegrationReference[]; version: number; createTime: string;
  updateTime: string; updatedBy: string;
}
export interface Endpoint { endpointCode: string; baseUrl: string; sandbox?: boolean }
export interface IntegrationApiSummary {
  apiCode: string; apiName: string; method: string; endpointCode: string; pathTemplate: string;
  requestScriptCount: number; responseScriptCount: number; referencedBy: string[]; version: number; updateTime: string;
}
export interface IntegrationScriptSummary {
  scriptCode: string; scriptName: string; executionStage: ScriptStage; parameterNames: string[];
  referencedApiCodes: string[]; version: number; updateTime: string;
}
export interface ImplementationSummary {
  standardApiCode: string; standardApiName: string; nodeCount: number; returnCount: number; initializerIds: string[];
  timeoutMs: number; version: number; updateTime: string;
}
export interface Mutation { objectType: string; objectCode: string; version: number }

function require(valid: unknown): asserts valid {
  if (!valid) throw new ApiError(502, 'INVALID_RESPONSE');
}
const text = (value: unknown, max: number): value is string => typeof value === 'string' && value.length <= max;
const nullableText = (value: unknown, max: number) => value === null || text(value, max);
const count = (value: unknown) => Number.isSafeInteger(value) && (value as number) >= 0;
const version = (value: unknown) => Number.isInteger(value) && (value as number) >= 1;
const instant = (value: unknown) => typeof value === 'string' && Number.isFinite(Date.parse(value));
const code = (value: unknown): value is string => typeof value === 'string' && codePattern.test(value);
const codes = (value: unknown) => Array.isArray(value) && value.every(item => typeof item === 'string');

function decodeField(value: unknown): ApiField {
  const v = value as ApiField;
  require(v && fieldLocations.includes(v.fieldLocation) && text(v.fieldPath, 512) && text(v.fieldName, 128)
    && fieldDataTypes.includes(v.dataType) && typeof v.required === 'boolean' && nullableText(v.description, 512));
  return { fieldLocation: v.fieldLocation, fieldPath: v.fieldPath, fieldName: v.fieldName, dataType: v.dataType,
    required: v.required, description: v.description };
}
export function decodeFields(value: unknown): ApiField[] {
  require(Array.isArray(value));
  return value.map(decodeField);
}
function decodeIntegrationReference(value: unknown): IntegrationReference {
  const v = value as IntegrationReference;
  require(v && code(v.integrationCode) && text(v.integrationName, 128));
  return { integrationCode: v.integrationCode, integrationName: v.integrationName };
}
export function decodeStandardApiSummary(value: unknown): StandardApiSummary {
  const v = value as StandardApiSummary;
  require(v && code(v.standardApiCode) && text(v.standardApiName, 128) && count(v.requestFieldCount)
    && count(v.responseFieldCount) && Array.isArray(v.implementedBy) && version(v.version) && instant(v.updateTime));
  return { standardApiCode: v.standardApiCode, standardApiName: v.standardApiName, requestFieldCount: v.requestFieldCount,
    responseFieldCount: v.responseFieldCount, implementedBy: v.implementedBy.map(decodeIntegrationReference),
    version: v.version, updateTime: v.updateTime };
}
export function decodeStandardApi(value: unknown, expectedCode: string): StandardApiDetail {
  const v = value as StandardApiDetail;
  require(v && v.standardApiCode === expectedCode && text(v.standardApiName, 128) && nullableText(v.description, 512)
    && Array.isArray(v.implementations) && version(v.version) && instant(v.createTime) && instant(v.updateTime)
    && typeof v.updatedBy === 'string');
  return { standardApiCode: v.standardApiCode, standardApiName: v.standardApiName, description: v.description,
    requestFields: decodeFields(v.requestFields), responseFields: decodeFields(v.responseFields),
    implementations: v.implementations.map(decodeIntegrationReference), version: v.version, createTime: v.createTime,
    updateTime: v.updateTime, updatedBy: v.updatedBy };
}
export function decodeIntegrationApiSummary(value: unknown): IntegrationApiSummary {
  const v = value as IntegrationApiSummary;
  require(v && code(v.apiCode) && text(v.apiName, 128) && text(v.method, 8) && text(v.endpointCode, 64)
    && text(v.pathTemplate, 512) && count(v.requestScriptCount) && count(v.responseScriptCount) && codes(v.referencedBy)
    && version(v.version) && instant(v.updateTime));
  return { apiCode: v.apiCode, apiName: v.apiName, method: v.method, endpointCode: v.endpointCode,
    pathTemplate: v.pathTemplate, requestScriptCount: v.requestScriptCount, responseScriptCount: v.responseScriptCount,
    referencedBy: [...v.referencedBy], version: v.version, updateTime: v.updateTime };
}
export function decodeScriptSummary(value: unknown): IntegrationScriptSummary {
  const v = value as IntegrationScriptSummary;
  require(v && code(v.scriptCode) && text(v.scriptName, 128) && (v.executionStage === 'REQUEST' || v.executionStage === 'RESPONSE')
    && codes(v.parameterNames) && codes(v.referencedApiCodes) && version(v.version) && instant(v.updateTime));
  return { scriptCode: v.scriptCode, scriptName: v.scriptName, executionStage: v.executionStage,
    parameterNames: [...v.parameterNames], referencedApiCodes: [...v.referencedApiCodes], version: v.version,
    updateTime: v.updateTime };
}
export function decodeImplementationList(value: unknown): ImplementationSummary[] {
  const v = value as { items: ImplementationSummary[] };
  require(v && Array.isArray(v.items));
  const items = v.items.map(item => {
    require(item && code(item.standardApiCode) && text(item.standardApiName, 128) && count(item.nodeCount)
      && count(item.returnCount) && codes(item.initializerIds) && count(item.timeoutMs) && version(item.version)
      && instant(item.updateTime));
    return { ...item, initializerIds: [...item.initializerIds] };
  });
  require(new Set(items.map(item => item.standardApiCode)).size === items.length);
  return items;
}

/** 分页响应：页码、条数与条目身份唯一；条目由各自解码器校验。 */
export function decodePage<T>(value: unknown, decode: (item: unknown) => T, identity: (item: T) => string): PagingResponseBody<T> {
  const v = value as PagingResponseBody<unknown>;
  require(v && Array.isArray(v.items) && Number.isSafeInteger(v.total) && v.total >= 0 && Number.isInteger(v.page)
    && v.page >= 1 && listSizes.includes(v.size));
  const items = v.items.map(decode);
  require(new Set(items.map(identity)).size === items.length);
  return { items, page: v.page, size: v.size, total: v.total };
}

/** 第二段合表后 Owner 写结果的对象类型可能改用渠道命名；两种写法都视为同一对象。 */
const objectTypeAliases: Readonly<Record<string, string>> = {
  INTEGRATION_API: 'CHANNEL_API', INTEGRATION_SCRIPT: 'CHANNEL_SCRIPT', INTEGRATION_IMPLEMENTATION: 'CHANNEL_IMPLEMENTATION',
  INTEGRATION_VERIFY_ACCOUNT: 'CHANNEL_VERIFY_ACCOUNT', INTEGRATION_VERIFY_CASE: 'CHANNEL_VERIFY_CASE',
};

/** 写结果必须对应本次对象：类型（或其渠道命名）、编码一致，版本为正整数。 */
export function decodeMutation(value: unknown, objectType: string, objectCode: string): Mutation {
  const v = value as Mutation;
  require(v && (v.objectType === objectType || v.objectType === objectTypeAliases[objectType]) && v.objectCode === objectCode && version(v.version));
  return { objectType: v.objectType, objectCode: v.objectCode, version: v.version };
}

/** BODY 字段按点分段与数组段计算缩进层级；其他位置都是顶层。 */
export function fieldDepth(field: Pick<ApiField, 'fieldLocation' | 'fieldPath'>): number {
  if (field.fieldLocation !== 'BODY') return 0;
  return field.fieldPath.split('.').length - 1;
}

/** 新建实现时可选的标准 API：全部标准 API 去掉本渠道已实现的，按编码排序。 */
export function availableStandardApis(all: StandardApiSummary[], implemented: ImplementationSummary[]): StandardApiSummary[] {
  const used = new Set(implemented.map(item => item.standardApiCode));
  return all.filter(item => !used.has(item.standardApiCode))
    .toSorted((left, right) => left.standardApiCode.localeCompare(right.standardApiCode));
}

/** 只保留非空筛选值并去除首尾空白。 */
export function cleanFilters(values: Record<string, string | undefined>): Record<string, string> {
  return Object.fromEntries(Object.entries(values).filter(([, value]) => value?.trim()).map(([key, value]) => [key, value!.trim()]));
}

/** 在当前查询串上更新或删除参数，用于页签、筛选与分页写入 URL。 */
export function replaceQuery(search: string, updates: Record<string, string | null>): string {
  const next = new URLSearchParams(search);
  for (const [key, value] of Object.entries(updates)) {
    if (value === null || value === '') next.delete(key); else next.set(key, value);
  }
  const text = next.toString();
  return text ? `?${text}` : '';
}

/** 表单字段行转为请求体：说明空白视为 null，其余原样提交，由服务端校验。 */
export function toFieldBodies(fields: ApiField[]): ApiField[] {
  return fields.map(field => ({ ...field, fieldPath: field.fieldPath.trim(), fieldName: field.fieldName.trim(),
    description: field.description?.trim() ? field.description.trim() : null }));
}

/** 字段编辑时的即时提示，与保存校验同口径；最终以服务端结果为准。 */
export interface FieldHint { index: number; column: 'fieldLocation' | 'fieldPath' | 'dataType'; message: string }

const bodySegment = /^[A-Za-z_$][A-Za-z0-9_$-]{0,63}(\[\])?$/;
const pathRules: Record<FieldLocation, RegExp> = {
  HEADER: /^[!#$%&'*+.^_`|~0-9A-Za-z-]{1,64}$/,
  QUERY: /^[A-Za-z0-9_.~-]{1,64}$/,
  PATH: /^[A-Za-z][A-Za-z0-9_]{0,63}$/,
  BODY: /^$/,
};

function validPath(field: Pick<ApiField, 'fieldLocation' | 'fieldPath'>): boolean {
  if (field.fieldLocation !== 'BODY') return pathRules[field.fieldLocation].test(field.fieldPath);
  const segments = field.fieldPath.split('.');
  return segments.length <= 16 && segments.every(segment => bodySegment.test(segment));
}

export function fieldHints(fields: ApiField[], allowed: FieldLocation[]): FieldHint[] {
  const hints: FieldHint[] = [];
  const seen = new Set<string>();
  const bodyTypes = new Map<string, FieldDataType>();
  fields.filter(field => field.fieldLocation === 'BODY').forEach(field => {
    if (!bodyTypes.has(field.fieldPath)) bodyTypes.set(field.fieldPath, field.dataType);
  });
  fields.forEach((field, index) => {
    if (!allowed.includes(field.fieldLocation)) {
      hints.push({ index, column: 'fieldLocation', message: t('field.hint.locationNotAllowed', { location: field.fieldLocation }) });
      return;
    }
    if (!validPath(field)) hints.push({ index, column: 'fieldPath', message: t('field.hint.pathInvalid') });
    if (field.fieldLocation !== 'BODY' && (field.dataType === 'OBJECT' || field.dataType === 'ARRAY')) {
      hints.push({ index, column: 'dataType', message: t('field.hint.scalarOnly') });
    }
    const identity = `${field.fieldLocation}:${field.fieldLocation === 'HEADER' ? field.fieldPath.toLowerCase() : field.fieldPath}`;
    if (seen.has(identity)) hints.push({ index, column: 'fieldPath', message: t('field.hint.pathDuplicate') });
    seen.add(identity);
    if (field.fieldLocation === 'BODY' && validPath(field)) {
      const dot = field.fieldPath.lastIndexOf('.');
      const parent = dot >= 0 ? field.fieldPath.slice(0, dot) : field.fieldPath.endsWith('[]') ? field.fieldPath : null;
      if (parent !== null) {
        const array = parent.endsWith('[]');
        const parentPath = array ? parent.slice(0, -2) : parent;
        if (bodyTypes.get(parentPath) !== (array ? 'ARRAY' : 'OBJECT')) {
          hints.push({ index, column: 'fieldPath', message: t(array ? 'field.hint.parentArray' : 'field.hint.parentObject', { parent: parentPath }) });
        }
      }
    }
  });
  return hints;
}

/** 从 422 字段路径中取出某个数组（如 requestFields）的出错下标，用于在表格中标红对应行。 */
export function errorRows(fieldErrors: readonly { field: string }[], container: string): number[] {
  const pattern = new RegExp(`^${container.replace(/[.[\]]/g, '\\$&')}\\[(\\d+)\\]`);
  return [...new Set(fieldErrors.flatMap(error => {
    const match = pattern.exec(error.field);
    return match ? [Number(match[1])] : [];
  }))].toSorted((left, right) => left - right);
}
