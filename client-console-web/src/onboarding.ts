import { RequestFailure } from "./auth.ts";
import type { ApiOptions, ApiRequest } from "./auth.ts";
import { translator } from "./i18n/index.ts";

const to = translator("onboarding");
/** 草稿分区值：`{分区: {字段: 值}}`；null 表示清空。 */
export type Values = Record<string, Record<string, string | null>>;
export type Flat = Record<string, string>;
type FileInfo = { fileId: string; scanStatus: string; objectStatus: string };
export const base = "/onboarding/v1/applications";
export const files = "/onboarding-files/v1";
export function flatten(values: Values): Flat {
  return Object.fromEntries(
    Object.entries(values).flatMap(([group, fields]) =>
      Object.entries(fields).map(([field, value]) => [
        group + "." + field,
        value ?? "",
      ]),
    ),
  );
}
/**
 * 保存失败时按字段路径取出可展示的提示：只保留页面上存在的字段，其余由顶层提示承担。
 * 适用于 Problem errors[]（422 VALIDATION_ERROR 或 INVALID_DRAFT），程序判断仍只用顶层 code。
 */
export function fieldIssues(error: unknown, known: Iterable<string>): Record<string, string> {
  if (!(error instanceof RequestFailure)) return {};
  const allowed = new Set(known);
  return Object.fromEntries(Object.entries(error.fields).filter(([field]) => allowed.has(field)));
}
export function patch(before: Flat, after: Flat) {
  const dirtyFields = Object.keys(after).filter(
    (key) => (before[key] ?? "") !== after[key],
  );
  const values: Values = {};
  for (const key of dirtyFields) {
    const index = key.lastIndexOf(".");
    const group = key.slice(0, index), field = key.slice(index + 1);
    (values[group] ??= {})[field] = after[key].trim() || null;
  }
  return { dirtyFields, values };
}
// 一次用户意图冻结方法、Body 和幂等键；断网不能悄悄变成新命令。
export function createCommand(
  api: ApiRequest,
  path: string,
  method: ApiOptions["method"],
  body?: unknown,
) {
  const options: ApiOptions = {
    method,
    key: crypto.randomUUID(),
    ...(body === undefined ? {} : { body: structuredClone(body) }),
  };
  let flight: Promise<unknown> | null = null;
  return {
    run: () => {
      if (!flight)
        flight = api(path, options).finally(() => {
          flight = null;
        });
      return flight;
    },
  };
}
export type Upload = {
  applicationId: string;
  subjectPartyId: string;
  requirementCode: string;
  documentType: string;
  startKey: string;
  completeKey: string;
  associateKey: string;
  phase: "PUT" | "COMPLETE" | "SCAN" | "ASSOCIATE" | "DONE";
  /** 材料签发日期 / 到期日（YYYY-MM-DD），关联时随引用提交；未填写为空。 */
  issueDate?: string;
  expiryDate?: string;
  uploadId?: string;
  fileId?: string;
  fileAddress?: string;
};
export function newUpload(
  applicationId: string,
  subjectPartyId: string,
  requirementCode: string,
  documentType: string,
): Upload {
  return {
    applicationId,
    subjectPartyId,
    requirementCode,
    documentType,
    startKey: crypto.randomUUID(),
    completeKey: crypto.randomUUID(),
    associateKey: crypto.randomUUID(),
    phase: "PUT",
  };
}
export function downloadUrl(grant: { url: string; expiresAt: string }): string {
  const url = new URL(grant.url);
  if (
    url.protocol !== "https:" ||
    url.username ||
    url.password ||
    url.hash ||
    grant.url.length > 8192 ||
    !(Date.parse(grant.expiresAt) > Date.now())
  )
    throw new Error(to("file.grant_invalid"));
  return url.href;
}
const id = (value: unknown): value is string =>
  typeof value === "string" &&
  /^[1-9][0-9]{0,18}$/.test(value) &&
  BigInt(value) <= 9223372036854775807n;
// 文件服务签发的稳定地址只能对应同一文件 ID；关联材料时原样提交。
const fileAddress = (value: unknown, fileId: string): value is string =>
  value === "/files/v1/" + fileId;
