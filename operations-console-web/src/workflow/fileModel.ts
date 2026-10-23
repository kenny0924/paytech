import type {
  ActionRun, FileUploadConfig, FormDefinition, FrozenTemplate, InstanceDetail, Problem, WorkflowFile, WorkflowGraph,
} from './workflowTypes.ts';

/**
 * 工作流附件的纯逻辑（technique 第 5 节、接口 #20 #26—#28）：上传状态机、临时地址与 Header 守卫、数量与类型预检、
 * 从启动值与动作输出取文件 ID、预览方式与 422 结构错误到节点的映射。不持有浏览器状态，便于单测。
 */

/** 文件 ID：无前导零的正整数十进制字符串。 */
export const fileIdPattern = /^[1-9][0-9]{0,18}$/;

/** 文件地址：业务数据里保存的文件引用，固定为 /files/v1/{fileId}。 */
export const fileAddressPattern = /^\/files\/v1\/[1-9][0-9]{0,18}$/;

/** 按固定格式由文件 ID 生成文件地址；ID 非法时抛错。 */
export function fileAddressOf(fileId: string): string {
  if (!fileIdPattern.test(fileId)) throw new Error('文件 ID 无效');
  return `/files/v1/${fileId}`;
}

/** 解析文件地址里的文件 ID；不是合法地址返回 null。 */
export function fileIdOfAddress(address: unknown): string | null {
  return typeof address === 'string' && fileAddressPattern.test(address) ? address.slice('/files/v1/'.length) : null;
}

/** 开始上传返回的临时 PUT 授权。 */
export interface UploadGrant {
  uploadId: string;
  authorization: { method: string; url: string; headers: Record<string, string>; expiresAt: string };
  expiresAt: string;
}
export interface UploadReceipt { uploadId: string; fileId: string; fileAddress: string; completedAt: string }
/** 下载方式：INLINE 浏览器内打开，ATTACHMENT 作为附件下载。 */
export type FileDisposition = 'INLINE' | 'ATTACHMENT';
/** 下载授权；disposition 为实际生效的方式，请求 INLINE 不满足条件时由文件服务降为 ATTACHMENT。 */
export interface DownloadGrant { url: string; expiresAt: string; disposition: FileDisposition }

const object = (value: unknown): Record<string, unknown> =>
  value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};

function invalid(): never { throw new Error('文件接口响应无效'); }

export function decodeUploadGrant(value: unknown): UploadGrant {
  const item = object(value); const authorization = object(item.authorization);
  if (typeof item.uploadId !== 'string' || !fileIdPattern.test(item.uploadId) || typeof authorization.url !== 'string') invalid();
  const headers = Object.fromEntries(Object.entries(object(authorization.headers)).filter(([, header]) => typeof header === 'string')) as Record<string, string>;
  return { uploadId: item.uploadId, expiresAt: String(item.expiresAt ?? ''), authorization: { method: String(authorization.method ?? ''),
    url: authorization.url, headers, expiresAt: String(authorization.expiresAt ?? '') } };
}

export function decodeUploadReceipt(value: unknown): UploadReceipt {
  const item = object(value);
  if (typeof item.uploadId !== 'string' || typeof item.fileId !== 'string' || !fileIdPattern.test(item.fileId)) invalid();
  const expected = fileAddressOf(item.fileId);
  // 响应带 fileAddress 时必须与文件 ID 一致；旧响应没有时按固定格式兜底。
  if (item.fileAddress !== undefined && item.fileAddress !== null
    && (typeof item.fileAddress !== 'string' || !fileAddressPattern.test(item.fileAddress) || item.fileAddress !== expected)) invalid();
  return { uploadId: item.uploadId, fileId: item.fileId, fileAddress: expected, completedAt: String(item.completedAt ?? '') };
}

export function decodeDownloadGrant(value: unknown): DownloadGrant {
  const item = object(value);
  if (typeof item.url !== 'string' || typeof item.expiresAt !== 'string') invalid();
  if (item.disposition !== undefined && item.disposition !== null && item.disposition !== 'INLINE' && item.disposition !== 'ATTACHMENT') invalid();
  // 旧响应没有 disposition 时按附件。
  return { url: item.url, expiresAt: item.expiresAt, disposition: item.disposition === 'INLINE' ? 'INLINE' : 'ATTACHMENT' };
}

