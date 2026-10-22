import { ApiError, session } from '../api/session.ts';

/** Operations BFF 存储管理路径；实例、桶与写入规则的唯一 Owner 是 Platform。 */
export const storageAdminPath = '/storage-admin/v1';
/** 新建、编辑、更换密钥、启停、检查与写入规则维护的按钮资源；没有该按钮时页面只读。 */
export const manageFileStorage = 'OPERATIONS_FILE_STORAGE_MANAGE';
/** 只有拥有维护按钮资源时才显示写操作入口；否则整页只读。 */
export const canManageFileStorage = (resources: readonly { resourceType: string; resourceCode: string }[]) =>
  resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === manageFileStorage);
/** 默认写入规则的来源系统，未单独配置的来源都落到这里。 */
export const defaultSourceSystem = '*';
/** 桶启用前要求最近一次检查通过的时效。 */
export const bucketCheckValidMillis = 10 * 60_000;

export const storageTypes = ['MINIO', 'S3'] as const;
export const credentialModes = ['STATIC', 'IAM_ROLE'] as const;
export const instanceStatuses = ['DRAFT', 'ACTIVE', 'DISABLED'] as const;
export const bucketStatuses = ['DRAFT', 'ACTIVE', 'READ_ONLY', 'DISABLED'] as const;
export const checkResults = ['OK', 'NOT_FOUND', 'VERSIONING_DISABLED', 'ENCRYPTION_DISABLED', 'ACCESS_DENIED', 'UNAVAILABLE'] as const;
export type StorageType = typeof storageTypes[number];
export type CredentialMode = typeof credentialModes[number];
export type InstanceStatus = typeof instanceStatuses[number];
export type BucketStatus = typeof bucketStatuses[number];
export type CheckResult = typeof checkResults[number];

export const storageTypeLabels: Readonly<Record<StorageType, string>> = { MINIO: 'MinIO', S3: 'Amazon S3' };
export const credentialModeLabels: Readonly<Record<CredentialMode, string>> = { STATIC: '固定密钥（Vault）', IAM_ROLE: 'IAM 角色' };
export const instanceStatusLabels: Readonly<Record<InstanceStatus, string>> = { DRAFT: '草稿', ACTIVE: '启用', DISABLED: '停用' };
export const bucketStatusLabels: Readonly<Record<BucketStatus, string>> = { DRAFT: '草稿', ACTIVE: '启用', READ_ONLY: '只读', DISABLED: '停用' };
export const statusColors: Readonly<Record<string, string>> = { DRAFT: 'gold', ACTIVE: 'green', READ_ONLY: 'blue', DISABLED: 'default' };
export const checkResultLabels: Readonly<Record<CheckResult, string>> = {
  OK: '检查通过',
  NOT_FOUND: '桶不存在，请先由基础设施创建',
  VERSIONING_DISABLED: '未开启版本控制',
  ENCRYPTION_DISABLED: '未开启默认加密',
  ACCESS_DENIED: '凭据无权读写该桶',
  UNAVAILABLE: '存储或 Vault 暂时无法访问',
};

export const instanceCodePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
export const sourceSystemPattern = /^[a-z][a-z0-9-]{0,63}$/;
/** S3 桶命名规则的前端预检；最终以 Platform 校验为准。 */
export const bucketNamePattern = /^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$/;
export const regionPattern = /^[a-z0-9][a-z0-9-]{0,63}$/;
export const scanPolicyCodePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
export const connectTimeoutRange = { min: 1000, max: 10_000, defaultValue: 2000 } as const;
export const readTimeoutRange = { min: 1000, max: 60_000, defaultValue: 10_000 } as const;
export const uploadSecondsRange = { min: 60, max: 900, defaultValue: 300 } as const;

