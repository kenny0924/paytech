/**
 * 直传文件服务的进度传输：形状同 fetch，供 `runUpload` 使用
 * <li>fetch 拿不到上传进度，这里用 XMLHttpRequest；不带 Cookie（withCredentials=false），不跟随重定向之外的行为由浏览器保证。</li>
 * <li>取消只中止当前传输；已完成的阶段坐标仍按原恢复规则保存。</li>
 */
export type UploadPhase = "idle" | "uploading" | "checking" | "done" | "failed";

export function progressTransport(onProgress: (ratio: number) => void, signal: AbortSignal): typeof fetch {
  return ((input: RequestInfo | URL, init?: RequestInit) => new Promise<Response>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open(init?.method ?? "PUT", String(input));
    xhr.withCredentials = false;
    new Headers(init?.headers).forEach((value, name) => xhr.setRequestHeader(name, value));
    xhr.upload.onprogress = (event) => { if (event.lengthComputable) onProgress(event.loaded / event.total); };
    xhr.onload = () => resolve(new Response(null, { status: xhr.status === 0 ? 599 : xhr.status }));
    xhr.onerror = () => reject(new TypeError("network"));
    xhr.onabort = () => reject(new DOMException("aborted", "AbortError"));
    if (signal.aborted) { reject(new DOMException("aborted", "AbortError")); return; }
    signal.addEventListener("abort", () => xhr.abort(), { once: true });
    xhr.send(init?.body as XMLHttpRequestBodyInit | null ?? null);
  })) as typeof fetch;
}

/** 前端预检：类型与大小；真伪与内容由文件扫描和审核判断。 */
export const acceptedTypes = ["application/pdf", "image/jpeg", "image/png"];
export const maxBytes = 10 * 1024 * 1024;
export function fileProblem(file: File): "type" | "size" | "empty" | null {
  if (file.size === 0) return "empty";
  if (file.size > maxBytes) return "size";
  if (file.type && !acceptedTypes.includes(file.type)) return "type";
  return null;
}

/** 文件大小：1 位小数的 MB / KB。 */
export function fileSize(bytes: number | string, locale: string): string {
  const value = Number(bytes);
  if (!Number.isFinite(value) || value < 0) return "";
  const unit = value >= 1024 * 1024 ? "megabyte" : "kilobyte";
  const amount = value / (unit === "megabyte" ? 1024 * 1024 : 1024);
  return new Intl.NumberFormat(locale, { style: "unit", unit, maximumFractionDigits: 1, unitDisplay: "short" }).format(amount);
}

/** 文件类型短标：PDF / JPG / PNG。 */
export function fileKind(name: string, mime: string): string {
  if (mime === "application/pdf" || /\.pdf$/i.test(name)) return "PDF";
  if (mime === "image/png" || /\.png$/i.test(name)) return "PNG";
  if (mime === "image/jpeg" || /\.jpe?g$/i.test(name)) return "JPG";
  return "FILE";
}

/**
 * 页面内进行中的材料上传登记（按恢复坐标键）
 * <li>active：正在传输或检查的槽位，用于在离开材料页前提示等待。</li>
 * <li>claimed：已挂载、自己负责推进的槽位；未被认领的恢复坐标由申请页后台续传，避免离开材料页后上传永远不关联。</li>
 */
export function createUploadRegistry() {
  const active = new Set<string>(), claimed = new Set<string>(), listeners = new Set<() => void>();
  let version = 0;
  const publish = () => { version++; listeners.forEach((listener) => listener()); };
  return {
    setActive(key: string, on: boolean) { if (on === active.has(key)) return; if (on) active.add(key); else active.delete(key); publish(); },
    claim(key: string) { claimed.add(key); },
    release(key: string) { claimed.delete(key); active.delete(key); publish(); },
    isClaimed: (key: string) => claimed.has(key),
    activeCount: () => active.size,
    version: () => version,
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener); }; },
  };
}
export const uploads = createUploadRegistry();
/** 本标签页某账号某申请的上传恢复坐标键前缀。 */
export const uploadKeyPrefix = (accountId: string | undefined, applicationId: string) => "client-upload:" + accountId + ":" + applicationId + ":";
