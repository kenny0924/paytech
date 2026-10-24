import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent, DragEvent } from 'react';
import { Alert, Button, Progress, Space, Spin, Tag, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import { decodeUploadStart } from '../card/binLibrary';
import { putFile } from '../card/directUpload';
import { configurationCommand } from '../configuration/configurationModel';
import type { ConfigurationCommand } from '../configuration/configurationModel';
import { ScanTag } from '../fileRecords/FileRecordParts';
import { accessLevelColors, accessLevelLabel } from '../fileStorage/fileStorage';
import { byteSizeLabel } from '../fileStorage/scanPolicy';
import { useT } from '../i18n/react';
import {
  acceptAttribute, decodeUploadCompletionReceipt, decodeUploadedFileFor, fileRecordHref, fileUploadPaths, pollUploadedFile, routeRuleLabel, startUploadBody, uploadFileIssue, uploadRejectionReason,
  uploadValidityLabel, visibleAddresses,
} from './fileUpload';
import type { UploadConstraints, UploadedFile } from './fileUpload';

/**
 * 顶部限制提示：公开标签、目标存储、写入规则、检查策略、允许类型、大小上限与上传期限
 * <li>未配置时标红：有存储管理页面时提供「前往存储管理」，否则提示联系管理员；未取到时提示重试。</li>
 */
export function ConstraintsBar({ constraints, failed, canOpenStorage, onRetry }: {
  constraints: UploadConstraints | null; failed: boolean; canOpenStorage: boolean; onRetry: () => void;
}) {
  const t = useT('file'), tCommon = useT('common'); const navigate = useNavigate();
  if (failed) return <Alert type="warning" showIcon className="file-upload-constraints" title={t('upload.constraints.loadFailed')}
    action={<Button size="small" onClick={onRetry}>{tCommon('action.retry')}</Button>} />;
  if (!constraints) return <div className="file-upload-constraints is-loading"><Spin size="small" /></div>;
  if (!constraints.configured) return canOpenStorage
    ? <Alert type="error" showIcon className="file-upload-constraints" title={t('upload.constraints.notConfigured')}
      action={<Button size="small" onClick={() => void navigate('/file-storage')}>{t('upload.constraints.goToStorage')}</Button>} />
    : <Alert type="error" showIcon className="file-upload-constraints" title={t('upload.constraints.notConfiguredContact')} />;
  const item = (label: string, value: string, mono = true) => <span className="file-upload-constraint">
    <span className="muted">{label}</span><span className={mono ? 'mono' : undefined}>{value}</span></span>;
  return <div className="file-upload-constraints is-configured">
    <Tag color={accessLevelColors[constraints.accessLevel]}>{accessLevelLabel(constraints.accessLevel)}</Tag>
    {item(t('upload.constraints.target'), `${constraints.instanceCode} / ${constraints.bucketName}`)}
    {item(t('upload.constraints.rule'), routeRuleLabel(constraints.routeSourceSystem!), false)}
    {item(t('upload.constraints.policy'), t('upload.constraints.policyRevision', { code: constraints.scanPolicyCode, revisionNo: constraints.scanPolicyRevisionNo }))}
    {item(t('upload.constraints.allowed'), constraints.allowedMimeTypes.join(', '))}
    {item(t('upload.constraints.maxSize'), byteSizeLabel(constraints.maxSizeBytes!), false)}
    {item(t('upload.constraints.validity'), uploadValidityLabel(constraints.maxUploadSeconds!), false)}
  </div>;
}

type Stage =
  | { kind: 'idle' }
  | { kind: 'rejected'; name: string; reason: string }
  | { kind: 'running'; name: string; step: 'start' | 'upload' | 'complete' | 'check'; loaded: number; total: number }
  | { kind: 'error'; name: string; reason: string }
  | { kind: 'done'; outcome: 'AVAILABLE' | 'FAILED' | 'TIMEOUT'; name: string; file: UploadedFile | null };

/** 写命令：JSON Content-Type；同一目标与正文的人工重试复用 Idempotency-Key；403 刷新授权。 */
function useUploadCommand(onAuthorizationChanged: () => void) {
  const last = useRef<ConfigurationCommand>(undefined);
  return async function run<T>(path: string, body: unknown, decode: (value: unknown) => T, signal: AbortSignal): Promise<T> {
    const command = configurationCommand(path, 'POST', body ?? {}, last.current); last.current = command;
    try {
      const result = await session.request<T>(path, { method: 'POST', body: body === undefined ? undefined : command.body, signal,
        // 无 Body 的命令（完成上传）同样声明 JSON，BFF 入站对写方法统一核对媒体类型。
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key } }, decode);
      last.current = undefined;
      return result;
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      throw error;
    }
  };
}