/** 实例详情里的文件元数据；旧响应没有时为空数组。 */
export function decodeFiles(value: unknown): WorkflowFile[] {
  return (Array.isArray(value) ? value : []).map(object).filter(item => typeof item.fileId === 'string' && fileIdPattern.test(item.fileId)).map(item => ({
    fileId: item.fileId as string,
    // 旧响应没有 fileAddress 时按固定格式兜底。
    fileAddress: typeof item.fileAddress === 'string' && fileAddressPattern.test(item.fileAddress) ? item.fileAddress : fileAddressOf(item.fileId as string),
    displayName: typeof item.displayName === 'string' ? item.displayName : null,
    mimeType: typeof item.mimeType === 'string' ? item.mimeType : null, sizeBytes: typeof item.sizeBytes === 'string' ? item.sizeBytes : null,
    scanStatus: typeof item.scanStatus === 'string' ? item.scanStatus as WorkflowFile['scanStatus'] : null,
    uploaderId: typeof item.uploaderId === 'string' ? item.uploaderId : null }));
}

/** 临时地址：只接受 HTTPS、无账号口令与片段、未过期（与入驻上传一致）。 */
export function safeTransferUrl(grant: { url: string; expiresAt: string }, now = Date.now()): string {
  let url: URL;
  try { url = new URL(grant.url); } catch { throw new Error('文件授权已过期或无效，请重新获取'); }
  if (url.protocol !== 'https:' || url.username || url.password || url.hash || grant.url.length > 8192 || !(Date.parse(grant.expiresAt) > now))
    throw new Error('文件授权已过期或无效，请重新获取');
  return url.href;
}

/** 直传 Header 不得携带会话、Cookie、代理或网关身份。 */
export function safeUploadHeaders(headers: Record<string, string>): Headers {
  const result = new Headers();
  for (const [name, value] of Object.entries(headers)) {
    if (/^(authorization|cookie|proxy-|x-gateway-|x-caller-)/i.test(name)) throw new Error('上传授权包含不允许的请求头');
    result.set(name, value);
  }
  return result;
}

export interface FileRules { minFiles?: number; maxFiles?: number; mimeTypes?: string[] }
export interface LocalFile { name: string; type: string; size: number }

/** 浏览器未识别类型时声明为通用二进制，由存储策略决定是否接受。 */
export const declaredMime = (file: LocalFile) => file.type ? file.type.toLowerCase() : 'application/octet-stream';

/** MIME 是否在允许名单内；名单为空表示不限，支持 image/* 形式。 */
export function mimeAllowed(mime: string, allowed: string[] | undefined): boolean {
  if (!allowed?.length) return true;
  const value = mime.toLowerCase();
  return allowed.some(item => item === value || item.endsWith('/*') && value.startsWith(item.slice(0, -1)));
}

/** 选择文件前的预检：空文件、文件名长度、类型与上限；返回第一个问题，通过为 null。 */
export function preCheckFiles(rules: FileRules, existingCount: number, files: LocalFile[]): string | null {
  if (!files.length) return null;
  if (rules.maxFiles !== undefined && existingCount + files.length > rules.maxFiles) return `最多 ${rules.maxFiles} 个文件，已有 ${existingCount} 个`;
  for (const file of files) {
    if (file.size <= 0) return `「${file.name}」是空文件`;
    if ([...file.name].length > 255) return `「${file.name.slice(0, 20)}…」文件名超过 255 个字符`;
    if (!mimeAllowed(declaredMime(file), rules.mimeTypes)) return `「${file.name}」类型 ${declaredMime(file)} 不在允许范围（${rules.mimeTypes!.join('、')}）`;
  }
  return null;
}

/** 已选文件数量是否满足规则（提交前预检）。 */
export function countProblem(rules: FileRules, count: number, required: boolean): string | null {
  if (!count) return required ? '必填' : null;
  if (rules.minFiles !== undefined && count < rules.minFiles) return `至少 ${rules.minFiles} 个文件`;
  if (rules.maxFiles !== undefined && count > rules.maxFiles) return `最多 ${rules.maxFiles} 个文件`;
  return null;
}

/** 值中的文件地址；纯数字 ID 等非法元素丢弃。 */
export function fileAddressesOf(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => fileIdOfAddress(item) !== null) : [];
}

/** 规则文字：“application/pdf · 1—5 个”。 */
export function rulesHint(rules: FileRules): string {
  return [rules.mimeTypes?.length ? rules.mimeTypes.join('、') : '类型不限',
    rules.minFiles !== undefined || rules.maxFiles !== undefined ? `${rules.minFiles ?? 0}—${rules.maxFiles ?? '∞'} 个` : ''].filter(Boolean).join(' · ');
}

/** 表单中 FILE 字段的 key。 */
export const fileFieldKeys = (form: FormDefinition | undefined) => (form?.fields ?? []).filter(field => field.type === 'FILE').map(field => field.key);

/** 动作输出里的文件地址：FILE_UPLOAD 取 files，FORM 取 FILE 字段。 */
export function outputFileAddresses(run: Pick<ActionRun, 'actionType' | 'output'>, template?: Pick<FrozenTemplate, 'config'>): string[] {
  if (!run.output) return [];
  if (run.actionType === 'FILE_UPLOAD') return fileAddressesOf(run.output.files);
  if (run.actionType === 'FORM') return fileFieldKeys(template?.config as FormDefinition | undefined).flatMap(key => fileAddressesOf(run.output![key]));
  return [];
}

