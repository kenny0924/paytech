import type { UploadAuthorization } from './binLibrary';
import { translator } from '../i18n/index';

const t = translator('card');

/** 浏览器不允许脚本设置的请求头，由浏览器自行生成。 */
const browserManagedHeaders = new Set(['content-length', 'host', 'connection']);

/**
 * 按 Platform 签发的临时 PUT 授权把文件直传对象存储（不经 Gateway），回报上传字节进度
 * <li>授权要求的请求头原样携带；2xx 视为成功，其余状态、网络错误与中止均以失败返回，由调用方提示重试。</li>
 * <li>地址含临时签名，不写入错误消息。</li>
 */
export function putFile(authorization: UploadAuthorization, file: Blob, onProgress: (loaded: number, total: number) => void,
  signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open('PUT', authorization.url);
    for (const [name, value] of Object.entries(authorization.headers)) {
      if (!browserManagedHeaders.has(name.toLowerCase())) request.setRequestHeader(name, value);
    }
    request.upload.onprogress = event => onProgress(event.loaded, event.lengthComputable ? event.total : file.size);
    request.onload = () => request.status >= 200 && request.status < 300 ? resolve()
      : reject(new Error(t('upload.rejected', { status: request.status })));
    request.onerror = () => reject(new Error(t('upload.connectionFailed')));
    request.onabort = () => reject(new DOMException(t('upload.cancelled'), 'AbortError'));
    if (signal.aborted) { request.abort(); return; }
    signal.addEventListener('abort', () => request.abort(), { once: true });
    request.send(file);
  });
}
