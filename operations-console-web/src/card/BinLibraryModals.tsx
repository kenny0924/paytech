import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Checkbox, Modal, Progress, Table } from 'antd';
import {
  ArrowRightOutlined, BlockOutlined, CheckCircleOutlined, ClockCircleOutlined, CloseCircleOutlined, FileTextOutlined, InfoCircleOutlined,
  UnorderedListOutlined, UploadOutlined, WarningOutlined,
} from '@ant-design/icons';
import {
  activationConflictMessage, binLibraryPaths, csvColumns, csvFileIssue, csvTemplateFileName, csvTemplateHref, decodeActivation, decodeUploadCompletion,
  decodeUploadStart, decodeVersion, formatCount, maxCsvBytes, previewCsv, rowDelta, versionAction,
} from './binLibrary';
import { useT } from '../i18n/react';
import { richText } from '../i18n/richText';
import type { BinLibraryVersion, CsvPreview, UploadCompletion } from './binLibrary';
import { putFile } from './directUpload';
import { useBinLibraryCommand } from './useBinLibraryCommand';

/** 本地预检只读取文件开头，足够覆盖表头与前 3 行。 */
const previewBytes = 64 * 1024;
const blockedScans = new Set(['QUARANTINED', 'REJECTED']);

type Stage = 'select' | 'upload' | 'create';
interface Picked { file: File; issue: string | null; preview: CsvPreview | null }

/**
 * 导入新版本（画板 07）：选择文件并本地核对表头 → 直传对象存储 → 用文件地址创建版本
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
  const error = failure ?? command.formError;
  const fileInput = <input type="file" accept=".csv,text/csv" hidden disabled={running} onChange={event => void pick(event)} />;
  return <Modal open wrapClassName="dq-modal bin-modal" title={t('import.title')} width={680} destroyOnHidden onCancel={close}
    footer={[<Button key="cancel" onClick={close}>{running ? t('import.cancelUpload') : tCommon('action.cancel')}</Button>,
      <Button key="start" type="primary" icon={<UploadOutlined />} disabled={!ready} loading={running} onClick={() => void start()}>
        {running ? (stage === 'upload' ? t('import.uploading') : t('import.creating')) : error ? tCommon('action.retry') : t('import.start')}</Button>]}>
    {error && <Alert type="error" showIcon className="dq-banner" title={error} />}
    {picked ? <div className="bin-file-card">
      <span className="bin-file-icon"><FileTextOutlined /></span>
      <span className="bin-file-text"><strong className="bin-ellipsis">{picked.file.name}</strong>
        <span className="muted">{t('import.fileSize', { size: fileSize(picked.file.size) })}</span></span>
      <label className={`bin-link${running ? ' is-disabled' : ''}`}>{t('import.changeFile')}{fileInput}</label>
    </div> : <label className="bin-dropzone"><UploadOutlined /><strong>{t('import.selectFile')}</strong>
      <span className="muted">{t('import.dropHint')}</span>{fileInput}</label>}
    {picked && <CheckList items={importChecks(t, picked)} />}
    {picked?.preview?.headerValid && picked.preview.rows.length > 0 && <>
      <div className="bin-modal-section-head"><strong>{t('import.previewTitle', { count: picked.preview.rows.length })}</strong>
        <span className="muted">{t('import.previewLocal')}</span></div>
      <Table size="small" className="bin-table is-compact" pagination={false} rowKey={(_, index) => String(index)} tableLayout="fixed"
        scroll={{ x: 840 }} dataSource={picked.preview.rows.map(cells => ({ cells }))}
        columns={csvColumns.map((column, index) => ({ title: <span className="mono">{column}</span>, render: (_: unknown, row: { cells: string[] }) =>
          <span className="mono">{row.cells[index] || '—'}</span> }))} />
    </>}
    {stage !== 'select' && <div className="bin-upload-progress">
      <span>{stage === 'upload' ? t('import.uploadingDirect') : t('import.uploadedCreating')}</span>
      <Progress percent={stage === 'create' ? 100 : percent} status={error ? 'exception' : running ? 'active' : undefined}
        format={value => progress && stage === 'upload' ? `${(progress.loaded / 1024 / 1024).toFixed(1)} / ${(progress.total / 1024 / 1024).toFixed(1)} MB · ${value}%` : `${value}%`} />
    </div>}
    <p className="bin-modal-note"><InfoCircleOutlined />
      <span>{richText(t, 'import.note', { template: <a href={csvTemplateHref} download={csvTemplateFileName}>{t('page.csvTemplate')}</a> })}</span></p>
  </Modal>;
}

const fileSize = (bytes: number) => bytes < 1024 * 1024 ? `${Math.max(0.1, bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;

interface Check { ok: boolean | 'warning'; text: string }

/** 本地预检结果：文件类型 / 大小，以及表头与列数。 */
function importChecks(t: ReturnType<typeof useT>, picked: Picked): Check[] {
  const fileIssue = csvFileIssue(picked.file);
  if (fileIssue) return [{ ok: false, text: fileIssue }];
  return [
    picked.issue ? { ok: false, text: t('import.precheckFailed', { issue: picked.issue }) } : { ok: true, text: t('import.check.header', { columns: csvColumns.length }) },
    { ok: picked.file.size <= maxCsvBytes, text: t('import.check.size') },
  ];
}

