import {
  isId, isInstant, isInteger, isOneOf, isOptionalText, isRecord, isText, isTextList, orNull, pageDecoder, require,
} from './dataQueryCommon.ts';
import { translator } from '../i18n/index.ts';

const t = translator('data-query');

export const engineTypes = ['MYSQL', 'DORIS'] as const;
export type EngineType = typeof engineTypes[number];
export const engineLabels: Readonly<Record<EngineType, string>> = { MYSQL: 'MySQL', DORIS: 'Doris' };
export const dataSourceStatuses = ['INACTIVE', 'ACTIVE', 'DELETED'] as const;
export type DataSourceStatus = typeof dataSourceStatuses[number];
export const checkResults = ['PASSED', 'FAILED'] as const;
export type CheckResult = typeof checkResults[number];

/** 允许配置的连接参数（其余如 allowMultiQueries、allowLoadLocalInfile 由系统固定关闭）。 */
export const connectionParamKeys = ['sslMode', 'connectTimeout', 'socketTimeout', 'connectionTimeZone', 'characterEncoding'] as const;
export type ConnectionParamKey = typeof connectionParamKeys[number];
export const maxPoolSizeLimit = 10;
export const hostPattern = /^[A-Za-z0-9._:-]+$/;
export const databaseNamePattern = /^[A-Za-z0-9_$]{1,128}$/;
/** 只读账号允许的权限；其余（含角色授权）都视为违规。 */
export const allowedPrivileges = ['USAGE', 'SELECT', 'SHOW VIEW'] as const;

export interface DataSource {
  dataSourceId: string; dataSourceCode: string; dataSourceName: string; engineType: EngineType; host: string; port: number;
  databaseName: string | null; username: string; connectionParams: Record<string, string>; maxPoolSize: number; status: DataSourceStatus;
  lastCheckResult: CheckResult | null; lastCheckMessage: string | null; lastCheckTime: string | null; description: string | null;
  templateCount: number; activeTemplateCount: number; version: number; createdBy: string; updatedBy: string; createTime: string; updateTime: string;
}

export interface ConnectionTest {
  result: CheckResult; connected: boolean; privilegesPassed: boolean; grants: string[]; violations: string[]; checkedAt: string;
}

export function decodeDataSource(value: unknown): DataSource {
  require(isRecord(value));
  const v = value;
  const params = v.connectionParams ?? {};
  require(isId(v.dataSourceId) && isText(v.dataSourceCode) && isText(v.dataSourceName) && isOneOf(engineTypes, v.engineType)
    && isText(v.host) && isInteger(v.port, 1, 65535) && isOptionalText(v.databaseName) && isText(v.username)
    && isRecord(params) && Object.values(params).every(isText) && isInteger(v.maxPoolSize, 1)
    && isOneOf(dataSourceStatuses, v.status) && (v.lastCheckResult == null || isOneOf(checkResults, v.lastCheckResult))
    && isOptionalText(v.lastCheckMessage) && (v.lastCheckTime == null || isInstant(v.lastCheckTime)) && isOptionalText(v.description)
    && isInteger(v.templateCount) && isInteger(v.activeTemplateCount) && isInteger(v.version, 1)
    && isText(v.createdBy) && isText(v.updatedBy) && isInstant(v.createTime) && isInstant(v.updateTime));
  return {
    ...(v as unknown as DataSource), connectionParams: params as Record<string, string>, databaseName: orNull(v.databaseName as string | null),
    lastCheckResult: orNull(v.lastCheckResult as CheckResult | null), lastCheckMessage: orNull(v.lastCheckMessage as string | null),
    lastCheckTime: orNull(v.lastCheckTime as string | null), description: orNull(v.description as string | null),
  };
}
export const decodeDataSourcePage = pageDecoder(decodeDataSource);

export function decodeConnectionTest(value: unknown): ConnectionTest {
  require(isRecord(value) && isOneOf(checkResults, value.result) && typeof value.connected === 'boolean'
    && typeof value.privilegesPassed === 'boolean' && isTextList(value.grants ?? []) && isTextList(value.violations ?? []) && isInstant(value.checkedAt));
  return { ...(value as unknown as ConnectionTest), grants: (value.grants ?? []) as string[], violations: (value.violations ?? []) as string[] };
}

// ---------- 展示 ----------