/** 启动值里的文件地址。 */
export const startFileAddresses = (detail: Pick<InstanceDetail, 'startForm' | 'startValues'>) =>
  fileFieldKeys(detail.startForm).flatMap(key => fileAddressesOf(detail.startValues[key]));

/** 上传动作是否要求至少一个文件：新配置看 required，旧配置 minFiles ≥ 1 视为要求（需求 E1）。 */
export const uploadRequired = (config: FileUploadConfig | undefined): boolean =>
  config?.required ?? (config?.minFiles ?? 0) >= 1;

/** 上传动作不再限制数量与类型（需求 E1）：旧配置的 minFiles / maxFiles / mimeTypes 忽略。 */
export const uploadRules = (_config?: FileUploadConfig | undefined): FileRules => ({});

/** 扫描未完成（等待或进行中）：不挡完成，只提示在后台进行（需求 E3）。 */
export const scanPending = (file: WorkflowFile | undefined) => !file?.scanStatus || file.scanStatus === 'PENDING_SCAN' || file.scanStatus === 'SCANNING';

/** 未通过安全检查：不可预览、不可下载。 */
export const scanFailed = (file: WorkflowFile | undefined) => file?.scanStatus === 'QUARANTINED' || file?.scanStatus === 'REJECTED';

/** 已确认未通过安全检查的文件在上传区与 FILE 字段的提示；发起与完成同一规则，须删除后才能提交。 */
export const SCAN_FAILED_TEXT = '未通过安全检查，请删除后重新上传';

/**
 * 422 中落在单个文件元素上的错误：字段路径为 {root}.{key}[i]（上传动作 key 为 files），按本次提交值取回地址。
 * 前端刚上传的文件没有扫描元数据，以服务端结论为准。ops-bff 只透传登记的 Platform 固定文案，其余为通用提示，
 * 因此不按文案判断：落在单个文件元素上的错误（页面只会提交本人刚上传的文件，实际即未通过安全检查）都要求删除后重新上传。
 */
export function scanFailedErrors(errors: readonly { field: string; message: string }[], root: string,
  submitted: Record<string, unknown>): { field: string; address: string }[] {
  const prefix = `${root}.`;
  return errors.flatMap(error => {
    if (!error.field.startsWith(prefix)) return [];
    const item = /^([A-Za-z][A-Za-z0-9_]*)\[(\d+)\]$/.exec(error.field.slice(prefix.length));
    const value = item ? submitted[item[1]] : undefined;
    const address = Array.isArray(value) ? value[Number(item![2])] : undefined;
    return fileIdOfAddress(address) ? [{ field: error.field, address: address as string }] : [];
  });
}

/** 须删除重传的文件地址（见 scanFailedErrors）。 */
export function scanFailedAddresses(errors: readonly { field: string; message: string }[], root: string,
  submitted: Record<string, unknown>): string[] {
  return scanFailedErrors(errors, root, submitted).map(item => item.address);
}

/** 去掉已在文件上就地标红的元素错误，剩余的才按字段汇总显示，避免同一问题出现两次。 */
export function withoutInlineFileErrors<T extends { field: string; message: string }>(errors: readonly T[], root: string,
  submitted: Record<string, unknown>): T[] {
  const inline = new Set(scanFailedErrors(errors, root, submitted).map(item => item.field));
  return errors.filter(error => !inline.has(error.field));
}

/** 已选文件中须删除后才能提交的：实例元数据为未通过，或服务端已报告未通过；扫描中的不算。 */
export function blockingFiles(addresses: string[], known: ReadonlyMap<string, WorkflowFile> | undefined,
  reported: ReadonlySet<string>): string[] {
  return addresses.filter(address => reported.has(address) || scanFailed(known?.get(address)));
}

/** 文件服务可内嵌打开的图片类型，与 Platform 的内联白名单一致（另有 application/pdf）。 */
const INLINE_IMAGE_TYPES = new Set(['image/png', 'image/jpeg']);

export type PreviewKind = 'pdf' | 'image' | 'other';
/**
 * 按登记类型决定预览方式：PDF 用 iframe、PNG / JPEG 用 img，以 INLINE 签发；其他类型为 other（下载查看）。
 * 实际是否内联以下载授权返回的 disposition 为准，不在前端改写存储签名参数。
 */
export function previewKind(mime: string | null | undefined): PreviewKind {
  if (mime === 'application/pdf') return 'pdf';
  if (mime && INLINE_IMAGE_TYPES.has(mime)) return 'image';
  return 'other';
}

/** 只有扫描通过的文件可以下载与预览。 */
export const downloadable = (file: WorkflowFile | undefined) => file?.scanStatus === 'AVAILABLE';

