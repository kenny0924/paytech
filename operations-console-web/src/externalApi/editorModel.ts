import { ApiError } from '../api/session.ts';
import type { ScriptStage } from './externalApiModel.ts';
import type { ParamType } from './integrationApiModel.ts';

export interface RegisteredFunction {
  functionCode: string; kind: string; params: { name: string; dataType: string; required: boolean }[];
  returns: string; returnRoots: Record<string, string>; stages: string[];
}
export interface Capabilities { enums: Record<string, string[]>; limits: Record<string, number>; functions: RegisteredFunction[] }
export interface ValidationIssue { severity: 'ERROR' | 'WARNING'; code: string; location: string; message: string }
export interface ValidationResult { valid: boolean; issues: ValidationIssue[] }
export interface ScriptParameter { name: string; dataType: ParamType; required: boolean }
export interface ScriptDetail {
  scriptCode: string; scriptName: string; executionStage: ScriptStage; parameterDefinition: ScriptParameter[];
  sourceCode: string; description: string | null; referencedApiCodes: string[]; version: number; updateTime: string;
}

function require(valid: unknown): asserts valid {
  if (!valid) throw new ApiError(502, 'INVALID_RESPONSE');
}
const strings = (value: unknown) => Array.isArray(value) && value.every(item => typeof item === 'string');

export function decodeCapabilities(value: unknown): Capabilities {
  const v = value as Capabilities;
  require(v && v.enums && typeof v.enums === 'object' && v.limits && typeof v.limits === 'object' && Array.isArray(v.functions));
  require(Object.values(v.enums).every(strings) && Object.values(v.limits).every(limit => Number.isSafeInteger(limit)));
  const functions = v.functions.map(item => {
    require(item && typeof item.functionCode === 'string' && typeof item.kind === 'string' && Array.isArray(item.params)
      && typeof item.returns === 'string' && item.returnRoots && typeof item.returnRoots === 'object' && strings(item.stages));
    return { functionCode: item.functionCode, kind: item.kind, params: item.params.map(param => ({ ...param })), returns: item.returns,
      returnRoots: { ...item.returnRoots }, stages: [...item.stages] };
  });
  return { enums: { ...v.enums }, limits: { ...v.limits }, functions };
}

/** 校验结果：valid 必须与是否存在 ERROR 一致。 */
export function decodeValidation(value: unknown): ValidationResult {
  const v = value as ValidationResult;
  require(v && typeof v.valid === 'boolean' && Array.isArray(v.issues));
  const issues = v.issues.map(issue => {
    require(issue && (issue.severity === 'ERROR' || issue.severity === 'WARNING') && typeof issue.code === 'string'
      && typeof issue.location === 'string' && typeof issue.message === 'string');
    return { severity: issue.severity, code: issue.code, location: issue.location, message: issue.message };
  });
  require(v.valid === !issues.some(issue => issue.severity === 'ERROR'));
  return { valid: v.valid, issues };
}

export function decodeScript(value: unknown, expectedCode: string): ScriptDetail {
  const v = value as ScriptDetail;
  require(v && v.scriptCode === expectedCode && typeof v.scriptName === 'string'
    && (v.executionStage === 'REQUEST' || v.executionStage === 'RESPONSE') && Array.isArray(v.parameterDefinition)
    && typeof v.sourceCode === 'string' && (v.description === null || typeof v.description === 'string')
    && strings(v.referencedApiCodes) && Number.isInteger(v.version) && v.version >= 1 && typeof v.updateTime === 'string');
  const parameterDefinition = v.parameterDefinition.map(param => {
    require(param && typeof param.name === 'string' && typeof param.dataType === 'string' && typeof param.required === 'boolean');
    return { name: param.name, dataType: param.dataType, required: param.required };
  });
  return { scriptCode: v.scriptCode, scriptName: v.scriptName, executionStage: v.executionStage, parameterDefinition,
    sourceCode: v.sourceCode, description: v.description, referencedApiCodes: [...v.referencedApiCodes], version: v.version,
    updateTime: v.updateTime };
}

/** 脚本按阶段可读写的对象说明，供编辑页右侧对象树展示。 */
export function scriptObjects(stage: ScriptStage): { path: string; access: '读写' | '只读'; note: string }[] {
  return stage === 'REQUEST'
    ? [{ path: 'http.requestHeaders', access: '读写', note: '组装后的请求头' },
      { path: 'http.requestBody', access: '读写', note: '组装后、编码前的请求体' },
      { path: 'params.<名称>', access: '只读', note: '脚本参数' }]
    : [{ path: 'http.responseStatus', access: '只读', note: 'HTTP 状态码' },
      { path: 'http.responseHeaders', access: '读写', note: '响应头工作副本' },
      { path: 'http.responseBody', access: '读写', note: '解码后的响应体工作副本' },
      { path: 'http.requestHeaders / http.requestBody', access: '只读', note: '已发送的请求' },
      { path: 'params.<名称>', access: '只读', note: '脚本参数' }];
}