export interface StorageBucket {
  bucketName: string; status: BucketStatus; lastCheckTime: string | null; lastCheckResult: CheckResult | null; routeCount: number;
  version: number; updateTime: string;
}
export interface StorageInstance {
  instanceCode: string; storageType: StorageType; endpoint: string | null; region: string; pathStyleAccess: boolean;
  trustedCertificatePem: string | null; credentialMode: CredentialMode; credentialConfigured: boolean; credentialUpdateTime: string | null;
  connectTimeoutMs: number; readTimeoutMs: number; status: InstanceStatus; version: number; updateTime: string; routeCount: number;
  buckets: StorageBucket[];
}
export interface StorageRoute {
  sourceSystem: string; instanceCode: string; bucketName: string; scanPolicyCode: string; maxUploadSeconds: number; version: number; updateTime: string;
}
export interface BucketRow extends StorageBucket { instanceCode: string; instanceStatus: InstanceStatus }

const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const exactFields = (v: Record<string, unknown>, fields: readonly string[]) =>
  Object.keys(v).length === fields.length && fields.every(field => field in v);
const bucketFields = ['bucketName', 'status', 'lastCheckTime', 'lastCheckResult', 'routeCount', 'version', 'updateTime'];
const instanceFields = ['instanceCode', 'storageType', 'endpoint', 'region', 'pathStyleAccess', 'trustedCertificatePem', 'credentialMode',
  'credentialConfigured', 'credentialUpdateTime', 'connectTimeoutMs', 'readTimeoutMs', 'status', 'version', 'updateTime', 'routeCount', 'buckets'];
const routeFields = ['sourceSystem', 'instanceCode', 'bucketName', 'scanPolicyCode', 'maxUploadSeconds', 'version', 'updateTime'];
const mutationFields = ['objectType', 'objectId', 'objectCode', 'status', 'version'];
const checkFields = ['instanceCode', 'bucketName', 'result', 'checkedAt', 'version'];
const text = (v: unknown): v is string => typeof v === 'string' && v.trim().length > 0;
const optionalText = (v: unknown) => v === null || text(v);
const integer = (v: unknown, min: number, max = Number.MAX_SAFE_INTEGER) => Number.isInteger(v) && Number(v) >= min && Number(v) <= max;
const oneOf = (values: readonly string[]) => (v: unknown) => typeof v === 'string' && values.includes(v);
const instant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
const optionalInstant = (v: unknown) => v === null || instant(v);
/** 解码失败只给通用错误，不回显响应内容。 */
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

function decodeBucket(value: unknown): StorageBucket {
  require(object(value) && exactFields(value, bucketFields));
  const v = value as unknown as StorageBucket;
  require(text(v.bucketName) && oneOf(bucketStatuses)(v.status) && optionalInstant(v.lastCheckTime)
    && (v.lastCheckResult === null || oneOf(checkResults)(v.lastCheckResult)) && integer(v.routeCount, 0) && integer(v.version, 1)
    && instant(v.updateTime));
  return v;
}

function decodeInstance(value: unknown): StorageInstance {
  require(object(value) && exactFields(value, instanceFields) && Array.isArray(value.buckets));
  const v = value as unknown as StorageInstance;
  require(instanceCodePattern.test(String(v.instanceCode)) && oneOf(storageTypes)(v.storageType) && optionalText(v.endpoint)
    && text(v.region) && typeof v.pathStyleAccess === 'boolean' && optionalText(v.trustedCertificatePem)
    && oneOf(credentialModes)(v.credentialMode) && typeof v.credentialConfigured === 'boolean' && optionalInstant(v.credentialUpdateTime)
    && integer(v.connectTimeoutMs, 1) && integer(v.readTimeoutMs, 1) && oneOf(instanceStatuses)(v.status)
    && integer(v.routeCount, 0) && integer(v.version, 1) && instant(v.updateTime));
  const buckets = v.buckets.map(decodeBucket);
  require(new Set(buckets.map(bucket => bucket.bucketName)).size === buckets.length);
  return { ...v, buckets };
}

/** 实例列表严格解码：字段集合、枚举、计数、版本与时间逐项校验，实例代码不得重复；多出的字段（如凭据 Token）失败关闭。 */
export function decodeInstanceList(value: unknown): StorageInstance[] {
  require(object(value) && exactFields(value, ['items']) && Array.isArray(value.items));
  const items = (value as { items: unknown[] }).items.map(decodeInstance);
  require(new Set(items.map(item => item.instanceCode)).size === items.length);
  return items;
}

