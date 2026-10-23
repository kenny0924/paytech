import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import { Alert, Button, Checkbox, Modal, Progress, Steps, Table, Tag } from 'antd';
import {
  activationConflictMessage, binLibraryPaths, csvColumns, csvFileIssue, csvTemplate, decodeActivation, decodeUploadCompletion, decodeUploadStart,
  decodeVersion, formatCount, previewCsv, rowDelta, versionAction, versionStatusColors,
} from './binLibrary';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import type { BinLibraryVersion, CsvPreview, UploadCompletion } from './binLibrary';
import { putFile } from './directUpload';
import { useBinLibraryCommand } from './useBinLibraryCommand';

/** 本地预检只读取文件开头，足够覆盖表头与前 3 行。 */
const previewBytes = 64 * 1024;
const blockedScans = new Set(['QUARANTINED', 'REJECTED']);
const templateHref = `data:text/csv;charset=utf-8,${encodeURIComponent(csvTemplate)}`;

type Stage = 'select' | 'upload' | 'create';
interface Picked { file: File; issue: string | null; preview: CsvPreview | null }

/**
 * 导入新版本（原型 02）：选择文件并本地核对表头 → 直传对象存储 → 用文件地址创建版本
 * <li>上传完成后再失败只重试创建版本，不重新上传；关闭对话框会中止进行中的上传。</li>
 */
