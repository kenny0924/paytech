import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Space, Spin, Tag } from 'antd';
import {
  decodeDownloadGrant, downloadable, fileIdOfAddress, previewKind, preCheckFiles, rulesHint, safeTransferUrl, scanStatusColors, scanStatusLabels, sizeText, uploadOne,
} from './fileModel';
import type { FileRules, UploadItem, WorkflowPost } from './fileModel';
import { failureText, sendWorkflowCommand, workflowCommand } from './useWorkflowCommand';
import { workflowPaths } from './workflowApi';
import type { WorkflowFile } from './workflowTypes';

/** 工作流写请求（每次独立幂等键）。 */
const post: WorkflowPost = (path, body, decode) => sendWorkflowCommand(workflowCommand(path, 'POST', body), decode);

/** 按实例签发一次 60 秒下载地址；文件 ID 从文件地址解析，不保存临时地址。 */
export async function fetchDownloadUrl(instanceId: string, address: string): Promise<string> {
  const fileId = fileIdOfAddress(address);
  if (!fileId) throw new Error('文件地址无效');
  const grant = await post(workflowPaths.download(instanceId, fileId), undefined, decodeDownloadGrant);
  return safeTransferUrl(grant);
}

const errorText = (error: unknown) => error instanceof Error && !(error as { status?: number }).status ? error.message : failureText(error);

/**
 * 文件字段（启动表单、FORM 文件字段、上传文件动作共用）：选择 → 开始上传 → 直传 → 完成上传；值为文件地址数组。
 * 提交前可移除；扫描异步进行，提交时由服务端校验。
 */
export function FileUploadField({ value, onChange, rules = {}, disabled, known }: {
  value?: unknown; onChange?: (value: string[]) => void; rules?: FileRules; disabled?: boolean; known?: Map<string, WorkflowFile>;
}) {
  const ids = Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
  const [items, setItems] = useState<UploadItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const latest = useRef(ids); latest.current = ids;
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const busy = items.some(item => item.phase === 'UPLOADING' || item.phase === 'COMPLETING');
  function update(localId: string, patch: Partial<UploadItem>) {
    if (mounted.current) setItems(current => current.map(item => item.localId === localId ? { ...item, ...patch } : item));
  }
  async function choose(list: FileList | null) {
    const files = [...(list ?? [])];
    if (input.current) input.current.value = '';
    const problem = preCheckFiles(rules, ids.length, files);
    setError(problem);
    if (problem || !files.length) return;
    const queued = files.map(file => ({ file, item: { localId: crypto.randomUUID(), name: file.name, size: file.size, mime: file.type, phase: 'UPLOADING' as const } }));
    setItems(current => [...current, ...queued.map(entry => entry.item)]);
    for (const { file, item } of queued) {
      try {
        const fileAddress = await uploadOne(file, { uploads: workflowPaths.uploads, completion: workflowPaths.uploadCompletion }, post,
          (url, init) => fetch(url, init), phase => update(item.localId, { phase }));
        update(item.localId, { phase: 'DONE', fileAddress });
        onChange?.([...latest.current, fileAddress]);
      } catch (failure) {
        update(item.localId, { phase: 'FAILED', error: errorText(failure) });
      }
    }
  }
  function remove(address: string) {
    onChange?.(ids.filter(id => id !== address));
    setItems(current => current.filter(item => item.fileAddress !== address));
  }
  const local = new Map(items.filter(item => item.fileAddress).map(item => [item.fileAddress!, item]));
  return <div className="wf-upload">
    {!disabled && <div className="wf-upload-zone" role="button" tabIndex={0} onClick={() => !busy && input.current?.click()}
      onKeyDown={event => { if (event.key === 'Enter') input.current?.click(); }}
      onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); if (!busy) void choose(event.dataTransfer.files); }}>
      拖入文件或点击上传（{rulesHint(rules)}）
      <input ref={input} type="file" multiple hidden accept={rules.mimeTypes?.join(',')} onChange={event => void choose(event.target.files)} />
    </div>}
    {error && <Alert type="error" showIcon title={error} />}
    <ul className="wf-file-list">
      {ids.map(id => {
        const file = known?.get(id); const mine = local.get(id);
        return <li key={id}><span>{file?.displayName ?? mine?.name ?? <code>{id}</code>}
          <span className="muted"> · {sizeText(file?.sizeBytes ?? mine?.size)}</span></span>
          <Space size={4}>{file?.scanStatus ? <Tag color={scanStatusColors[file.scanStatus]}>{scanStatusLabels[file.scanStatus]}</Tag>
            : <Tag color="processing">已上传，等待扫描</Tag>}
            {!disabled && <Button size="small" type="link" danger onClick={() => remove(id)}>移除</Button>}</Space></li>;
      })}
      {items.filter(item => item.phase !== 'DONE').map(item => <li key={item.localId}><span>{item.name}<span className="muted"> · {sizeText(item.size)}</span></span>
        {item.phase === 'FAILED' ? <Space size={4}><span className="wf-danger-text">{item.error}</span>
          <Button size="small" type="link" onClick={() => setItems(current => current.filter(other => other.localId !== item.localId))}>关闭</Button></Space>
          : <Space size={4}><Spin size="small" />{item.phase === 'COMPLETING' ? '登记中' : '上传中'}</Space>}</li>)}
    </ul>
  </div>;
}

