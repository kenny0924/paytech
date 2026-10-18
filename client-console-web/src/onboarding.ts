import { RequestFailure } from "./auth.ts";
import type { ApiOptions, ApiRequest } from "./auth.ts";
import { enterableWorkspace } from "./workspace.ts";
import type { Workspaces } from "./workspace.ts";
export type Values = Record<string, Record<string, string | null>>;
export type Flat = Record<string, string>;
export type Requirement = {
  requirementCode: string;
  documentType: string;
  requiredFlag: boolean;
};
export type FileInfo = {
  fileId: string;
  displayName: string;
  sizeBytes: string;
  mimeType: string;
  scanStatus: string;
  objectStatus: string;
  allowedActions: string[];
};
export type Relation = {
  relationId: string;
  relatedPartyId: string;
  relationType: string;
  ownershipPercentage: string | null;
  effectiveFrom: string;
  effectiveTo: string | null;
  status: string;
  editable: boolean;
};
export type Person = { partyId: string; editable: boolean; values: Values };
export type Application = {
  applicationId: string;
  applicationNo: string;
  clientId?: string;
  subjectPartyId: string;
  clientType: "INDIVIDUAL" | "ORGANIZATION";
  status: string;
  savedAt: string;
  editable: boolean;
  values: Values;
  people: Person[];
  relations: Relation[];
  documents: {
    documentId: string;
    subjectPartyId: string;
    fileId: string;
    requirementCode: string;
    documentType: string;
    subjectInScope: boolean;
    fileStatus: string;
    file: FileInfo | null;
  }[];
  materialSubjects: {
    subjectPartyId: string;
    requirementsStatus: string;
    requirements: Requirement[];
  }[];
  reviewStatus?: "NONE" | "AVAILABLE" | "UNAVAILABLE";
  review?: ReviewResult | null;
};
/** 最近一次审核的客户可见结果；内部理由不会返回到页面。 */
export type ReviewResult = {
  outcome: "RETURNED" | "APPROVED" | "REJECTED";
  returnSection: "BASIC" | "BUSINESS" | "PEOPLE" | "MATERIALS" | null;
  publicNote: string | null;
  decidedAt: string | null;
};
/** 退回分区对应的页面位置。 */
export const returnSections: Record<string, { label: string; anchor: string }> = {
  BASIC: { label: "基础资料", anchor: "profile" },
  BUSINESS: { label: "经营资料", anchor: "business" },
  PEOPLE: { label: "相关人员", anchor: "people" },
  MATERIALS: { label: "申请材料", anchor: "materials" },
};
export type ResultBanner = {
  tone: "warning" | "success" | "error" | "info";
  title: string;
  lines: string[];
  anchor: string | null;
  /** 审核通过且工作区已可访问时，可直接进入的成员；否则为空。 */
  enterMemberId?: string | null;
};
/**
 * 审核结果提示；只用 CCT 状态与 Risk 公开说明，工作区是否可进入只看服务端返回的工作区状态
 * <li>结果读取失败时给出通用说明，不把未知错误当成审核原因。</li>
 */
