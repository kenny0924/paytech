import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import { Alert, Button, Checkbox, Modal, Progress, Steps, Table, Tag } from 'antd';
import {
  activationConflictMessage, binLibraryPaths, csvColumns, csvFileIssue, csvTemplate, decodeActivation, decodeUploadCompletion, decodeUploadStart,
  decodeVersion, formatCount, previewCsv, rowDelta, versionAction, versionStatusColors, versionStatusLabels,
} from './binLibrary';
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
        '创建版本失败', controller.signal);
      if (version) onCreated(version);
    } finally { if (!controller.signal.aborted) setRunning(false); }
  }

  async function upload(file: File, signal: AbortSignal): Promise<UploadCompletion | null> {
    const started = await command.run(binLibraryPaths.uploads, { fileName: file.name, sizeBytes: String(file.size) }, decodeUploadStart,
      '开始上传失败', signal);
    if (!started) return null;
    try {
      await putFile(started.authorization, file, (loaded, total) => setProgress({ loaded, total }), signal);
    } catch (error) {
      if (!signal.aborted) setFailure(error instanceof Error ? error.message : '上传失败，请重试');
      return null;
    }
    const done = await command.run(binLibraryPaths.uploadCompletion(started.uploadId), undefined, decodeUploadCompletion, '完成上传失败', signal);
    if (done && blockedScans.has(done.scanStatus)) {
      setFailure('文件未通过安全扫描，请检查文件后重新选择。');
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
  return <Modal open wrapClassName="dq-modal bin-modal" title="导入新版本" width={760} destroyOnHidden onCancel={close}
    footer={[<Button key="cancel" onClick={close}>{running ? '取消上传' : '取消'}</Button>,
      <Button key="start" type="primary" disabled={!ready} loading={running} onClick={() => void start()}>
        {running ? (stage === 'upload' ? '上传中…' : '创建版本中…') : error ? '重试' : '开始导入'}</Button>]}>
    <Steps size="small" current={stepIndex} status={error ? 'error' : undefined} className="bin-steps"
      items={[{ title: '选择文件' }, { title: '上传文件' }, { title: '创建版本并校验' }]} />
    {error && <Alert type="error" showIcon className="dq-banner" title={error} />}
    <div className="bin-file-row">
      <label className="ant-btn ant-btn-default bin-file-button">
        {picked ? '更换文件' : '选择 CSV 文件'}
        <input type="file" accept=".csv,text/csv" hidden disabled={running} onChange={event => void pick(event)} />
      </label>
      {picked && <span><span className="mono">{picked.file.name}</span>
        <span className="muted"> · {(picked.file.size / 1024 / 1024).toFixed(1)} MB</span></span>}
    </div>
    {picked?.issue && <Alert type="error" showIcon className="dq-banner" title={`本地预检未通过：${picked.issue}`} />}
    {picked && !picked.issue && <p className="muted">本地预检：表头正确，前 {picked.preview?.rows.length ?? 0} 行格式正确（完整校验在创建版本后进行）。</p>}
    {picked?.preview?.headerValid && <Table size="small" pagination={false} rowKey={(_, index) => String(index)} tableLayout="fixed"
      scroll={{ x: 760 }} dataSource={picked.preview.rows.map(cells => ({ cells }))}
      columns={csvColumns.map((column, index) => ({ title: <span className="mono">{column}</span>, render: (_: unknown, row: { cells: string[] }) =>
        <span className="mono">{row.cells[index] || '—'}</span> }))} />}
    {stage !== 'select' && <div className="bin-upload-progress">
      <span>{stage === 'upload' ? '正在直传到文件存储…' : '上传完成，正在创建版本…'}</span>
      <Progress percent={stage === 'create' ? 100 : percent} status={error ? 'exception' : running ? 'active' : undefined}
        format={value => progress && stage === 'upload' ? `${(progress.loaded / 1024 / 1024).toFixed(1)} / ${(progress.total / 1024 / 1024).toFixed(1)} MB · ${value}%` : `${value}%`} />
    </div>}
    <div className="dq-note">
      文件不经过网关，直接上传到对象存储；上传完成后自动创建版本，并在后台完整校验。关闭对话框会中止上传。<br />
      平台标准 CSV：UTF-8 · 逗号分隔 · 第 1 行为固定表头 · 文件 ≤ 50 MB、≤ 100 万行 · bin_prefix 6～8 位数字且同一前缀不得重复 ·
      card_brand、issuer_country 必填 · 发卡区域按平台国家区域映射自动推导。{' '}
      <a href={templateHref} download="card-bin-library-template.csv">下载示例模板</a>
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
  const command = useBinLibraryCommand(onAuthorizationChanged);
  const [confirmed, setConfirmed] = useState(false);
  const action = versionAction(target);
  if (!action) return null;
  const rollback = action.kind === 'ROLLBACK';
  const submit = async () => {
    const result = await command.run(binLibraryPaths.activation(target.versionNo), { expectedActiveVersionNo: active?.versionNo ?? null },
      decodeActivation, `${action.verb}失败`);
    if (result) onDone();
  };
  const delta = rowDelta(active?.totalRows ?? null, target.totalRows);
  return <Modal open wrapClassName="dq-modal bin-modal" title={rollback ? `回切到 v${target.versionNo}` : `激活版本 v${target.versionNo}`}
    okText={rollback ? '确认回切' : '确认激活'} cancelText="取消" confirmLoading={command.saving} width={560} destroyOnHidden
    okButtonProps={{ disabled: !confirmed || command.conflict, danger: rollback }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }}>
    {command.conflict && <Alert type="error" showIcon className="dq-banner" title={activationConflictMessage}
      description={`页面看到的生效版本是 ${active ? `v${active.versionNo}` : '无'}。`} action={<Button size="small" onClick={onDone}>刷新</Button>} />}
    {command.formError && <Alert type="error" showIcon className="dq-banner" title={command.formError} />}
    <div className="bin-compare">
      <VersionBox version={active} caption="当前生效" />
      <span className="bin-compare-arrow">→</span>
      <VersionBox version={target} caption={versionStatusLabels[target.status]} delta={delta} />
    </div>
    <ul className="bin-impact">
      {rollback ? <>
        <li>回切只影响之后新建的支付工具，已创建的交易保留原卡属性。</li>
        {active && <li>v{active.versionNo} 变为已停用，之后仍可再次激活。</li>}
        <li>回切会记录在激活历史中（操作人与时间）。</li>
      </> : <>
        <li>提交后所有实例立即按 v{target.versionNo} 查询，无需重启。</li>
        <li>已创建的支付工具保留原卡属性，不受影响。</li>
        <li>如页面打开后已有他人切换了生效版本，提交会被拒绝并提示刷新。</li>
      </>}
    </ul>
    <Checkbox checked={confirmed} onChange={event => setConfirmed(event.target.checked)}>
      {rollback ? `我确认回切到 v${target.versionNo}，之后新交易按 v${target.versionNo} 识别`
        : `我确认激活 v${target.versionNo}${active ? `，替换当前生效的 v${active.versionNo}` : ''}`}
    </Checkbox>
  </Modal>;
}

function VersionBox({ version, caption, delta }: { version: BinLibraryVersion | null; caption: string; delta?: string }) {
  if (!version) return <div className="bin-compare-box"><strong>无</strong><Tag>{caption}</Tag><span className="muted">尚未激活任何版本</span></div>;
  return <div className="bin-compare-box">
    <strong>v{version.versionNo}</strong><Tag color={versionStatusColors[version.status]}>{caption}</Tag>
    <span className="muted">{formatCount(version.totalRows)} 个号段{delta ? `（${delta}）` : ''}</span>
  </div>;
}