export const scanStatusLabels: Record<NonNullable<WorkflowFile['scanStatus']>, string> = {
  PENDING_SCAN: '安全检查中', SCANNING: '安全检查中', AVAILABLE: '已通过安全检查', QUARANTINED: '未通过安全检查', REJECTED: '未通过安全检查',
};
export const scanStatusColors: Record<NonNullable<WorkflowFile['scanStatus']>, string> = {
  PENDING_SCAN: 'processing', SCANNING: 'processing', AVAILABLE: 'success', QUARANTINED: 'error', REJECTED: 'error',
};

/** 字节数展示：1.2 MB。 */
export function sizeText(bytes: number | string | null | undefined): string {
  if (bytes === null || bytes === undefined || bytes === '') return '—';
  const value = Number(bytes);
  if (!Number.isFinite(value) || value < 0) return '—';
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

// ---------- 上传 ----------
export type UploadPhase = 'UPLOADING' | 'COMPLETING' | 'DONE' | 'FAILED';
export interface UploadItem { localId: string; name: string; size: number; mime: string; phase: UploadPhase; fileAddress?: string; error?: string }

/** 发送工作流写请求：path、无正文时 body 为 undefined。 */
export type WorkflowPost = <T>(path: string, body: unknown, decode: (value: unknown) => T) => Promise<T>;
type Transport = (url: string, init: RequestInit) => Promise<{ ok: boolean; status: number }>;

/**
 * 一个文件的上传：开始上传 → 按授权直传 → 完成上传，返回文件地址（/files/v1/{fileId}）。
 * 直传不带浏览器会话、凭据或 Referrer；任一步失败抛出可展示的中文原因。
 */
export async function uploadOne(file: LocalFile & Blob, paths: { uploads: string; completion: (uploadId: string) => string },
  post: WorkflowPost, transport: Transport, onPhase: (phase: UploadPhase) => void = () => {}): Promise<string> {
  onPhase('UPLOADING');
  const grant = await post(paths.uploads, { filename: file.name, mimeType: declaredMime(file), sizeBytes: String(file.size) }, decodeUploadGrant);
  if (grant.authorization.method !== 'PUT') throw new Error('上传授权无效');
  const url = safeTransferUrl(grant.authorization);
  const headers = safeUploadHeaders(grant.authorization.headers);
  let response: { ok: boolean; status: number };
  try {
    response = await transport(url, { method: 'PUT', body: file, headers, credentials: 'omit', redirect: 'error', referrerPolicy: 'no-referrer' });
  } catch {
    throw new Error('文件传输未确认，请重新上传');
  }
  if (!response.ok) throw new Error(`文件传输失败（${response.status}），请重新上传`);
  onPhase('COMPLETING');
  const receipt = await post(paths.completion(grant.uploadId), undefined, decodeUploadReceipt);
  if (receipt.uploadId !== grant.uploadId) throw new Error('文件完成响应无效');
  onPhase('DONE');
  return receipt.fileAddress;
}

// ---------- 422 结构错误 ----------
/**
 * 草稿保存 422 字段错误转为编辑器问题：graph.nodes[i] / graph.edges[i] 定位到节点或连线，startForm.fields[i] 写字段 key。
 */
export function structureProblems(errors: readonly { field: string; message: string }[], graph: WorkflowGraph,
  startForm: FormDefinition): Problem[] {
  return errors.map(error => {
    const node = /^graph\.nodes\[(\d+)\]/.exec(error.field); const edge = /^graph\.edges\[(\d+)\]/.exec(error.field);
    const field = /^startForm\.fields\[(\d+)\]/.exec(error.field);
    const nodeKey = node ? graph.nodes[Number(node[1])]?.key ?? null : null;
    const edgeKey = edge ? graph.edges[Number(edge[1])]?.key ?? null : null;
    const fieldKey = field ? startForm.fields[Number(field[1])]?.key : undefined;
    const where = fieldKey ? `启动表单字段 ${fieldKey}（${error.field}）` : error.field;
    return { code: 'STRUCTURE_INVALID', nodeKey, edgeKey, itemKey: fieldKey ?? null, message: `${where}${fieldKey ? '' : ' '}${error.message}` };
  });
}

/** 缩略图：节点没有位置（新建草稿）时自动排列，否则原样。 */
export function needsLayout(graph: WorkflowGraph): boolean {
  if (graph.nodes.length < 2) return false;
  const first = graph.nodes[0].position;
  return graph.nodes.every(node => node.position.x === first.x && node.position.y === first.y);
}

/** 实例文件元数据索引，按文件地址。 */
export const fileIndex = (detail: Pick<InstanceDetail, 'files'>) => new Map((detail.files ?? []).map(file => [file.fileAddress, file]));