function decodeRoute(value: unknown): StorageRoute {
  require(object(value) && exactFields(value, routeFields));
  const v = value as unknown as StorageRoute;
  require((v.sourceSystem === defaultSourceSystem || sourceSystemPattern.test(String(v.sourceSystem)))
    && instanceCodePattern.test(String(v.instanceCode)) && text(v.bucketName) && scanPolicyCodePattern.test(String(v.scanPolicyCode))
    && integer(v.maxUploadSeconds, uploadSecondsRange.min, uploadSecondsRange.max) && integer(v.version, 1) && instant(v.updateTime));
  return v;
}

/** 写入规则列表：来源系统不得重复，默认规则排在最前。 */
export function decodeRouteList(value: unknown): StorageRoute[] {
  require(object(value) && exactFields(value, ['items']) && Array.isArray(value.items));
  const items = (value as { items: unknown[] }).items.map(decodeRoute);
  require(new Set(items.map(item => item.sourceSystem)).size === items.length);
  return [...items].sort((a, b) => Number(b.sourceSystem === defaultSourceSystem) - Number(a.sourceSystem === defaultSourceSystem));
}

export interface StorageMutation { objectType: string; objectId: string; objectCode: string; status: string | null; version: number }

/** 写回执：对象代码必须是本次操作的对象（桶为 {实例代码}/{桶名}），版本为正整数；删除回执的状态为空。 */
export function decodeMutation(value: unknown, objectCode: string): StorageMutation {
  require(object(value) && exactFields(value, mutationFields));
  const v = value as unknown as StorageMutation;
  require(text(v.objectType) && text(v.objectId) && v.objectCode === objectCode && (v.status === null || text(v.status))
    && integer(v.version, 1));
  return v;
}

export interface BucketCheck { instanceCode: string; bucketName: string; result: CheckResult; checkedAt: string; version: number }

/** 桶检查结果：必须是本次检查的桶，结果码属于约定集合，检查时间可解析。 */
export function decodeBucketCheck(value: unknown, instanceCode: string, bucketName: string): BucketCheck {
  require(object(value) && exactFields(value, checkFields));
  const v = value as unknown as BucketCheck;
  require(v.instanceCode === instanceCode && v.bucketName === bucketName && oneOf(checkResults)(v.result) && instant(v.checkedAt)
    && integer(v.version, 1));
  return v;
}

/** 桶在写回执中的对象代码。 */
export const bucketObjectCode = (instanceCode: string, bucketName: string) => `${instanceCode}/${bucketName}`;

/** 桶页签的行：展开各实例的桶，可按实例筛选。 */
export function bucketRows(instances: readonly StorageInstance[], instanceCode?: string): BucketRow[] {
  return instances.filter(instance => !instanceCode || instance.instanceCode === instanceCode)
    .flatMap(instance => instance.buckets.map(bucket => ({ ...bucket, instanceCode: instance.instanceCode, instanceStatus: instance.status })));
}

/** 写入规则可选的目标：启用实例下的启用桶。 */
export function routeTargets(instances: readonly StorageInstance[]) {
  return instances.filter(instance => instance.status === 'ACTIVE').flatMap(instance => instance.buckets
    .filter(bucket => bucket.status === 'ACTIVE').map(bucket => ({ instanceCode: instance.instanceCode, bucketName: bucket.bucketName })));
}

/** 来源系统展示：默认规则显示为"默认"。 */
export const sourceSystemLabel = (sourceSystem: string) => sourceSystem === defaultSourceSystem ? '默认' : sourceSystem;

/** 默认规则不可删除，其余规则可删除。 */
export const deletableRoute = (route: StorageRoute) => route.sourceSystem !== defaultSourceSystem;

export interface StatusAction<S extends string> { target: S; verb: string; danger: boolean }

/** 实例状态动作：草稿与停用可启用，启用可停用。 */
export function instanceStatusAction(instance: StorageInstance): StatusAction<InstanceStatus> {
  return instance.status === 'ACTIVE'
    ? { target: 'DISABLED', verb: '停用', danger: true }
    : { target: 'ACTIVE', verb: '启用', danger: false };
}

