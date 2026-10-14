import { ApiError } from '../../api/session.ts';
import { codePattern, integrationPath, integrationRoute } from '../externalApiModel.ts';

/** 沙箱验证（沙箱验证技术方案第 4、5 节）：接口路径、响应解码、状态文案与编辑态转换。 */

export const verifyPath = (code: string) => `${integrationPath}/${code}`;
export const verifyRoute = (code: string) => `${integrationRoute}/${code}`;

export type VerifyCaseStatus = 'NOT_RUN' | 'RUNNING' | 'PASSED' | 'FAILED' | 'STALE';
export type VerifyRunStatus = 'RUNNING' | 'PASSED' | 'FAILED' | 'INTERRUPTED';
export type VerifyStepStatus = 'PASSED' | 'FAILED' | 'SKIPPED';
export type Tone = 'neutral' | 'running' | 'success' | 'error' | 'warning';

export const caseStatusLabels: Record<VerifyCaseStatus, { label: string; tone: Tone }> = {
  NOT_RUN: { label: '未运行', tone: 'neutral' },
  RUNNING: { label: '运行中', tone: 'running' },
  PASSED: { label: '通过', tone: 'success' },
  FAILED: { label: '未通过', tone: 'error' },
  STALE: { label: '需重新验证', tone: 'warning' },
};
export const runStatusLabels: Record<VerifyRunStatus, { label: string; tone: Tone }> = {
  RUNNING: { label: '运行中', tone: 'running' },
  PASSED: { label: '通过', tone: 'success' },
  FAILED: { label: '未通过', tone: 'error' },
  INTERRUPTED: { label: '已中断', tone: 'warning' },
};
export const stepStatusLabels: Record<VerifyStepStatus, { label: string; tone: Tone }> = {
  PASSED: { label: '通过', tone: 'success' },
  FAILED: { label: '未通过', tone: 'error' },
  SKIPPED: { label: '已跳过', tone: 'neutral' },
};

export interface VerifyAccountSummary {
  accountCode: string; accountName: string; channelCode: string; channelAccountId: string; entityNo: string;
  version: number; updateTime: string;
}
export interface VerifyAccountDetail extends VerifyAccountSummary {
  parameters: Record<string, string>; credentials: Record<string, string>; referencedCaseCodes: string[];
  createTime: string; updatedBy: string;
}
export interface VerifyRunBrief { runCode: string; runStatus: VerifyRunStatus; startTime: string; finishTime: string | null }
export interface VerifyCaseSummary {
  caseCode: string; caseName: string; accountCode: string | null; standardApiCodes: string[]; stepCount: number;
  verifyStatus: VerifyCaseStatus; latestRun: VerifyRunBrief | null; version: number; updateTime: string;
}
export interface VerifyCaseDetail {
  caseCode: string; caseName: string; description: string | null; accountCode: string | null; steps: unknown[];
  verifyStatus: VerifyCaseStatus; latestRun: VerifyRunBrief | null; version: number; createTime: string; updateTime: string;
  updatedBy: string;
}
export interface VerifyRunSummary {
  runCode: string; caseCode: string; caseName: string; runStatus: VerifyRunStatus; failureSummary: string | null;
  startedBy: string; startTime: string; finishTime: string | null;
}
export interface VerifyAssertion { target: string; match: string; expected: unknown; actual: unknown; passed: boolean }
export interface VerifyRunStep {
  stepNo: number; stepKey: string; standardApiCode: string; stepStatus: VerifyStepStatus; resolvedInput: unknown;
  result: Record<string, unknown> | null; assertions: VerifyAssertion[]; trace: Record<string, unknown> | null;
  elapsedMs: number | null;
}
export interface VerifyRunEndpoint { endpointCode: string; baseUrl: string; sandbox: boolean }
export interface VerifyRunDetail extends VerifyRunSummary {
  caseSnapshot: Record<string, unknown>; configVersions: Record<string, unknown> | null;
  endpoints: VerifyRunEndpoint[]; stale: boolean; steps: VerifyRunStep[];
}

function require(valid: unknown): asserts valid {
  if (!valid) throw new ApiError(502, 'INVALID_RESPONSE');
}
const text = (value: unknown, max: number): value is string => typeof value === 'string' && value.length <= max;
const nullableText = (value: unknown, max: number) => value === null || value === undefined || text(value, max);
const version = (value: unknown) => Number.isInteger(value) && (value as number) >= 1;
const instant = (value: unknown) => typeof value === 'string' && Number.isFinite(Date.parse(value));
const nullableInstant = (value: unknown) => value === null || value === undefined || instant(value);
const code = (value: unknown): value is string => typeof value === 'string' && codePattern.test(value);
const runCode = (value: unknown): value is string => typeof value === 'string' && /^[1-9][0-9]{0,18}$/.test(value);
const strings = (value: unknown): value is string[] => Array.isArray(value) && value.every(item => typeof item === 'string');
const object = (value: unknown): value is Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value);
const stringMap = (value: unknown): value is Record<string, string> => object(value) && Object.values(value).every(item => typeof item === 'string');

