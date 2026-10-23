import { useState } from 'react';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useT } from '../i18n/react';
import { decodeDownloadGrant, fileRecordDownloadPath } from './fileRecords';
import type { FileDownloadGrant } from './fileRecords';

/** 申请一次下载授权：无请求体的 POST 仍按 BFF 写入口要求声明 JSON 内容类型；每次申请都会写下载审计。 */
export function authorizeFileDownload(fileId: string, versionNo?: number, signal?: AbortSignal): Promise<FileDownloadGrant> {
  return session.request(fileRecordDownloadPath(fileId, versionNo), { method: 'POST', headers: { 'Content-Type': 'application/json' }, signal },
    value => decodeDownloadGrant(value));
}

/**
 * 文件记录下载：每次点击向 BFF 申请最长 60 秒的地址，随即以附件方式下载；地址不保存、不展示
 * <li>403 刷新导航权限；版本不可下载（409）、审计不可用（503）按统一提示展示。</li>
 */
export function useFileRecordDownload(onAuthorizationChanged: () => void) {
  const notify = useErrorNotification(); const t = useT('file');
  const [pending, setPending] = useState<string>();
  async function download(fileId: string, displayName: string, versionNo?: number) {
    if (pending) return;
    const key = `${fileId}:${versionNo ?? 'current'}`; setPending(key);
    try {
      const grant = await authorizeFileDownload(fileId, versionNo);
      const link = document.createElement('a');
      link.href = grant.url; link.rel = 'noopener noreferrer'; link.download = displayName;
      document.body.appendChild(link); link.click(); link.remove();
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      notify(error, error instanceof ApiError && error.status === 409 ? t('download.versionUnavailable') : t('download.failed'));
    } finally { setPending(undefined); }
  }
  return { pending, download, isPending: (fileId: string, versionNo?: number) => pending === `${fileId}:${versionNo ?? 'current'}` };
}