/** 实例启停前的提示；启用要求至少一个启用的桶，停用时被写入规则引用会被 Platform 拒绝。 */
export function instanceStatusNotice(instance: StorageInstance): string {
  if (instance.status === 'ACTIVE') {
    return instance.routeCount > 0
      ? `该实例被 ${instance.routeCount} 条写入规则引用，需先把这些规则改到其他桶，否则停用会被拒绝。`
      : '停用后该实例上的文件读写都返回不可用，已有记录保留。';
  }
  return instance.buckets.some(bucket => bucket.status === 'ACTIVE')
    ? '启用后该实例可被写入规则引用。'
    : '该实例还没有启用的桶，启用会被拒绝；请先登记并检查、启用一个桶。';
}

/** 桶的可用状态动作：草稿与只读可启用，启用可设为只读，启用与只读可停用；停用为终态。 */
export function bucketStatusActions(bucket: StorageBucket): StatusAction<BucketStatus>[] {
  const actions: StatusAction<BucketStatus>[] = [];
  if (bucket.status === 'DRAFT' || bucket.status === 'READ_ONLY') actions.push({ target: 'ACTIVE', verb: '启用', danger: false });
  if (bucket.status === 'ACTIVE') actions.push({ target: 'READ_ONLY', verb: '只读', danger: false });
  if (bucket.status === 'ACTIVE' || bucket.status === 'READ_ONLY') actions.push({ target: 'DISABLED', verb: '停用', danger: true });
  return actions;
}

/**
 * 桶状态动作的前端预检，返回不满足时的原因，满足时返回 null；最终以 Platform 判断为准
 * <li>草稿桶启用前要求最近一次检查通过且在 10 分钟内。</li>
 * <li>被写入规则引用的桶不能设为只读或停用。</li>
 */
export function bucketStatusBlocker(bucket: StorageBucket, target: BucketStatus, now = Date.now()): string | null {
  if (target !== 'ACTIVE') return bucket.routeCount > 0 ? `被 ${bucket.routeCount} 条写入规则引用，需先修改规则` : null;
  if (bucket.status !== 'DRAFT') return null;
  if (bucket.lastCheckResult !== 'OK' || !bucket.lastCheckTime) return '启用前需要先检查通过';
  if (now - Date.parse(bucket.lastCheckTime) > bucketCheckValidMillis) return '最近一次检查已超过 10 分钟，请重新检查';
  return null;
}

/** MinIO 只能使用固定密钥；S3 两种方式均可。 */
export const allowedCredentialModes = (storageType: StorageType): readonly CredentialMode[] =>
  storageType === 'MINIO' ? ['STATIC'] : credentialModes;

export interface InstanceFormValues {
  instanceCode?: string; storageType?: StorageType; endpoint?: string; region?: string; pathStyleAccess?: boolean;
  trustedCertificatePem?: string; credentialMode?: CredentialMode; accessKey?: string; secretKey?: string;
  connectTimeoutMs?: number; readTimeoutMs?: number; reason?: string;
}

const trimmedOrNull = (value?: string) => value?.trim() ? value.trim() : null;

/** 新建实例请求体：IAM 角色不发送密钥字段；密钥原样发送，不做裁剪。 */
export function createInstanceBody(values: InstanceFormValues) {
  const staticCredential = values.credentialMode === 'STATIC';
  return {
    instanceCode: (values.instanceCode ?? '').trim(), storageType: values.storageType, endpoint: trimmedOrNull(values.endpoint),
    region: (values.region ?? '').trim(), pathStyleAccess: values.pathStyleAccess === true,
    trustedCertificatePem: trimmedOrNull(values.trustedCertificatePem), credentialMode: values.credentialMode,
    accessKey: staticCredential ? values.accessKey ?? '' : null, secretKey: staticCredential ? values.secretKey ?? '' : null,
    connectTimeoutMs: values.connectTimeoutMs, readTimeoutMs: values.readTimeoutMs, reason: (values.reason ?? '').trim(),
  };
}

