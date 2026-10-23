import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { decodeUploadGrant, decodeUploadReceipt, safeTransferUrl, safeUploadHeaders } from '../workflow/fileModel';
import { classifyFailure, localizationCommand, sendCommand } from './commandModel';
import type { Command, CommandMethod, CommandResult } from './commandModel';
import { localizationPaths } from './localizationApi';

export { classifyFailure, isFileNotReady, localizationCommand, sendCommand, withScanRetry } from './commandModel';
export type { CommandFailure, CommandMethod, CommandResult } from './commandModel';

/** 单飞写命令：403 刷新授权；失败按 classifyFailure 返回，other 类同时弹出通用错误通知。 */
export function useLocalizationCommand(onAuthorizationChanged: () => void) {
  const notify = useErrorNotification();
  const [saving, setSaving] = useState(false);
  const busy = useRef(false); const last = useRef<Command>(undefined); const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const run = useCallback(async <T>(path: string, method: CommandMethod, body: unknown, decode: (value: unknown) => T,
    failureTitle: string): Promise<CommandResult<T> | null> => {
    if (busy.current) return null;
    busy.current = true; setSaving(true);
    const command = localizationCommand(path, method, body, last.current); last.current = command;
    try {
      const value = await sendCommand(command, decode);
      last.current = undefined;
      return { ok: true, value };
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      const failure = classifyFailure(error);
      // 冲突后的重试是新的意图：换新的幂等键。
      if (failure.kind === 'conflict') last.current = undefined;
      if (failure.kind === 'other') notify(error, failureTitle);
      return { ok: false, failure };
    } finally { busy.current = false; if (mounted.current) setSaving(false); }
  }, [notify, onAuthorizationChanged]);
  return { saving, run };
}

/** 上传未完成的原因；页面按此选择文案（底层错误信息不直接展示）。 */
export class ImportUploadError extends Error {
  readonly reason: 'transfer' | 'rejected';
  constructor(reason: 'transfer' | 'rejected') { super(reason); this.reason = reason; }
}

/**
 * 导入文件上传（卡 B：BFF 直调文件服务，2026-10-22 文件上传原则）：
 * POST /uploads {purpose, fileName, sizeBytes} → 按 PUT 授权直传（不带会话与凭据）→ POST /uploads/{uploadId}/completion，返回文件 ID。
 * 完成时扫描已判定拒绝或隔离则立即失败；仍在检查中由调用方在导入时重试。
 */
export async function uploadImportFile(file: File, purpose: 'KEY_IMPORT' | 'TRANSLATION_IMPORT', transport: typeof fetch = (...args) => fetch(...args)): Promise<string> {
  const grant = await sendCommand(localizationCommand(localizationPaths.uploads, 'POST', { purpose, fileName: file.name, sizeBytes: String(file.size) }), decodeUploadGrant);
  if (grant.authorization.method !== 'PUT') throw new ImportUploadError('transfer');
  let url: string; let headers: Headers;
  try { url = safeTransferUrl(grant.authorization); headers = safeUploadHeaders(grant.authorization.headers); }
  catch { throw new ImportUploadError('transfer'); }
  let response: Response;
  try { response = await transport(url, { method: 'PUT', body: file, headers, credentials: 'omit', redirect: 'error', referrerPolicy: 'no-referrer' }); }
  catch { throw new ImportUploadError('transfer'); }
  if (!response.ok) throw new ImportUploadError('transfer');
  const receipt = await sendCommand(localizationCommand(localizationPaths.uploadCompletion(grant.uploadId), 'POST', undefined), value => {
    const item = value !== null && typeof value === 'object' ? value as Record<string, unknown> : {};
    return { ...decodeUploadReceipt(value), scanStatus: typeof item.scanStatus === 'string' ? item.scanStatus : null };
  });
  if (receipt.uploadId !== grant.uploadId) throw new ImportUploadError('transfer');
  if (receipt.scanStatus === 'REJECTED' || receipt.scanStatus === 'QUARANTINED') throw new ImportUploadError('rejected');
  return receipt.fileId;
}