export function decodeAccountSummary(value: unknown): VerifyAccountSummary {
  const v = value as VerifyAccountSummary;
  require(v && code(v.accountCode) && text(v.accountName, 128) && text(v.channelCode, 64) && text(v.channelAccountId, 64)
    && text(v.entityNo, 64) && version(v.version) && instant(v.updateTime));
  return { accountCode: v.accountCode, accountName: v.accountName, channelCode: v.channelCode,
    channelAccountId: v.channelAccountId, entityNo: v.entityNo, version: v.version, updateTime: v.updateTime };
}

export function decodeAccounts(value: unknown): VerifyAccountSummary[] {
  require(Array.isArray(value));
  return value.map(decodeAccountSummary);
}

export function decodeAccount(value: unknown, expectedCode: string): VerifyAccountDetail {
  const v = value as VerifyAccountDetail;
  require(v && v.accountCode === expectedCode && stringMap(v.parameters) && stringMap(v.credentials)
    && strings(v.referencedCaseCodes) && instant(v.createTime) && typeof v.updatedBy === 'string');
  return { ...decodeAccountSummary(v), parameters: { ...v.parameters }, credentials: { ...v.credentials },
    referencedCaseCodes: [...v.referencedCaseCodes], createTime: v.createTime, updatedBy: v.updatedBy };
}

function decodeBrief(value: unknown): VerifyRunBrief | null {
  if (value === null || value === undefined) return null;
  const v = value as VerifyRunBrief;
  require(runCode(v.runCode) && v.runStatus in runStatusLabels && instant(v.startTime) && nullableInstant(v.finishTime));
  return { runCode: v.runCode, runStatus: v.runStatus, startTime: v.startTime, finishTime: v.finishTime ?? null };
}

export function decodeCaseSummary(value: unknown): VerifyCaseSummary {
  const v = value as VerifyCaseSummary;
  require(v && code(v.caseCode) && text(v.caseName, 128) && (v.accountCode === null || code(v.accountCode))
    && strings(v.standardApiCodes) && Number.isInteger(v.stepCount) && v.verifyStatus in caseStatusLabels
    && version(v.version) && instant(v.updateTime));
  return { caseCode: v.caseCode, caseName: v.caseName, accountCode: v.accountCode, standardApiCodes: [...v.standardApiCodes],
    stepCount: v.stepCount, verifyStatus: v.verifyStatus, latestRun: decodeBrief(v.latestRun), version: v.version,
    updateTime: v.updateTime };
}

export function decodeCase(value: unknown, expectedCode: string): VerifyCaseDetail {
  const v = value as VerifyCaseDetail;
  require(v && v.caseCode === expectedCode && text(v.caseName, 128) && nullableText(v.description, 512)
    && (v.accountCode === null || code(v.accountCode)) && Array.isArray(v.steps) && v.verifyStatus in caseStatusLabels
    && version(v.version) && instant(v.createTime) && instant(v.updateTime) && typeof v.updatedBy === 'string');
  return { caseCode: v.caseCode, caseName: v.caseName, description: v.description ?? null, accountCode: v.accountCode,
    steps: v.steps, verifyStatus: v.verifyStatus, latestRun: decodeBrief(v.latestRun), version: v.version,
    createTime: v.createTime, updateTime: v.updateTime, updatedBy: v.updatedBy };
}

export function decodeRunSummary(value: unknown): VerifyRunSummary {
  const v = value as VerifyRunSummary;
  require(v && runCode(v.runCode) && code(v.caseCode) && text(v.caseName, 128) && v.runStatus in runStatusLabels
    && nullableText(v.failureSummary, 512) && typeof v.startedBy === 'string' && instant(v.startTime)
    && nullableInstant(v.finishTime));
  return { runCode: v.runCode, caseCode: v.caseCode, caseName: v.caseName, runStatus: v.runStatus,
    failureSummary: v.failureSummary ?? null, startedBy: v.startedBy, startTime: v.startTime, finishTime: v.finishTime ?? null };
}

export function decodeRunStart(value: unknown): VerifyRunBrief {
  const v = value as { runCode: string; runStatus: VerifyRunStatus };
  require(v && runCode(v.runCode) && v.runStatus === 'RUNNING');
  return { runCode: v.runCode, runStatus: v.runStatus, startTime: new Date().toISOString(), finishTime: null };
}