export function resultBanner(app: Pick<Application, "status" | "reviewStatus" | "review" | "clientId">,
  workspaces: Workspaces | null = null): ResultBanner | null {
  const review = app.reviewStatus === "AVAILABLE" && app.review?.outcome === app.status ? app.review : null;
  const note = review?.publicNote?.trim() || null;
  const unavailable = "审核说明暂时无法读取，请稍后刷新页面。";
  if (app.status === "RETURNED") {
    const section = review?.returnSection ? returnSections[review.returnSection] : undefined;
    return {
      tone: "warning",
      title: "申请需要修改后重新提交",
      lines: [
        ...(section ? ["需要修改：" + section.label] : []),
        ...(note ? [note] : review ? [] : [unavailable]),
        "修改完成后，请在页面底部重新提交审核。",
      ],
      anchor: section?.anchor ?? null,
    };
  }
  if (app.status === "APPROVED") {
    const entry = enterableWorkspace(app.clientId, workspaces);
    return {
      tone: "success",
      title: "审核已通过",
      lines: [...(note ? [note] : []), entry ? "客户工作区已开通，可以进入。" : "客户工作区正在开通，开通完成后即可进入。"],
      anchor: null,
      enterMemberId: entry?.memberId ?? null,
    };
  }
  if (app.status === "REJECTED") {
    return {
      tone: "error",
      title: "申请未通过",
      lines: [note ?? (review ? "如有疑问，请联系客服。" : unavailable), "此申请不能再修改；如需继续，请重新发起申请。"],
      anchor: null,
    };
  }
  return null;
}
export type Start = {
  workspaces?: unknown;
  applications: {
    applicationId: string;
    applicationNo: string;
    clientType: string;
    status: string;
    active: boolean;
  }[];
  activeApplicationId: string | null;
  hasMore: boolean;
};
export const base = "/onboarding/v1/applications";
export const files = "/onboarding-files/v1";
export const statuses: Record<string, string> = {
  DRAFT: "填写中",
  RETURNED: "退回修改",
  SUBMITTED: "已提交",
  UNDER_REVIEW: "审核中",
  APPROVED: "已通过",
  REJECTED: "已拒绝",
};
export const scanLabels: Record<string, string> = {
  PENDING_SCAN: "等待扫描",
  SCANNING: "正在扫描",
  AVAILABLE: "可用",
  QUARANTINED: "已隔离",
  REJECTED: "不可用",
};
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
    const [group, field] = key.split(".");
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
  uploadId?: string;
  fileId?: string;
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
    throw new Error("文件授权已过期或无效，请重新获取");
  return url.href;
}
const id = (value: unknown): value is string =>
  typeof value === "string" &&
  /^[1-9][0-9]{0,18}$/.test(value) &&
  BigInt(value) <= 9223372036854775807n;
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
    if (value.phase !== "COMPLETE" && !id(value.fileId)) return null;
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
      ...(value.fileId ? { fileId: value.fileId } : {}),
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
    if (!file || file.size === 0) throw new Error("请选择非空文件");
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
        applicationId: state.applicationId,
        subjectPartyId: state.subjectPartyId,
        filename: file.name,
        mimeType: file.type || "application/octet-stream",
        sizeBytes: String(file.size),
      },
    });
    if (!id(grant.uploadId) || grant.authorization.method !== "PUT")
      throw new Error("上传授权无效");
    const url = downloadUrl(grant.authorization);
    const headers = new Headers(grant.authorization.headers);
    // 不转发浏览器会话或代理身份，即使依赖响应出现异常。
    for (const name of headers.keys())
      if (/^(authorization|cookie|proxy-|x-gateway-|x-caller-)/i.test(name))
        throw new Error("上传授权包含不允许的请求头");
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
      throw new RequestFailure(0, "文件传输未确认，请重试原文件");
    }
    if (!response.ok)
      throw new RequestFailure(response.status, "文件传输失败，请重试");
    state.uploadId = grant.uploadId;
    state.phase = "COMPLETE";
    checkpoint(state);
  }
  if (state.phase === "COMPLETE") {
    const receipt = await api<{ uploadId: string; fileId: string }>(
      files + "/uploads/" + state.uploadId + "/completion",
      { method: "POST", key: state.completeKey },
    );
    if (receipt.uploadId !== state.uploadId || !id(receipt.fileId))
      throw new Error("文件完成响应无效");
    state.fileId = receipt.fileId;
    state.phase = "SCAN";
    checkpoint(state);
  }
  if (state.phase === "SCAN") {
    const meta = await api<FileInfo>(files + "/files/" + state.fileId);
    if (meta.fileId !== state.fileId || meta.objectStatus !== "ACTIVE")
      throw new Error("文件已不可用，请重新上传");
    if (["PENDING_SCAN", "SCANNING"].includes(meta.scanStatus)) return;
    if (meta.scanStatus !== "AVAILABLE")
      throw new Error("文件未通过安全检查，请选择其他文件");
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
        fileId: state.fileId,
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
