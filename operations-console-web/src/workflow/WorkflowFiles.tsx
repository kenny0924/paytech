import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Modal, Space, Spin, Tag } from 'antd';
import { EyeOutlined, FileImageOutlined, FileOutlined, FilePdfOutlined, SafetyCertificateOutlined, StopOutlined } from '@ant-design/icons';
import {
  decodeDownloadGrant, downloadable, fileIdOfAddress, previewKind, preCheckFiles, rulesHint, safeTransferUrl, SCAN_FAILED_TEXT, scanFailed, scanPending, scanStatusColors, scanStatusLabels, sizeText, uploadOne,
} from './fileModel';
import type { FileDisposition, FileRules, UploadItem, WorkflowPost } from './fileModel';
import { failureText, sendWorkflowCommand, workflowCommand } from './useWorkflowCommand';
import { workflowPaths } from './workflowApi';
import type { WorkflowFile } from './workflowTypes';

/** 工作流写请求（每次独立幂等键）。 */
const post: WorkflowPost = (path, body, decode) => sendWorkflowCommand(workflowCommand(path, 'POST', body), decode);

/**
 * 按实例签发一次 60 秒下载地址；文件 ID 从文件地址解析，不保存临时地址。
 * 返回实际生效的下载方式：请求 INLINE 但文件服务不满足条件时为 ATTACHMENT。
 */
export async function fetchDownloadUrl(instanceId: string, address: string, disposition?: FileDisposition)
  : Promise<{ url: string; disposition: FileDisposition }> {
  const fileId = fileIdOfAddress(address);
  if (!fileId) throw new Error('文件地址无效');
  const grant = await post(workflowPaths.download(instanceId, fileId, disposition), undefined, decodeDownloadGrant);
  return { url: safeTransferUrl(grant), disposition: grant.disposition };
}

const errorText = (error: unknown) => error instanceof Error && !(error as { status?: number }).status ? error.message : failureText(error);

/**
 * 文件字段（启动表单、FORM 文件字段、上传文件动作共用）：选择 → 开始上传 → 直传 → 完成上传；值为文件地址数组。
 * 提交前可移除；扫描异步进行：扫描中只作提示、不挡提交；已确认未通过（实例元数据或服务端 422 报告）的文件标红，须删除后重新上传。
 */
