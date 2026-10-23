import { ApiError } from '../api/session.ts';
import { decodeFields } from './externalApiModel.ts';
import type { ApiField, FieldDataType, ScriptStage } from './externalApiModel.ts';
import { translator } from '../i18n/index.ts';

const t = translator('channel');

/** 取值绑定：PATH 读路径，EXPRESSION 为 Aviator 表达式（第 1 步只做结构与引用校验），FUNCTION 调注册纯函数。 */
export type Binding =
  | { kind: 'PATH'; path: string }
  | { kind: 'EXPRESSION'; expression: string }
  | { kind: 'FUNCTION'; function: string; args: Record<string, Binding>; result: string };
export type BindingKind = Binding['kind'];

export type ParamType = FieldDataType | 'PROTECTED_HANDLE';
export const paramTypes: ParamType[] = ['STRING', 'BOOLEAN', 'INTEGER', 'DECIMAL', 'OBJECT', 'ARRAY', 'PROTECTED_HANDLE'];
export const methods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'];
export const requestEncodings = ['JSON', 'FORM_URLENCODED', 'NONE'];
export const responseDecodings = ['JSON', 'NONE'];
/** 渠道报文中 DECIMAL 的 JSON 形式：STRING 为十进制字符串（默认），NUMBER 为 JSON 数字。 */
export const decimalEncodings = ['STRING', 'NUMBER'];

export interface ParamDeclaration { name: string; dataType: ParamType; required: boolean; description?: string | null }
export interface FixedValue { key: string; expression: string }
export interface Transport {
  protocol: 'HTTP'; method: string; endpointCode: string; pathTemplate: string; connectTimeoutMs: number; responseTimeoutMs: number;
}
export interface ScriptUse { id: string; scriptCode: string; stage: ScriptStage; params: Record<string, Binding> }
export interface StatusRule { condition: string; executeStatus: 'SUCCESS' | 'FAIL' }
export interface OutputDeclaration { name: string; dataType: FieldDataType; required: boolean; value: Binding }

/** 原子 API 定义的编辑态：固定值转为有序行，脚本按阶段拆成两组。 */
export interface DefinitionDraft {
  params: ParamDeclaration[];
  fixedValues: FixedValue[];
  transport: Transport;
  requestEncoding: string;
  responseDecoding: string;
  decimalEncoding: string;
  /** 卡字段键 BODY:<path>：只能由卡值句柄赋值，执行轨迹中遮蔽。 */
  cardFields: string[];
  /** 凭据字段键 BODY:<path>：只能由渠道账号凭据（受控句柄）赋值，执行轨迹中遮蔽。 */
  credentialFields: string[];
  requestScripts: ScriptUse[];
  responseScripts: ScriptUse[];
  statusRules: StatusRule[];
  outputs: { SUCCESS: OutputDeclaration[]; FAIL: OutputDeclaration[] };
}

export interface IntegrationApiDetail {
  apiCode: string; apiName: string; description: string | null; requestFields: ApiField[]; responseFields: ApiField[];
  definition: unknown; referencedBy: { standardApiCode: string; nodeIds: string[] }[]; version: number;
  createTime: string; updateTime: string; updatedBy: string;
}

function require(valid: unknown): asserts valid {
  if (!valid) throw new ApiError(502, 'INVALID_RESPONSE');
}

export function decodeIntegrationApi(value: unknown, expectedCode: string): IntegrationApiDetail {
  const v = value as IntegrationApiDetail;
  require(v && v.apiCode === expectedCode && typeof v.apiName === 'string' && (v.description === null || typeof v.description === 'string')
    && v.definition !== null && typeof v.definition === 'object' && Array.isArray(v.referencedBy) && Number.isInteger(v.version)
    && v.version >= 1 && typeof v.updateTime === 'string');
  const referencedBy = v.referencedBy.map(item => {
    require(item && typeof item.standardApiCode === 'string' && Array.isArray(item.nodeIds));
    return { standardApiCode: item.standardApiCode, nodeIds: [...item.nodeIds] };
  });
  return { apiCode: v.apiCode, apiName: v.apiName, description: v.description, requestFields: decodeFields(v.requestFields),
    responseFields: decodeFields(v.responseFields), definition: v.definition, referencedBy, version: v.version,
    createTime: v.createTime, updateTime: v.updateTime, updatedBy: v.updatedBy };
}