function CheckList({ items }: { items: Check[] }) {
  return <ul className="bin-checks">{items.map((item, index) => <li key={index} className={item.ok === true ? 'is-ok' : item.ok === 'warning' ? 'is-warning' : 'is-error'}>
    {item.ok === true ? <CheckCircleOutlined /> : item.ok === 'warning' ? <WarningOutlined /> : <CloseCircleOutlined />}<span>{item.text}</span></li>)}</ul>;
}

/**
 * 激活 / 回切确认（画板 06）：变更摘要（生效版本、号段数、生效时间）、检查结果与影响提示，二次勾选后提交；409 提示他人已切换
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
  const versionNo = target.versionNo;
  const delta = rowDelta(active?.totalRows ?? null, target.totalRows);
  const fewer = active?.totalRows != null && target.totalRows !== null ? active.totalRows - target.totalRows : 0;
  const checks: Check[] = [{ ok: true, text: t('activation.check.valid', { versionNo, errors: target.errorRows }) },
    ...(active && fewer > 0 ? [{ ok: 'warning' as const, text: t('activation.check.fewer', { versionNo, activeVersionNo: active.versionNo, count: fewer }) }] : [])];
  return <Modal open wrapClassName="dq-modal bin-modal" title={rollback ? t('activation.rollbackTitle', { versionNo }) : t('activation.activateTitle', { versionNo })}
    okText={rollback ? t('activation.confirmRollback', { versionNo }) : t('activation.confirmActivate', { versionNo })} cancelText={tCommon('action.cancel')}
    confirmLoading={command.saving} width={560} destroyOnHidden okButtonProps={{ disabled: !confirmed || command.conflict, danger: rollback }}
    onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }}>
    {command.conflict && <Alert type="error" showIcon className="dq-banner" title={activationConflictMessage()}
      description={t('activation.conflictSeen', { version: active ? `v${active.versionNo}` : t('version.none') })} action={<Button size="small" onClick={onDone}>{tCommon('action.refresh')}</Button>} />}
    {command.formError && <Alert type="error" showIcon className="dq-banner" title={command.formError} />}
    <div className="bin-summary-rows">
      <SummaryRow icon={<BlockOutlined />} label={t('activation.row.version')}>
        <span className="muted">{active ? `v${active.versionNo}` : t('version.none')}</span><ArrowRightOutlined className="bin-arrow" /><strong>v{versionNo}</strong></SummaryRow>
      <SummaryRow icon={<UnorderedListOutlined />} label={t('activation.row.ranges')}>
        <span className="muted">{formatCount(active?.totalRows)}</span><ArrowRightOutlined className="bin-arrow" /><strong>{formatCount(target.totalRows)}</strong>
        {delta && delta !== '±0' && <span className={`bin-delta${fewer > 0 ? ' is-warning' : ''}`}>{delta}</span>}</SummaryRow>
      <SummaryRow icon={<ClockCircleOutlined />} label={t('activation.row.effective')}><strong>{t('activation.effectiveNow')}</strong></SummaryRow>
    </div>
    <CheckList items={checks} />
    <p className="bin-modal-note"><InfoCircleOutlined /><span>{rollback ? t('activation.impact.rollbackNewOnly')
      : active ? t('activation.info.activate', { activeVersionNo: active.versionNo }) : t('activation.impact.activateExisting')}</span></p>
    <Checkbox className="bin-confirm" checked={confirmed} onChange={event => setConfirmed(event.target.checked)}>
      {rollback ? t('activation.confirmRollbackCheck', { versionNo })
        : active ? t('activation.confirmReplaceCheck', { versionNo, activeVersionNo: active.versionNo }) : t('activation.confirmActivateCheck', { versionNo })}
    </Checkbox>
  </Modal>;
}

function SummaryRow({ icon, label, children }: { icon: ReactNode; label: string; children: ReactNode }) {
  return <div className="bin-summary-row"><span className="bin-icon-chip">{icon}</span><span className="bin-summary-label">{label}</span>
    <span className="bin-summary-value">{children}</span></div>;
}