/**
 * 上传区与结果卡
 * <li>选择或拖入单个文件 → 前端预校验（被拦截不调接口）→ 开始上传 → 直传对象存储（带进度）→ 完成上传 → 每秒查一次检查结果，最多 30 秒。</li>
 * <li>只有检查通过才显示公开地址、带版本的地址与文件地址；未通过只显示状态与通用提示（有文件记录页面时引导去详情查看）；超时提示稍后在最近上传中查看。</li>
 */
export function UploadWorkspace({ constraints, enabled, canOpenRecords, onUploaded, onAuthorizationChanged }: {
  constraints: UploadConstraints | null; enabled: boolean; canOpenRecords: boolean; onUploaded: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('file'), tCommon = useT('common');
  const run = useUploadCommand(onAuthorizationChanged);
  const [stage, setStage] = useState<Stage>({ kind: 'idle' }); const [dragging, setDragging] = useState(false);
  const abort = useRef<AbortController | null>(null); const input = useRef<HTMLInputElement>(null);
  useEffect(() => () => abort.current?.abort(), []);
  const running = stage.kind === 'running';
  const disabled = !enabled || running;

  async function upload(file: File) {
    if (disabled || !constraints) return;
    const issue = uploadFileIssue(file, constraints);
    if (issue) { setStage({ kind: 'rejected', name: file.name, reason: issue }); return; }
    const controller = new AbortController(); abort.current = controller; const { signal } = controller;
    const current: { step: 'start' | 'upload' | 'complete' | 'check' } = { step: 'start' };
    const step = (next: typeof current.step, loaded = 0) => {
      current.step = next; setStage({ kind: 'running', name: file.name, step: next, loaded, total: file.size });
    };
    try {
      step('start');
      const started = await run(fileUploadPaths.uploads, startUploadBody(file), decodeUploadStart, signal);
      step('upload');
      await putFile(started.authorization, file, loaded => step('upload', loaded), signal);
      step('complete');
      const completion = await run(fileUploadPaths.uploadCompletion(started.uploadId), undefined, decodeUploadCompletionReceipt, signal);
      step('check');
      const outcome = await pollUploadedFile(pollSignal => session.request(fileUploadPaths.file(completion.fileId), { signal: pollSignal },
        decodeUploadedFileFor(completion.fileId)), { signal });
      setStage({ kind: 'done', outcome: outcome.kind, name: file.name, file: outcome.file });
      onUploaded();
    } catch (error) {
      if (signal.aborted) return;
      const rejected = error instanceof ApiError && error.status === 422;
      setStage({ kind: rejected ? 'rejected' : 'error', name: file.name, reason: uploadRejectionReason(error, current.step === 'complete') });
    }
  }

  function cancel() { abort.current?.abort(); setStage({ kind: 'idle' }); }
  function pick(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]; event.target.value = '';
    if (file) void upload(file);
  }
  function drop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault(); setDragging(false);
    const file = event.dataTransfer.files[0];
    if (file && event.dataTransfer.files.length === 1) void upload(file);
    else if (file) setStage({ kind: 'rejected', name: file.name, reason: t('upload.issue.singleFile') });
  }
  const percent = stage.kind === 'running' && stage.total > 0 ? Math.floor(stage.loaded * 100 / stage.total) : 0;

  return <div className="file-upload-workspace">
    <section className="file-upload-panel">
      <Alert type="warning" showIcon title={t('upload.panel.publicWarning')} />
      <div className={['file-upload-drop', dragging ? 'is-dragging' : '', disabled ? 'is-disabled' : ''].join(' ').trim()}
        onDragOver={event => { event.preventDefault(); if (!disabled) setDragging(true); }} onDragLeave={() => setDragging(false)}
        onDrop={event => disabled ? event.preventDefault() : drop(event)}>
        <div>{t('upload.panel.drop')}</div>
        <Button type="primary" disabled={disabled} onClick={() => input.current?.click()}>{t('upload.panel.select')}</Button>
        <input ref={input} type="file" hidden disabled={disabled} accept={constraints ? acceptAttribute(constraints) : undefined} onChange={pick} />
        <div className="muted">{enabled ? t('upload.panel.single') : t('upload.panel.disabled')}</div>
      </div>
      {stage.kind === 'running' && <div className="file-upload-progress">
        <div className="file-upload-progress-head"><span className="file-ellipsis mono" title={stage.name}>{stage.name}</span>
          <Button size="small" onClick={cancel}>{tCommon('action.cancel')}</Button></div>
        <span className="muted">{stage.step === 'start' ? t('upload.stage.starting') : stage.step === 'upload' ? t('upload.stage.uploading')
          : stage.step === 'complete' ? t('upload.stage.completing') : t('upload.stage.checking')}</span>
        <Progress percent={stage.step === 'upload' ? percent : stage.step === 'start' ? 0 : 100} status="active"
          format={value => stage.step === 'upload' ? `${byteSizeLabel(stage.loaded)} / ${byteSizeLabel(stage.total)} · ${value}%` : `${value}%`} />
      </div>}
    </section>
    <section className="file-upload-result">
      <div className="file-upload-result-head"><strong>{t('upload.result.title')}</strong>
        {stage.kind === 'done' && stage.file && <ScanTag status={stage.file.scanStatus} />}</div>
      <UploadResult stage={stage} canOpenRecords={canOpenRecords} onRetry={() => input.current?.click()} retryDisabled={disabled} />
    </section>
  </div>;
}