/** 编辑实例请求体：代码、类型与凭据方式不可在此修改。 */
export function updateInstanceBody(values: InstanceFormValues, instance: StorageInstance) {
  return {
    endpoint: trimmedOrNull(values.endpoint), region: (values.region ?? '').trim(), pathStyleAccess: values.pathStyleAccess === true,
    trustedCertificatePem: trimmedOrNull(values.trustedCertificatePem), connectTimeoutMs: values.connectTimeoutMs,
    readTimeoutMs: values.readTimeoutMs, expectedVersion: instance.version, reason: (values.reason ?? '').trim(),
  };
}

export interface CredentialFormValues { credentialMode?: CredentialMode; accessKey?: string; secretKey?: string; reason?: string }

/** 更换密钥请求体：切换为 IAM 角色时不发送密钥。 */
export function credentialBody(values: CredentialFormValues, instance: StorageInstance) {
  const staticCredential = values.credentialMode === 'STATIC';
  return {
    credentialMode: values.credentialMode, accessKey: staticCredential ? values.accessKey ?? '' : null,
    secretKey: staticCredential ? values.secretKey ?? '' : null, expectedVersion: instance.version, reason: (values.reason ?? '').trim(),
  };
}

export interface RouteFormValues { sourceSystem?: string; target?: string; scanPolicyCode?: string; maxUploadSeconds?: number; reason?: string }

/** 目标选择框的值：实例代码与桶名用 / 连接（实例代码不含 /）。 */
export const targetValue = (instanceCode: string, bucketName: string) => `${instanceCode}/${bucketName}`;

function splitTarget(target?: string) {
  const index = (target ?? '').indexOf('/');
  return { instanceCode: index > 0 ? target!.slice(0, index) : '', bucketName: index > 0 ? target!.slice(index + 1) : '' };
}

/** 新建写入规则请求体。 */
export function createRouteBody(values: RouteFormValues) {
  return { sourceSystem: (values.sourceSystem ?? '').trim(), ...splitTarget(values.target),
    scanPolicyCode: (values.scanPolicyCode ?? '').trim(), maxUploadSeconds: values.maxUploadSeconds, reason: (values.reason ?? '').trim() };
}

/** 修改写入规则请求体：来源系统在路径上。 */
export function updateRouteBody(values: RouteFormValues, route: StorageRoute) {
  return { ...splitTarget(values.target), scanPolicyCode: (values.scanPolicyCode ?? '').trim(),
    maxUploadSeconds: values.maxUploadSeconds, expectedVersion: route.version, reason: (values.reason ?? '').trim() };
}

export const instancePath = (instanceCode: string) => `${storageAdminPath}/instances/${encodeURIComponent(instanceCode)}`;
export const bucketPath = (instanceCode: string, bucketName: string) => `${instancePath(instanceCode)}/buckets/${encodeURIComponent(bucketName)}`;
export const routePath = (sourceSystem: string) => `${storageAdminPath}/routes/${encodeURIComponent(sourceSystem)}`;

export type StorageCommandMethod = 'POST' | 'PUT' | 'PATCH' | 'DELETE';
/** 一次写命令；body 为 null 表示无请求体（桶检查）。 */
export interface StorageCommand { path: string; method: StorageCommandMethod; body: string | null; key: string }

/** 同一目标与正文的人工重试复用 Idempotency-Key；改了内容才生成新 Key。 */
export function storageCommand(path: string, method: StorageCommandMethod, payload: unknown, previous?: StorageCommand): StorageCommand {
  const body = payload === null ? null : JSON.stringify(payload);
  const same = previous?.path === path && previous.method === method && previous.body === body;
  return { path, method, body, key: same ? previous.key : crypto.randomUUID() };
}

/** 发出写命令；请求幂等由 Gateway 依据 Idempotency-Key 处理；BFF 要求写方法声明 JSON，无请求体（桶检查）时也只带 Content-Type 不带 Body。 */
export function sendStorageCommand<T>(command: StorageCommand, decode: (value: unknown) => T): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json', 'Idempotency-Key': command.key };
  return session.request<T>(command.path, { method: command.method, body: command.body ?? undefined, headers }, decode);
}