/** 新建原子 API 的默认定义：HTTP POST、JSON 编解码、默认超时，其余为空。 */
export function emptyDraft(endpointCode = ''): DefinitionDraft {
  return { params: [], fixedValues: [], requestEncoding: 'JSON', responseDecoding: 'JSON', decimalEncoding: 'STRING', cardFields: [], credentialFields: [], requestScripts: [], responseScripts: [],
    transport: { protocol: 'HTTP', method: 'POST', endpointCode, pathTemplate: '/', connectTimeoutMs: 2000, responseTimeoutMs: 10000 },
    statusRules: [], outputs: { SUCCESS: [], FAIL: [] } };
}

const record = (value: unknown): Record<string, unknown> =>
  value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
const array = (value: unknown): unknown[] => Array.isArray(value) ? value : [];

/** 已保存的定义 JSON 转为编辑态；缺失部分取默认值，不修复非法内容（交给服务端校验）。 */
export function toDraft(definition: unknown): DefinitionDraft {
  const d = record(definition); const base = emptyDraft(); const transport = record(d.transport);
  const uses = array(d.scripts).map(item => item as ScriptUse);
  const outputs = record(d.outputs);
  return {
    params: array(d.params) as ParamDeclaration[],
    fixedValues: Object.entries(record(d.fixedValues)).map(([key, value]) => ({ key, expression: String(record(value).expression ?? '') })),
    transport: { ...base.transport, ...transport } as Transport,
    requestEncoding: typeof d.requestEncoding === 'string' ? d.requestEncoding : base.requestEncoding,
    responseDecoding: typeof d.responseDecoding === 'string' ? d.responseDecoding : base.responseDecoding,
    decimalEncoding: typeof d.decimalEncoding === 'string' ? d.decimalEncoding : base.decimalEncoding,
    cardFields: array(d.cardFields).filter((key): key is string => typeof key === 'string'),
    credentialFields: array(d.credentialFields).filter((key): key is string => typeof key === 'string'),
    requestScripts: uses.filter(use => use.stage === 'REQUEST'),
    responseScripts: uses.filter(use => use.stage === 'RESPONSE'),
    statusRules: array(d.statusRules) as StatusRule[],
    outputs: { SUCCESS: array(outputs.SUCCESS) as OutputDeclaration[], FAIL: array(outputs.FAIL) as OutputDeclaration[] },
  };
}

/** 编辑态转回定义 JSON：键顺序与合同一致，REQUEST 脚本在前；参数说明为空时省略，decimalEncoding 为默认 STRING 时省略，没有卡字段、凭据字段时省略 cardFields、credentialFields。 */
export function toDefinition(draft: DefinitionDraft): Record<string, unknown> {
  return {
    params: draft.params.map(param => ({ name: param.name.trim(), dataType: param.dataType, required: param.required,
      ...(param.description?.trim() ? { description: param.description.trim() } : {}) })),
    fixedValues: Object.fromEntries(draft.fixedValues.map(item => [item.key, { kind: 'EXPRESSION', expression: item.expression }])),
    transport: { ...draft.transport },
    requestEncoding: draft.requestEncoding,
    responseDecoding: draft.responseDecoding,
    ...(draft.decimalEncoding !== 'STRING' ? { decimalEncoding: draft.decimalEncoding } : {}),
    ...(draft.cardFields.length ? { cardFields: [...draft.cardFields] } : {}),
    ...(draft.credentialFields.length ? { credentialFields: [...draft.credentialFields] } : {}),
    scripts: [...draft.requestScripts.map(use => ({ ...use, stage: 'REQUEST' })), ...draft.responseScripts.map(use => ({ ...use, stage: 'RESPONSE' }))],
    statusRules: draft.statusRules.map(rule => ({ condition: rule.condition, executeStatus: rule.executeStatus })),
    outputs: { SUCCESS: draft.outputs.SUCCESS, FAIL: draft.outputs.FAIL },
  };
}

