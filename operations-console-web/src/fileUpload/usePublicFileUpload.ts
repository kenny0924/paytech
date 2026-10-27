import { useEffect, useRef, useState } from 'react';
import { session } from '../api/session';
import { decodeUploadStart } from '../card/binLibrary';
import { putFile } from '../card/directUpload';
import { translator } from '../i18n/index';
import {
  canUploadFile, decodeUploadCompletionReceipt, decodeUploadConstraints, decodeUploadedFileFor, fileUploadPaths, pollUploadedFile,
  startUploadBody, uploadFileIssue, uploadRejectionReason,
} from './fileUpload';
import type { UploadConstraints, UploadedFile } from './fileUpload';
import { useUploadCommand } from './UploadPanel';

const t = translator('file');

export type PublicUploadState =
  | { kind: 'idle' }
  | { kind: 'running'; name: string; percent: number }
  | { kind: 'done'; file: UploadedFile }
  | { kind: 'failed'; name: string; reason: string };

/**
 * 其他页面（例如币种图标）复用"文件上传"页的公开上传链路：限制提示 → 开始上传 → 直传对象存储 → 完成上传 → 等待检查通过
 * <li>只有当前账号有文件上传按钮（OPERATIONS_FILE_UPLOAD_CREATE）时可用；限制在首次上传时读取并缓存。</li>
 * <li>检查通过才返回文件（含文件地址 /files/v1/{fileId} 与公开地址）；未通过、超时或被拒绝返回原因，调用方不保存地址。</li>
 */
export function usePublicFileUpload(resources: readonly { resourceType: string; resourceCode: string }[], onAuthorizationChanged: () => void) {
  const run = useUploadCommand(onAuthorizationChanged);
  const [state, setState] = useState<PublicUploadState>({ kind: 'idle' });
  const constraints = useRef<UploadConstraints | null>(null); const abort = useRef<AbortController | null>(null);
  useEffect(() => () => abort.current?.abort(), []);
  const enabled = canUploadFile(resources);

  async function upload(file: File): Promise<UploadedFile | null> {
    abort.current?.abort();
    const controller = new AbortController(); abort.current = controller; const { signal } = controller;
    let completing = false;
    setState({ kind: 'running', name: file.name, percent: 0 });
    try {
      constraints.current ??= await session.request(fileUploadPaths.constraints, { signal }, decodeUploadConstraints);
      const issue = constraints.current.configured ? uploadFileIssue(file, constraints.current) : t('upload.panel.disabled');
      if (issue) { setState({ kind: 'failed', name: file.name, reason: issue }); return null; }
      const started = await run(fileUploadPaths.uploads, startUploadBody(file), decodeUploadStart, signal);
      await putFile(started.authorization, file, (loaded, total) =>
        setState({ kind: 'running', name: file.name, percent: total > 0 ? Math.floor(loaded * 100 / total) : 0 }), signal);
      completing = true;
      const completion = await run(fileUploadPaths.uploadCompletion(started.uploadId), undefined, decodeUploadCompletionReceipt, signal);
      completing = false;
      const outcome = await pollUploadedFile(pollSignal => session.request(fileUploadPaths.file(completion.fileId), { signal: pollSignal },
        decodeUploadedFileFor(completion.fileId)), { signal });
      if (outcome.kind !== 'AVAILABLE') {
        setState({ kind: 'failed', name: file.name, reason: outcome.kind === 'FAILED' ? t('upload.result.failed') : t('upload.result.timeout') });
        return null;
      }
      setState({ kind: 'done', file: outcome.file });
      return outcome.file;
    } catch (error) {
      if (signal.aborted) return null;
      setState({ kind: 'failed', name: file.name, reason: uploadRejectionReason(error, completing) });
      return null;
    }
  }
  return { enabled, state, upload, reset: () => { abort.current?.abort(); setState({ kind: 'idle' }); } };
}