export function ImportModal({ onClose, onCreated, onAuthorizationChanged }: {
  onClose: () => void; onCreated: (version: BinLibraryVersion) => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('card'), tCommon = useT('common');
  const command = useBinLibraryCommand(onAuthorizationChanged);
  const [picked, setPicked] = useState<Picked | null>(null);
  const [stage, setStage] = useState<Stage>('select');
  const [running, setRunning] = useState(false);
  const [progress, setProgress] = useState<{ loaded: number; total: number } | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const completion = useRef<UploadCompletion | null>(null);
  const abort = useRef<AbortController | null>(null);
  useEffect(() => () => abort.current?.abort(), []);

  async function pick(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    completion.current = null; setFailure(null); setStage('select'); setProgress(null);
    const issue = csvFileIssue(file);
    const preview = issue ? null : previewCsv(await file.slice(0, previewBytes).text(), file.size <= previewBytes);
    setPicked({ file, issue: issue ?? preview?.issue ?? null, preview });
  }

  /** 上传（未完成时）后创建版本；每个阶段失败都停在该阶段等待重试。 */
  async function start() {
    if (!picked || running) return;
    const controller = new AbortController(); abort.current = controller;
    setRunning(true); setFailure(null); command.clearError();
    try {
      if (!completion.current) {
        setStage('upload');
        completion.current = await upload(picked.file, controller.signal);
        if (!completion.current) return;
      }
      setStage('create');
      const version = await command.run(binLibraryPaths.versions, { fileAddress: completion.current.fileAddress }, decodeVersion,
        t('import.createFailed'), controller.signal);
      if (version) onCreated(version);
    } finally { if (!controller.signal.aborted) setRunning(false); }
  }

  async function upload(file: File, signal: AbortSignal): Promise<UploadCompletion | null> {
    const started = await command.run(binLibraryPaths.uploads, { fileName: file.name, sizeBytes: String(file.size) }, decodeUploadStart,
      t('import.startFailed'), signal);
    if (!started) return null;
    try {
      await putFile(started.authorization, file, (loaded, total) => setProgress({ loaded, total }), signal);
    } catch (error) {
      if (!signal.aborted) setFailure(error instanceof Error ? error.message : t('upload.failed'));
      return null;
    }
    const done = await command.run(binLibraryPaths.uploadCompletion(started.uploadId), undefined, decodeUploadCompletion, t('import.completeFailed'), signal);
    if (done && blockedScans.has(done.scanStatus)) {
      setFailure(t('import.scanBlocked'));
      return null;
    }
    return done;
  }

  function close() {
    abort.current?.abort();
    onClose();
  }

  const ready = !!picked && !picked.issue && picked.preview?.headerValid === true;
  const percent = progress && progress.total > 0 ? Math.floor(progress.loaded * 100 / progress.total) : 0;
  const stepIndex = stage === 'select' ? 0 : stage === 'upload' ? 1 : 2;
  const error = failure ?? command.formError;
  return <Modal open wrapClassName="dq-modal bin-modal" title={t('import.title')} width={760} destroyOnHidden onCancel={close}
    footer={[<Button key="cancel" onClick={close}>{running ? t('import.cancelUpload') : tCommon('action.cancel')}</Button>,
      <Button key="start" type="primary" disabled={!ready} loading={running} onClick={() => void start()}>
        {running ? (stage === 'upload' ? t('import.uploading') : t('import.creating')) : error ? tCommon('action.retry') : t('import.start')}</Button>]}>
    <Steps size="small" current={stepIndex} status={error ? 'error' : undefined} className="bin-steps"
      items={[{ title: t('import.step.select') }, { title: t('import.step.upload') }, { title: t('import.step.create') }]} />
    {error && <Alert type="error" showIcon className="dq-banner" title={error} />}
    <div className="bin-file-row">
      <label className="ant-btn ant-btn-default bin-file-button">
        {picked ? t('import.changeFile') : t('import.selectFile')}
        <input type="file" accept=".csv,text/csv" hidden disabled={running} onChange={event => void pick(event)} />
      </label>
      {picked && <span><span className="mono">{picked.file.name}</span>
        <span className="muted"> · {(picked.file.size / 1024 / 1024).toFixed(1)} MB</span></span>}
    </div>
    {picked?.issue && <Alert type="error" showIcon className="dq-banner" title={t('import.precheckFailed', { issue: picked.issue })} />}
    {picked && !picked.issue && <p className="muted">{t('import.precheckPassed', { rows: picked.preview?.rows.length ?? 0 })}</p>}
    {picked?.preview?.headerValid && <Table size="small" pagination={false} rowKey={(_, index) => String(index)} tableLayout="fixed"
      scroll={{ x: 760 }} dataSource={picked.preview.rows.map(cells => ({ cells }))}
      columns={csvColumns.map((column, index) => ({ title: <span className="mono">{column}</span>, render: (_: unknown, row: { cells: string[] }) =>
        <span className="mono">{row.cells[index] || '—'}</span> }))} />}
    {stage !== 'select' && <div className="bin-upload-progress">
      <span>{stage === 'upload' ? t('import.uploadingDirect') : t('import.uploadedCreating')}</span>
      <Progress percent={stage === 'create' ? 100 : percent} status={error ? 'exception' : running ? 'active' : undefined}
        format={value => progress && stage === 'upload' ? `${(progress.loaded / 1024 / 1024).toFixed(1)} / ${(progress.total / 1024 / 1024).toFixed(1)} MB · ${value}%` : `${value}%`} />
    </div>}
    <div className="dq-note">
      {t('import.note.direct')}<br />
      {t('import.note.format')}{' '}
      <a href={templateHref} download="card-bin-library-template.csv">{t('import.note.template')}</a>
    </div>
  </Modal>;
}

/**
 * 激活 / 回切确认（原型 05）：当前生效 → 目标版本的号段数对比、影响说明与二次勾选；409 提示他人已切换
 * <li>提交带页面看到的生效版本号；没有生效版本时为 null。</li>
 */
export function ActivationModal({ target, active, onClose, onDone, onAuthorizationChanged }: {
  target: BinLibraryVersion; active: BinLibraryVersion | null; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('card'), tCommon = useT('common');
  const command = useBinLibraryCommand(onAuthorizationChanged);
  const [confirmed, setConfirmed] = useState(false);
  const action = versionAction(target);
  if (!action) return null;
  const rollback = action.kind === 'ROLLBACK';
  const submit = async () => {
    const result = await command.run(binLibraryPaths.activation(target.versionNo), { expectedActiveVersionNo: active?.versionNo ?? null },
      decodeActivation, rollback ? t('activation.rollbackFailed') : t('activation.activateFailed'));
    if (result) onDone();
  };
  const delta = rowDelta(active?.totalRows ?? null, target.totalRows);
  const versionNo = target.versionNo;
  return <Modal open wrapClassName="dq-modal bin-modal" title={rollback ? t('activation.rollbackTitle', { versionNo }) : t('activation.activateTitle', { versionNo })}
    okText={rollback ? t('activation.confirmRollback') : t('activation.confirmActivate')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving} width={560} destroyOnHidden
    okButtonProps={{ disabled: !confirmed || command.conflict, danger: rollback }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }}>
    {command.conflict && <Alert type="error" showIcon className="dq-banner" title={activationConflictMessage()}
      description={t('activation.conflictSeen', { version: active ? `v${active.versionNo}` : t('version.none') })} action={<Button size="small" onClick={onDone}>{tCommon('action.refresh')}</Button>} />}
    {command.formError && <Alert type="error" showIcon className="dq-banner" title={command.formError} />}
    <div className="bin-compare">
      <VersionBox version={active} caption={t('activation.currentActive')} />
      <span className="bin-compare-arrow">→</span>
      <VersionBox version={target} caption={enumLabel(t, 'enum.versionStatus', target.status)} delta={delta} />
    </div>
    <ul className="bin-impact">
      {rollback ? <>
        <li>{t('activation.impact.rollbackNewOnly')}</li>
        {active && <li>{t('activation.impact.rollbackRetires', { versionNo: active.versionNo })}</li>}
        <li>{t('activation.impact.rollbackRecorded')}</li>
      </> : <>
        <li>{t('activation.impact.activateImmediate', { versionNo })}</li>
        <li>{t('activation.impact.activateExisting')}</li>
        <li>{t('activation.impact.activateConflict')}</li>
      </>}
    </ul>
    <Checkbox checked={confirmed} onChange={event => setConfirmed(event.target.checked)}>
      {rollback ? t('activation.confirmRollbackCheck', { versionNo })
        : active ? t('activation.confirmReplaceCheck', { versionNo, activeVersionNo: active.versionNo }) : t('activation.confirmActivateCheck', { versionNo })}
    </Checkbox>
  </Modal>;
}

function VersionBox({ version, caption, delta }: { version: BinLibraryVersion | null; caption: string; delta?: string }) {
  const t = useT('card');
  if (!version) return <div className="bin-compare-box"><strong>{t('version.none')}</strong><Tag>{caption}</Tag><span className="muted">{t('version.noneActivated')}</span></div>;
  return <div className="bin-compare-box">
    <strong>v{version.versionNo}</strong><Tag color={versionStatusColors[version.status]}>{caption}</Tag>
    <span className="muted">{delta ? t('version.rangesWithDelta', { count: formatCount(version.totalRows), delta }) : t('version.ranges', { count: formatCount(version.totalRows) })}</span>
  </div>;
}