const isDate = (value: unknown): value is string => typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value);
// 只在文件成功传输后持久化无敏感内容的恢复坐标；临时 URL、文件名、字节与资料永不落浏览器存储。
export function readCheckpoint(
  raw: string | null,
  applicationId: string,
): Upload | null {
  try {
    const value = JSON.parse(raw ?? "null") as Upload;
    if (
      !value ||
      value.applicationId !== applicationId ||
      !id(value.subjectPartyId) ||
      !id(value.uploadId) ||
      !["COMPLETE", "SCAN", "ASSOCIATE"].includes(value.phase)
    )
      return null;
    if (
      value.phase !== "COMPLETE" &&
      !(id(value.fileId) && fileAddress(value.fileAddress, value.fileId))
    )
      return null;
    if (
      ![value.startKey, value.completeKey, value.associateKey].every((key) =>
        /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(key),
      )
    )
      return null;
    if (
      ![value.requirementCode, value.documentType].every((code) =>
        /^[A-Z][A-Z0-9_]{0,63}$/.test(code),
      )
    )
      return null;
    return {
      applicationId,
      subjectPartyId: value.subjectPartyId,
      requirementCode: value.requirementCode,
      documentType: value.documentType,
      startKey: value.startKey,
      completeKey: value.completeKey,
      associateKey: value.associateKey,
      phase: value.phase,
      uploadId: value.uploadId,
      ...(isDate(value.issueDate) ? { issueDate: value.issueDate } : {}),
      ...(isDate(value.expiryDate) ? { expiryDate: value.expiryDate } : {}),
      ...(value.fileId
        ? { fileId: value.fileId, fileAddress: value.fileAddress }
        : {}),
    };
  } catch {
    return null;
  }
}
export async function runUpload(
  state: Upload,
  api: ApiRequest,
  file?: File,
  transport: typeof fetch = fetch,
  checkpoint: (state: Upload) => void = () => {},
) {
  if (state.phase === "PUT") {
    if (!file || file.size === 0) throw new Error(to("file.empty"));
    const grant = await api<{
      uploadId: string;
      authorization: {
        method: string;
        url: string;
        expiresAt: string;
        headers: Record<string, string>;
      };
    }>(files + "/uploads", {
      method: "POST",
      key: state.startKey,
      body: {
        filename: file.name,
        mimeType: file.type || "application/octet-stream",
        sizeBytes: String(file.size),
      },
    });
    if (!id(grant.uploadId) || grant.authorization.method !== "PUT")
      throw new Error(to("file.upload_grant_invalid"));
    const url = downloadUrl(grant.authorization);
    const headers = new Headers(grant.authorization.headers);
    // 不转发浏览器会话或代理身份，即使依赖响应出现异常。
    for (const name of headers.keys())
      if (/^(authorization|cookie|proxy-|x-gateway-|x-caller-)/i.test(name))
        throw new Error(to("file.upload_grant_headers"));
    let response: Response;
    try {
      response = await transport(url, {
        method: "PUT",
        body: file,
        headers,
        credentials: "omit",
        redirect: "error",
        referrerPolicy: "no-referrer",
      });
    } catch {
      throw new RequestFailure(0, to("file.transfer_unconfirmed"));
    }
    if (!response.ok)
      throw new RequestFailure(response.status, to("file.transfer_failed"));
    state.uploadId = grant.uploadId;
    state.phase = "COMPLETE";
    checkpoint(state);
  }
  if (state.phase === "COMPLETE") {
    const receipt = await api<{
      uploadId: string;
      fileId: string;
      fileAddress: string;
    }>(files + "/uploads/" + state.uploadId + "/completion", {
      method: "POST",
      key: state.completeKey,
    });
    if (
      receipt.uploadId !== state.uploadId ||
      !id(receipt.fileId) ||
      !fileAddress(receipt.fileAddress, receipt.fileId)
    )
      throw new Error(to("file.completion_invalid"));
    state.fileId = receipt.fileId;
    state.fileAddress = receipt.fileAddress;
    state.phase = "SCAN";
    checkpoint(state);
  }
  if (state.phase === "SCAN") {
    const meta = await api<FileInfo>(files + "/files/" + state.fileId);
    if (meta.fileId !== state.fileId || meta.objectStatus !== "ACTIVE")
      throw new Error(to("file.unavailable"));
    if (["PENDING_SCAN", "SCANNING"].includes(meta.scanStatus)) return;
    if (meta.scanStatus !== "AVAILABLE")
      throw new Error(to("file.scan_rejected"));
    state.phase = "ASSOCIATE";
    checkpoint(state);
  }
  if (state.phase === "ASSOCIATE") {
    await api(base + "/" + state.applicationId + "/documents", {
      method: "POST",
      key: state.associateKey,
      body: {
        subjectPartyId: state.subjectPartyId,
        requirementCode: state.requirementCode,
        documentType: state.documentType,
        fileAddress: state.fileAddress,
        ...(state.issueDate ? { issueDate: state.issueDate } : {}),
        ...(state.expiryDate ? { expiryDate: state.expiryDate } : {}),
      },
    });
    state.phase = "DONE";
    checkpoint(state);
  }
}

// 关系局部修改同样不能覆盖本次未编辑的生效期间等属性。
export function relationPatch(
  before: Record<string, string | null>,
  after: Record<string, string | null>,
) {
  const dirtyFields = Object.keys(after).filter(
    (key) => before[key] !== after[key],
  );
  return {
    dirtyFields,
    values: Object.fromEntries(dirtyFields.map((key) => [key, after[key]])),
  };
}