/** 只读文件列表：名称、大小、扫描状态；扫描通过的可下载或预览。 */
export function AttachmentList({ instanceId, addresses, known, onPreview, selected }: {
  instanceId: string; addresses: string[]; known: Map<string, WorkflowFile>; onPreview?: (file: WorkflowFile) => void; selected?: string;
}) {
  const [error, setError] = useState<string | null>(null);
  if (!addresses.length) return <span className="muted">没有文件</span>;
  async function download(address: string) {
    setError(null);
    try { window.open(await fetchDownloadUrl(instanceId, address), '_blank', 'noopener'); } catch (failure) { setError(errorText(failure)); }
  }
  return <div><ul className="wf-file-list">{addresses.map(id => {
    const file = known.get(id);
    return <li key={id} className={selected === id ? 'is-selected' : undefined}>
      <span>{file?.displayName ?? <code>{id}</code>}<span className="muted"> · {sizeText(file?.sizeBytes)}{file?.mimeType ? ` · ${file.mimeType}` : ''}</span></span>
      <Space size={4}>{file?.scanStatus ? <Tag color={scanStatusColors[file.scanStatus]}>{scanStatusLabels[file.scanStatus]}</Tag> : <Tag>未知</Tag>}
        {onPreview && file && downloadable(file) && <Button size="small" type="link" onClick={() => onPreview(file)}>查看</Button>}
        <Button size="small" type="link" disabled={!downloadable(file)} title={downloadable(file) ? undefined : '扫描通过后才能下载'}
          onClick={() => void download(id)}>下载</Button></Space></li>;
  })}</ul>{error && <Alert type="error" showIcon title={error} />}</div>;
}

/**
 * 文件查看：显示原文件名、类型、大小；支持内嵌时（INLINE_PREVIEW_SUPPORTED）PDF 用 iframe、图片用 img，
 * 否则提供“下载查看”，每次点击重新签发 60 秒地址后在新窗口打开。临时地址不保存、不改写。
 */
export function FilePreview({ instanceId, file }: { instanceId: string; file: WorkflowFile | undefined }) {
  const kind = previewKind(file?.mimeType);
  const [state, setState] = useState<{ fileId: string; url?: string; error?: string }>();
  const [opening, setOpening] = useState(false);
  useEffect(() => {
    if (!file || kind === 'other') { setState(undefined); return; }
    let active = true; setState({ fileId: file.fileId });
    fetchDownloadUrl(instanceId, file.fileAddress).then(url => { if (active) setState({ fileId: file.fileId, url }); })
      .catch(failure => { if (active) setState({ fileId: file.fileId, error: errorText(failure) }); });
    return () => { active = false; };
  }, [instanceId, file?.fileId, kind]);
  if (!file) return <span className="muted">选择左侧文件查看</span>;
  async function open() {
    if (!file) return;
    setOpening(true); setState(undefined);
    try { window.open(await fetchDownloadUrl(instanceId, file.fileAddress), '_blank', 'noopener'); }
    catch (failure) { setState({ fileId: file.fileId, error: errorText(failure) }); }
    finally { setOpening(false); }
  }
  const info = <dl className="wf-output wf-file-info">
    <div><dt>文件名</dt><dd>{file.displayName ?? file.fileId}</dd></div>
    <div><dt>类型</dt><dd>{file.mimeType ?? '—'}</dd></div>
    <div><dt>大小</dt><dd>{sizeText(file.sizeBytes)}</dd></div>
  </dl>;
  if (kind === 'other') return <div className="wf-file-preview">{info}
    {state?.error && <Alert type="error" showIcon title={state.error} />}
    <Button type="primary" loading={opening} disabled={!downloadable(file)} onClick={() => void open()}>下载查看</Button>
    <div className="muted">文件服务暂不支持内嵌预览；每次点击重新取 60 秒有效地址。</div></div>;
  if (state?.error) return <Alert type="error" showIcon title={state.error} />;
  if (!state?.url) return <Spin />;
  return <div className="wf-file-preview">
    {kind === 'pdf' ? <iframe title={file.displayName ?? file.fileId} src={state.url} />
      : <img alt={file.displayName ?? file.fileId} src={state.url} />}
  </div>;
}