/** 路径模板中的 {变量} 名称，按出现顺序去重。 */
export function pathVariables(template: string): string[] {
  return [...new Set([...template.matchAll(/\{([^{}]*)\}/g)].map(match => match[1]))];
}

/** 路径变量与 PATH 字段双向对应的提示。 */
export function pathVariableHints(template: string, requestFields: ApiField[]): string[] {
  const variables = pathVariables(template);
  const pathFields = requestFields.filter(field => field.fieldLocation === 'PATH').map(field => field.fieldPath);
  return [
    ...variables.filter(name => !pathFields.includes(name)).map(name => t('api.hint.pathVariableUnmatched', { name })),
    ...pathFields.filter(name => !variables.includes(name)).map(name => t('api.hint.pathFieldUnused', { name })),
  ];
}

/** 请求字段在 fixedValues 与流程 request 中使用的键：LOCATION:path，HEADER 名称小写比较。 */
export function fieldKey(field: Pick<ApiField, 'fieldLocation' | 'fieldPath'>): string {
  return `${field.fieldLocation}:${field.fieldPath}`;
}
export function normalizeKey(key: string): string {
  return key.startsWith('HEADER:') ? `HEADER:${key.slice(7).toLowerCase()}` : key;
}
export function fixedKeySet(fixedValues: FixedValue[]): Set<string> {
  return new Set(fixedValues.map(item => normalizeKey(item.key)));
}

export type EditorTab = 'interface' | 'request' | 'response' | 'params' | 'scripts' | 'rules' | 'outputs';

/** 校验问题位置对应的编辑页签；无法识别的位置归到“接口”。 */
export function issueTab(location: string): EditorTab {
  if (location.startsWith('requestFields')) return 'request';
  if (location.startsWith('responseFields')) return 'response';
  if (location.startsWith('definition.params') || location.startsWith('definition.fixedValues')) return 'params';
  if (location.startsWith('definition.scripts')) return 'scripts';
  if (location.startsWith('definition.statusRules')) return 'rules';
  if (location.startsWith('definition.outputs')) return 'outputs';
  return 'interface';
}

/** 输出与状态规则可读的响应路径提示：http.responseBody.<BODY 字段>、http.responseHeaders.<HEADER 字段>、调用参数（不含受控句柄）。 */
export function responseReadPaths(responseFields: ApiField[], params: ParamDeclaration[]): string[] {
  return [
    'http.responseStatus',
    ...responseFields.filter(field => field.fieldLocation === 'BODY' && !field.fieldPath.includes('[]'))
      .map(field => `http.responseBody.${field.fieldPath}`),
    ...responseFields.filter(field => field.fieldLocation === 'HEADER').map(field => `http.responseHeaders.${field.fieldPath}`),
    ...params.filter(param => param.dataType !== 'PROTECTED_HANDLE' && param.name.trim()).map(param => `params.${param.name.trim()}`),
  ];
}

/** 新增脚本引用的默认 id：scriptN，避开已用 id。 */
export function nextScriptUseId(uses: ScriptUse[]): string {
  const used = new Set(uses.map(use => use.id));
  for (let index = 1; ; index++) if (!used.has(`script${index}`)) return `script${index}`;
}

/** 数组内移动一项，越界时原样返回。 */
export function moveItem<T>(items: T[], index: number, offset: number): T[] {
  const target = index + offset;
  if (target < 0 || target >= items.length) return items;
  const next = [...items]; const [item] = next.splice(index, 1); next.splice(target, 0, item);
  return next;
}