export function decodeRun(value: unknown, expectedCode: string): VerifyRunDetail {
  const v = value as VerifyRunDetail & { caseName?: string };
  require(v && v.runCode === expectedCode && object(v.caseSnapshot) && (v.configVersions === null || object(v.configVersions))
    && (v.endpoints === null || Array.isArray(v.endpoints)) && typeof v.stale === 'boolean' && Array.isArray(v.steps));
  const summary = decodeRunSummary({ ...v, caseName: typeof v.caseSnapshot.caseName === 'string' ? v.caseSnapshot.caseName : v.caseCode });
  const endpoints = (v.endpoints ?? []).map(item => {
    require(object(item) && text(item.endpointCode, 64) && text(item.baseUrl, 512) && typeof item.sandbox === 'boolean');
    return { endpointCode: item.endpointCode, baseUrl: item.baseUrl, sandbox: item.sandbox };
  });
  const steps = v.steps.map(step => {
    require(object(step) && Number.isInteger(step.stepNo) && typeof step.stepKey === 'string'
      && code(step.standardApiCode) && (step.stepStatus as string) in stepStatusLabels
      && (step.result === null || step.result === undefined || object(step.result))
      && (step.assertions === null || step.assertions === undefined || Array.isArray(step.assertions))
      && (step.trace === null || step.trace === undefined || object(step.trace)));
    return { stepNo: step.stepNo, stepKey: step.stepKey, standardApiCode: step.standardApiCode, stepStatus: step.stepStatus,
      resolvedInput: step.resolvedInput ?? null, result: (step.result as Record<string, unknown>) ?? null,
      assertions: ((step.assertions as VerifyAssertion[] | null) ?? []).map(item => ({ ...item })),
      trace: (step.trace as Record<string, unknown>) ?? null, elapsedMs: step.elapsedMs ?? null } as VerifyRunStep;
  });
  return { ...summary, caseSnapshot: v.caseSnapshot, configVersions: v.configVersions, endpoints, stale: v.stale, steps };
}

/** 账号参数与凭据的编辑行。 */
export interface EntryRow { name: string; value: string }

export const toRows = (entries: Record<string, string>): EntryRow[] =>
  Object.entries(entries).map(([name, value]) => ({ name, value }));

/** 编辑行转回对象：去掉首尾空白，跳过名称与值都为空的行；名称重复时保留后者。 */
export function toEntries(rows: EntryRow[]): Record<string, string> {
  const result: Record<string, string> = {};
  for (const row of rows) {
    const name = (row.name ?? '').trim(); const value = row.value ?? '';
    if (!name && !value.trim()) continue;
    result[name] = value.trim();
  }
  return result;
}

/** 重复的参数名（按出现顺序）。 */
export function duplicateNames(rows: EntryRow[]): string[] {
  const seen = new Set<string>(); const duplicates = new Set<string>();
  for (const row of rows) {
    const name = (row.name ?? '').trim();
    if (!name) continue;
    if (seen.has(name)) duplicates.add(name);
    seen.add(name);
  }
  return [...duplicates];
}

export const matches = ['EQUALS', 'EXISTS', 'ABSENT', 'REGEX', 'ONE_OF'] as const;
export type Match = typeof matches[number];
export const matchLabels: Record<Match, string> = {
  EQUALS: '等于', EXISTS: '存在', ABSENT: '不存在', REGEX: '正则匹配', ONE_OF: '属于其一',
};
export const executeStatuses = ['SUCCESS', 'FAIL', 'ERROR'] as const;

/** 输出期望的编辑行：期望值以文本编辑，EQUALS / ONE_OF 按 JSON 解析（解析失败的 EQUALS 视为字符串）。 */
export interface OutputRow { path: string; match: Match; value: string }

/** 步骤的编辑态。 */
export interface StepDraft {
  id: string; standardApiCode: string; input: string;
  expect: 'EXECUTE' | 'PROBLEM'; executeStatus: string; returnId: string; outputs: OutputRow[];
  problemStatus: string; problemCode: string;
}

export const emptyStep = (index: number): StepDraft => ({
  id: `step${index + 1}`, standardApiCode: '', input: '{\n  \n}', expect: 'EXECUTE', executeStatus: 'SUCCESS', returnId: '',
  outputs: [], problemStatus: '422', problemCode: 'VALIDATION_ERROR',
});

const valueText = (value: unknown) => value === undefined ? '' : typeof value === 'string' ? value : JSON.stringify(value);