/** 列表与详情的地址：有默认库为“主机:端口 / 库”，否则“主机:端口 · 整个实例”。 */
export function addressLabel(source: Pick<DataSource, 'host' | 'port' | 'databaseName'>): string {
  return source.databaseName ? `${source.host}:${source.port} / ${source.databaseName}` : t('dataSource.wholeInstance', { address: `${source.host}:${source.port}` });
}

/** 最后检查：未检查、通过 · 时间、失败 · 原因（无原因时显示时间）。 */
export function lastCheckLabel(source: Pick<DataSource, 'lastCheckResult' | 'lastCheckMessage' | 'lastCheckTime'>, format: (value: string | null) => string): string {
  if (!source.lastCheckResult) return t('dataSource.lastCheck.none');
  if (source.lastCheckResult === 'PASSED') return t('dataSource.lastCheck.passed', { time: format(source.lastCheckTime) });
  return t('dataSource.lastCheck.failed', { detail: source.lastCheckMessage || format(source.lastCheckTime) });
}

export interface GrantSegment { text: string; violation: boolean }

/**
 * SHOW GRANTS 一行拆成片段并标出违规权限：`GRANT 权限列表 ON …` 中不在 USAGE / SELECT / SHOW VIEW 内的权限标红；
 * 没有 ON 子句的行是角色授权，整行标红。列级权限（如 `SELECT (a)`）按权限名判断。
 */
export function grantSegments(line: string): GrantSegment[] {
  const match = /^(\s*GRANT\s+)(.+?)(\s+ON\s+[\s\S]*)$/i.exec(line);
  if (!match) return [{ text: line, violation: /^\s*GRANT\s/i.test(line) }];
  const segments: GrantSegment[] = [{ text: match[1], violation: false }];
  for (const part of splitTopLevel(match[2])) {
    if (/^,\s*$/.test(part)) { segments.push({ text: part, violation: false }); continue; }
    const name = part.replace(/\s*\(.*\)\s*$/, '').trim().toUpperCase().replace(/\s+/g, ' ');
    segments.push({ text: part, violation: !(allowedPrivileges as readonly string[]).includes(name) });
  }
  segments.push({ text: match[3], violation: false });
  return segments;
}

/** 按括号外的逗号切分权限列表，逗号与其后空白单独成段（列级权限的列名列表不切开）。 */
function splitTopLevel(text: string): string[] {
  const parts: string[] = []; let depth = 0; let start = 0;
  for (let i = 0; i < text.length; i++) {
    if (text[i] === '(') depth++;
    else if (text[i] === ')') depth = Math.max(depth - 1, 0);
    else if (text[i] === ',' && depth === 0) {
      parts.push(text.slice(start, i));
      let end = i + 1; while (end < text.length && /\s/.test(text[end])) end++;
      parts.push(text.slice(i, end)); start = end; i = end - 1;
    }
  }
  parts.push(text.slice(start));
  return parts.filter(part => part !== '');
}

// ---------- 表单 ----------

export interface ConnectionParamRow { key?: ConnectionParamKey; value?: string }
export interface DataSourceFormValues {
  dataSourceCode?: string; dataSourceName?: string; engineType?: EngineType; host?: string; port?: number; databaseName?: string;
  username?: string; password?: string; connectionParams?: ConnectionParamRow[]; maxPoolSize?: number; description?: string;
}

export function dataSourceFormValues(source: DataSource | null): DataSourceFormValues {
  if (!source) return { engineType: 'MYSQL', port: 3306, maxPoolSize: 5, connectionParams: [] };
  return {
    dataSourceCode: source.dataSourceCode, dataSourceName: source.dataSourceName, engineType: source.engineType, host: source.host,
    port: source.port, databaseName: source.databaseName ?? '', username: source.username, password: '', maxPoolSize: source.maxPoolSize,
    description: source.description ?? '',
    connectionParams: Object.entries(source.connectionParams).map(([key, value]) => ({ key: key as ConnectionParamKey, value })),
  };
}

const blankToNull = (value: string | undefined) => value?.trim() ? value.trim() : null;

/** 连接参数行 → Map；空行忽略，后写的同名键覆盖前者（表单另有重复校验）。 */
export function connectionParamMap(rows: readonly ConnectionParamRow[] | undefined): Record<string, string> {
  return Object.fromEntries((rows ?? []).filter(row => row.key && row.value?.trim()).map(row => [row.key!, row.value!.trim()]));
}