export function FileUploadField({ value, onChange, rules = {}, disabled, known, failed }: {
  value?: unknown; onChange?: (value: string[]) => void; rules?: FileRules; disabled?: boolean; known?: Map<string, WorkflowFile>;
  /** 服务端已报告未通过安全检查的文件地址（刚上传的文件没有扫描元数据）。 */
  failed?: ReadonlySet<string>;
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
        const rejected = failed?.has(id) || scanFailed(file);
        return <li key={id} className={rejected ? 'is-scan-failed' : undefined}><span>{file?.displayName ?? mine?.name ?? <code>{id}</code>}
          <span className="muted"> · {sizeText(file?.sizeBytes ?? mine?.size)}</span></span>
          <Space size={4}>{rejected ? <span className="wf-danger-text">{SCAN_FAILED_TEXT}</span>
            : file?.scanStatus ? <Tag color={scanStatusColors[file.scanStatus]}>{scanStatusLabels[file.scanStatus]}</Tag>
            : <span className="muted">已上传，安全检查在后台进行</span>}
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
    try { window.open((await fetchDownloadUrl(instanceId, address, 'ATTACHMENT')).url, '_blank', 'noopener'); } catch (failure) { setError(errorText(failure)); }
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

const fileIcon = (mime: string | null | undefined) => {
  const kind = previewKind(mime);
  return kind === 'pdf' ? <FilePdfOutlined /> : kind === 'image' ? <FileImageOutlined /> : <FileOutlined />;
};

/**
 * 审批前查看提交的文件（原型 15）：文件卡片显示名称、大小与安全检查；通过检查的可「预览」（弹窗内嵌，沿用 INLINE 预览），
 * 未通过的显示「未通过安全检查 · 不可下载」且不提供预览与下载，检查中的只提示稍后再看。
 */
export function FileCards({ instanceId, addresses, known }: { instanceId: string; addresses: string[]; known: Map<string, WorkflowFile> }) {
  const [previewing, setPreviewing] = useState<WorkflowFile | null>(null);
  if (!addresses.length) return <span className="muted">没有文件</span>;
  return <>
    <div className="wf-file-cards">{addresses.map(id => {
      const file = known.get(id);
      const failed = scanFailed(file);
      return <div key={id} className={`wf-file-card${failed ? ' is-scan-failed' : ''}`}>
        <span className={`wf-file-card-icon is-${previewKind(file?.mimeType)}`} aria-hidden>{fileIcon(file?.mimeType)}</span>
        <div className="wf-file-card-body">
          <strong className="wf-ellipsis" title={file?.displayName ?? id}>{file?.displayName ?? <code>{id}</code>}</strong>
          <span className="muted">{sizeText(file?.sizeBytes)}{downloadable(file) && <> · <SafetyCertificateOutlined className="wf-scan-ok" /> {scanStatusLabels.AVAILABLE}</>}</span>
        </div>
        {failed ? <span className="wf-danger-text wf-file-card-state"><StopOutlined /> 未通过安全检查 · 不可下载</span>
          : downloadable(file) ? <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => setPreviewing(file!)}>预览</Button>
          : <span className="muted wf-file-card-state"><SafetyCertificateOutlined /> {scanPending(file) ? '安全检查中' : '暂不可查看'}</span>}
      </div>;
    })}</div>
    {previewing && <Modal open width={880} title={previewing.displayName ?? previewing.fileId} footer={null} destroyOnHidden onCancel={() => setPreviewing(null)}>
      <FilePreview instanceId={instanceId} file={previewing} /></Modal>}
  </>;
}

/**
 * 文件查看：显示原文件名、类型、大小；PDF 用 iframe、PNG / JPEG 用 img，以 INLINE 签发地址内嵌显示。
 * 其他类型或文件服务把 INLINE 降为 ATTACHMENT 时提供“下载查看”，每次点击重新签发 60 秒附件地址后在新窗口打开。
 * 临时地址不保存、不改写；iframe 不加 sandbox（浏览器 PDF 查看器在 sandbox 中无法加载），只禁止 Referrer。
 */
export function FilePreview({ instanceId, file }: { instanceId: string; file: WorkflowFile | undefined }) {
  const requested = previewKind(file?.mimeType);
  const [state, setState] = useState<{ fileId: string; url?: string; degraded?: boolean; error?: string }>();
  const [opening, setOpening] = useState(false);
  useEffect(() => {
    if (!file || requested === 'other') { setState(undefined); return; }
    let active = true; setState({ fileId: file.fileId });
    fetchDownloadUrl(instanceId, file.fileAddress, 'INLINE').then(grant => {
      if (!active) return;
      // 文件服务降为附件时不内嵌，改为“下载查看”。
      setState(grant.disposition === 'INLINE' ? { fileId: file.fileId, url: grant.url } : { fileId: file.fileId, degraded: true });
    }).catch(failure => { if (active) setState({ fileId: file.fileId, error: errorText(failure) }); });
    return () => { active = false; };
  }, [instanceId, file?.fileId, requested]);
  if (!file) return <span className="muted">选择左侧文件查看</span>;
  // 切换文件后、新地址返回前，上一文件的状态不再使用。
  const current = state?.fileId === file.fileId ? state : undefined;
  const kind = current?.degraded ? 'other' : requested;
  async function open() {
    if (!file) return;
    setOpening(true); setState(current => current?.degraded ? { fileId: file.fileId, degraded: true } : undefined);
    try { window.open((await fetchDownloadUrl(instanceId, file.fileAddress, 'ATTACHMENT')).url, '_blank', 'noopener'); }
    catch (failure) { setState(current => ({ fileId: file.fileId, degraded: current?.degraded, error: errorText(failure) })); }
    finally { setOpening(false); }
  }
  const info = <dl className="wf-output wf-file-info">
    <div><dt>文件名</dt><dd>{file.displayName ?? file.fileId}</dd></div>
    <div><dt>类型</dt><dd>{file.mimeType ?? '—'}</dd></div>
    <div><dt>大小</dt><dd>{sizeText(file.sizeBytes)}</dd></div>
  </dl>;
  if (kind === 'other') return <div className="wf-file-preview">{info}
    {current?.error && <Alert type="error" showIcon title={current.error} />}
    <Button type="primary" loading={opening} disabled={!downloadable(file)} onClick={() => void open()}>下载查看</Button>
    <div className="muted">该文件不支持内嵌预览；每次点击重新取 60 秒有效地址。</div></div>;
  if (current?.error) return <Alert type="error" showIcon title={current.error} />;
  if (!current?.url) return <Spin />;
  return <div className="wf-file-preview">
    {kind === 'pdf' ? <iframe title={file.displayName ?? file.fileId} src={current.url} referrerPolicy="no-referrer" />
      : <img alt={file.displayName ?? file.fileId} src={current.url} referrerPolicy="no-referrer" />}
  </div>;
}