/** 已保存的步骤转为编辑态；未知结构原样带入，由校验接口报告。 */
export function toStepDrafts(steps: unknown[]): StepDraft[] {
  return steps.map((raw, index) => {
    const step = object(raw) ? raw : {};
    const expect = object(step.expect) ? step.expect : {};
    const problem = object(expect.problem) ? expect.problem : null;
    const outputs = Array.isArray(expect.outputs) ? expect.outputs : [];
    return {
      id: typeof step.id === 'string' ? step.id : `step${index + 1}`,
      standardApiCode: typeof step.standardApiCode === 'string' ? step.standardApiCode : '',
      input: JSON.stringify(step.input ?? {}, null, 2),
      expect: problem ? 'PROBLEM' : 'EXECUTE',
      executeStatus: typeof expect.executeStatus === 'string' ? expect.executeStatus : 'SUCCESS',
      returnId: typeof expect.returnId === 'string' ? expect.returnId : '',
      outputs: outputs.filter(object).map(item => ({ path: String(item.path ?? ''),
        match: (matches as readonly string[]).includes(item.match as string) ? item.match as Match : 'EQUALS',
        value: item.match === 'REGEX' ? String(item.value ?? '') : valueText(item.value) })),
      problemStatus: problem && problem.status !== undefined ? String(problem.status) : '422',
      problemCode: problem && typeof problem.code === 'string' ? problem.code : '',
    };
  });
}

/** 编辑态中不能转换的位置。 */
export interface DraftProblem { step: number; field: 'input' | 'problemStatus' | 'outputs'; message: string }

/** 期望值文本转为 JSON 值。 */
function parseValue(row: OutputRow): unknown {
  if (row.match === 'EXISTS' || row.match === 'ABSENT') return undefined;
  if (row.match === 'REGEX') return row.value;
  if (row.match === 'ONE_OF') {
    try {
      const parsed: unknown = JSON.parse(row.value);
      if (Array.isArray(parsed)) return parsed;
    } catch { /* 按逗号分隔 */ }
    return row.value.split(',').map(item => item.trim()).filter(Boolean);
  }
  try { return JSON.parse(row.value); } catch { return row.value; }
}

/** 编辑态转为保存的步骤；输入不是 JSON 对象或问题状态不是整数时返回问题而不是结果。 */
export function toSteps(drafts: StepDraft[]): { steps: unknown[]; problems: DraftProblem[] } {
  const problems: DraftProblem[] = [];
  const steps = drafts.map((draft, index) => {
    let input: unknown = null;
    try {
      input = JSON.parse(draft.input);
      if (!object(input)) problems.push({ step: index, field: 'input', message: '输入必须是 JSON 对象' });
    } catch {
      problems.push({ step: index, field: 'input', message: '输入不是合法 JSON' });
    }
    let expect: Record<string, unknown>;
    if (draft.expect === 'PROBLEM') {
      const status = Number(draft.problemStatus);
      if (!Number.isInteger(status)) problems.push({ step: index, field: 'problemStatus', message: '问题状态必须是整数' });
      expect = { problem: { status, ...(draft.problemCode.trim() ? { code: draft.problemCode.trim() } : {}) } };
    } else {
      expect = { executeStatus: draft.executeStatus,
        ...(draft.returnId.trim() ? { returnId: draft.returnId.trim() } : {}),
        ...(draft.outputs.length ? { outputs: draft.outputs.map(row => {
          const value = parseValue(row);
          return { path: row.path.trim(), match: row.match, ...(value === undefined ? {} : { value }) };
        }) } : {}) };
    }
    return { id: draft.id.trim(), standardApiCode: draft.standardApiCode, input, expect };
  });
  return { steps, problems };
}

/** 校验问题位置 steps[i]... 对应的步骤序号；不属于某一步时为 null。 */
export function issueStep(location: string): number | null {
  const match = /^steps\[(\d+)]/.exec(location);
  return match ? Number(match[1]) : null;
}

/** 输入模板可用的变量，供编辑页提示。 */
export const templateVariables = [
  { name: '${run.code}', description: '本次运行编号（每次不同，适合拼接订单号）' },
  { name: '${run.time}', description: '运行开始时间（UTC ISO-8601）' },
  { name: '${random.digits(n)}', description: 'n 位随机数字，n 为 1～18' },
  { name: '${account.identity}', description: '验证账号身份对象，只能整串使用（channelCode / channelAccountId / entityNo）' },
  { name: '${steps.<步骤ID>.input.<路径>}', description: '前序步骤的实际输入' },
  { name: '${steps.<步骤ID>.outputs.<路径>}', description: '前序步骤的实际输出' },
];