function UploadResult({ stage, canOpenRecords, onRetry, retryDisabled }: { stage: Stage; canOpenRecords: boolean; onRetry: () => void; retryDisabled: boolean }) {
  const t = useT('file'); const navigate = useNavigate();
  const again = <Button disabled={retryDisabled} onClick={onRetry}>{t('upload.result.uploadAgain')}</Button>;
  if (stage.kind === 'idle') return <div className="muted">{t('upload.result.empty')}</div>;
  if (stage.kind === 'running') return <div className="muted">{t('upload.result.inProgress')}</div>;
  if (stage.kind === 'rejected') return <Space orientation="vertical" className="file-upload-result-body">
    <Alert type="error" showIcon title={t('upload.result.rejected', { name: stage.name })} description={stage.reason} />{again}</Space>;
  if (stage.kind === 'error') return <Space orientation="vertical" className="file-upload-result-body">
    <Alert type="error" showIcon title={t('upload.result.error', { name: stage.name })} description={stage.reason} />{again}</Space>;
  const file = stage.file;
  if (stage.outcome === 'TIMEOUT') return <Space orientation="vertical" className="file-upload-result-body">
    <FileMeta name={stage.name} file={file} /><Alert type="info" showIcon title={t('upload.result.timeout')} /></Space>;
  if (stage.outcome === 'FAILED') return <Space orientation="vertical" className="file-upload-result-body">
    <FileMeta name={stage.name} file={file} />
    {file && canOpenRecords
      ? <Alert type="error" showIcon title={t('upload.result.failedSeeRecord')}
        action={<Button size="small" onClick={() => void navigate(fileRecordHref(file.fileId))}>{t('upload.result.openFileRecord')}</Button>} />
      : <Alert type="error" showIcon title={t('upload.result.failed')} />}
    {again}</Space>;
  const addresses = file ? visibleAddresses(file) : null;
  return <Space orientation="vertical" className="file-upload-result-body">
    <FileMeta name={stage.name} file={file} />
    {addresses ? <>
      <AddressRow label={t('upload.result.publicUrl')} value={addresses.publicUrl} openable />
      {addresses.versionedUrl && <AddressRow label={t('upload.result.versionedUrl')} value={addresses.versionedUrl} />}
      <AddressRow label={t('upload.result.fileAddress')} value={addresses.fileAddress} />
      <span className="muted">{t('upload.result.addressNote')}</span>
    </> : <Alert type="info" showIcon title={t('upload.result.noAddress')} />}
  </Space>;
}

function FileMeta({ name, file }: { name: string; file: UploadedFile | null }) {
  return <div><div className="file-ellipsis" title={file?.displayName ?? name}>{file?.displayName ?? name}</div>
    {file && <div className="cell-meta"><span className="mono">{file.fileId}</span> · {byteSizeLabel(file.sizeBytes)} · {file.mimeType}</div>}</div>;
}

/** 地址行：复制；公开地址另可在新标签页打开。 */
export function AddressRow({ label, value, openable = false }: { label: string; value: string; openable?: boolean }) {
  const t = useT('file');
  return <div className="file-upload-address">
    <span className="file-upload-address-label">{label}</span>
    <Typography.Text className="mono file-upload-address-value" ellipsis={{ tooltip: value }}>{value}</Typography.Text>
    <Space size={4}>
      <Typography.Text copyable={{ text: value, tooltips: [t('check.copy'), t('check.copied')],
        icon: [<span key="c">{t('check.copy')}</span>, <span key="d">{t('check.copied')}</span>] }} />
      {openable && <Typography.Link href={value} target="_blank" rel="noopener noreferrer">{t('publicUrl.open')}</Typography.Link>}
    </Space>
  </div>;
}