function connectionFields(values: DataSourceFormValues) {
  return {
    engineType: values.engineType, host: (values.host ?? '').trim(), port: values.port, databaseName: blankToNull(values.databaseName),
    username: (values.username ?? '').trim(), connectionParams: connectionParamMap(values.connectionParams),
  };
}

export function createDataSourceBody(values: DataSourceFormValues) {
  return {
    dataSourceCode: (values.dataSourceCode ?? '').trim(), dataSourceName: (values.dataSourceName ?? '').trim(), ...connectionFields(values),
    password: values.password ?? '', maxPoolSize: values.maxPoolSize, description: blankToNull(values.description),
  };
}

/** 修改请求体：编码在路径上；密码留空表示保留 Vault 中的原密码。 */
export function updateDataSourceBody(values: DataSourceFormValues, source: DataSource) {
  return {
    dataSourceName: (values.dataSourceName ?? '').trim(), ...connectionFields(values), password: values.password ? values.password : null,
    maxPoolSize: values.maxPoolSize, description: blankToNull(values.description), expectedVersion: source.version,
  };
}

/** 测试连接请求体：编辑时带 dataSourceId，密码留空则服务端使用已保存的密码。 */
export function connectionTestBody(values: DataSourceFormValues, source: DataSource | null) {
  return { dataSourceId: source?.dataSourceId ?? null, ...connectionFields(values), password: values.password ? values.password : null };
}

/** 影响连接检查的字段指纹；变化后上一轮检查结果失效。 */
export function connectionFingerprint(values: DataSourceFormValues): string {
  return JSON.stringify({ ...connectionFields(values), password: values.password ?? '' });
}

export interface SaveGate { allowed: boolean; reason: string | null }

/**
 * 保存门槛：启用中的数据源修改后，必须用当前表单的连接配置测试通过才能保存；新建与停用状态不要求。
 * 服务端仍会在保存时重新检查。
 */
export function saveGate(source: DataSource | null, test: { fingerprint: string; result: ConnectionTest } | null, fingerprint: string): SaveGate {
  if (!source || source.status !== 'ACTIVE') return { allowed: true, reason: null };
  if (!test || test.fingerprint !== fingerprint) return { allowed: false, reason: t('dataSource.saveGate.testRequired') };
  if (test.result.result !== 'PASSED') return { allowed: false, reason: t('dataSource.saveGate.testFailed') };
  return { allowed: true, reason: null };
}

/** 状态动作：停用可启用（服务端先做连接检查），启用可停用。 */
export function statusAction(source: Pick<DataSource, 'status'>): { target: 'ACTIVE' | 'INACTIVE'; verb: string; notice: string } | null {
  if (source.status === 'ACTIVE') return { target: 'INACTIVE', verb: t('dataSource.status.disable'), notice: t('dataSource.status.disableNotice') };
  if (source.status === 'INACTIVE') return { target: 'ACTIVE', verb: t('dataSource.status.enable'), notice: t('dataSource.status.enableNotice') };
  return null;
}

/**
 * 模板编辑器可选的数据源：只列启用中的；模板已保存版本或当前草稿正在使用的停用数据源保留并标注，便于看清现状后改选。
 */
export function templateSourceOptions(sources: readonly DataSource[], inUse: readonly (string | null | undefined)[]): { value: string; label: string }[] {
  return sources.filter(item => item.status === 'ACTIVE' || inUse.includes(item.dataSourceId)).map(item => ({
    value: item.dataSourceId, label: item.status === 'ACTIVE' ? `${item.dataSourceCode} · ${engineLabels[item.engineType]}`
      : t('dataSource.optionInactive', { code: item.dataSourceCode, engine: engineLabels[item.engineType] }),
  }));
}

/** 删除前置条件：必须停用且没有启用中的模板引用。 */
export function deletionBlock(source: Pick<DataSource, 'status' | 'activeTemplateCount'>): string | null {
  if (source.status !== 'INACTIVE') return t('dataSource.deletion.disableFirst');
  if (source.activeTemplateCount > 0) return t('dataSource.deletion.referenced', { count: source.activeTemplateCount });
  return null;
}
