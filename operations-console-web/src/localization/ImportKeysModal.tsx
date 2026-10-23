import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, App, Button, Modal, Spin } from 'antd';
import { ApiError } from '../api/session';
import { formatNumber } from '../i18n';
import { PREVIEW_LIST_LIMIT, SOURCE_FORMAT, importBlocker, importChangeTotal, importRows, summarizeImportFile } from './importModel';
import type { ImportRowKind, SourceFileSummary } from './importModel';
import { decodeKeyImportPreview, decodeKeyImportResult, localizationPaths } from './localizationApi';
import type { Application, KeyImportItem, KeyImportPreview, Namespace } from './localizationApi';
import { Icons, ToneIcon, useAdminT } from './LocalizationParts';
import type { Tone } from './LocalizationParts';
import { NamespaceModal } from './NamespaceModal';
import { sizeText } from './releaseModel';
import { ImportUploadError, isFileNotReady, localizationCommand, sendCommand, uploadImportFile, useLocalizationCommand, withScanRetry } from './useLocalizationCommand';

type Phase = 'choose' | 'uploading' | 'checking' | 'scanning' | 'preview' | 'failed';
const rowTone: Record<ImportRowKind, Tone> = { added: 'green', source_changed: 'orange', deprecated: 'red', restored: 'blue' };
const rowIcon: Record<ImportRowKind, ReactNode> = { added: Icons.plus(12), source_changed: Icons.pencil(12), deprecated: Icons.minus(12), restored: Icons.restore(12) };

/** 导入 key（原型 03）：选文件 → 上传 → 预览四类差异与未创建的命名空间（可直达创建，建好后自动重新预览）→ 确认。 */
export function ImportKeysModal({ application, liveReleaseNo, namespaces, onAuthorizationChanged, onClose, onNamespacesChanged, onImported }: {
  application: Application; liveReleaseNo: number | null; namespaces: Namespace[]; onAuthorizationChanged: () => void;
  onClose: () => void; onNamespacesChanged: () => void; onImported: () => void;
}) {
  const t = useAdminT(); const { message } = App.useApp();
  const input = useRef<HTMLInputElement>(null);
  const [phase, setPhase] = useState<Phase>('choose');
  const [file, setFile] = useState<{ name: string; size: number; summary: SourceFileSummary } | null>(null);
  const [fileId, setFileId] = useState<string | null>(null);
  const [preview, setPreview] = useState<KeyImportPreview | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [stale, setStale] = useState(false);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [creating, setCreating] = useState<string | null>(null);
  const [knownNamespaces, setKnownNamespaces] = useState(namespaces);
  const generation = useRef(0);
  const command = useLocalizationCommand(onAuthorizationChanged);
  useEffect(() => () => { generation.current++; }, []);

  async function choose(selected: File | undefined) {
    if (input.current) input.current.value = '';
    if (!selected) return;
    const current = ++generation.current;
    setPreview(null); setFileId(null); setFailure(null); setStale(false); setExpanded(null);
    const summary = summarizeImportFile(await selected.text(), selected.size, { application: application.applicationCode, format: SOURCE_FORMAT });
    if (current !== generation.current) return;
    setFile({ name: selected.name, size: selected.size, summary });
    if (summary.problem) { setPhase('failed'); setFailure(t(`import.file_problem.${summary.problem}`, { application: summary.application ?? '—', expected: application.applicationCode })); return; }
    setPhase('uploading');
    try {
      const id = await uploadImportFile(selected, 'KEY_IMPORT');
      if (current !== generation.current) return;
      setFileId(id);
      await runPreview(id, current);
    } catch (error) {
      if (current !== generation.current) return;
      setPhase('failed');
      setFailure(error instanceof ImportUploadError ? t(error.reason === 'rejected' ? 'import.file_rejected' : 'import.upload_failed')
        : error instanceof ApiError ? error.detail || error.message : t('import.upload_failed'));
    }
  }
  async function runPreview(id: string, current = ++generation.current) {
    setPhase('checking'); setStale(false); setFailure(null);
    try {
      const result = await withScanRetry(() => sendCommand(localizationCommand(localizationPaths.keyImportPreview(application.applicationCode), 'POST', { fileId: id }),
        decodeKeyImportPreview), () => current !== generation.current, () => { if (current === generation.current) setPhase('scanning'); });
      if (current !== generation.current) return;
      setPreview(result); setPhase('preview');
    } catch (error) {
      if (current !== generation.current) return;
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      setPhase('failed');
      setFailure(isFileNotReady(error) ? t('import.still_scanning') : error instanceof ApiError ? error.detail || error.message : t('import.preview_failed'));
    }
  }
  async function confirm() {
    if (!preview || !fileId) return;
    const result = await command.run(localizationPaths.keyImports(application.applicationCode), 'POST',
      { fileId, previewDigest: preview.previewDigest }, decodeKeyImportResult, t('import.confirm_failed'));
    if (!result) return;
    if (result.ok) {
      const value = result.value;
      void message.success(t('import.done', { added: value.addedCount, changed: value.sourceChangedCount, deprecated: value.deprecatedCount, restored: value.restoredCount }));
      onImported(); return;
    }
    const problem = result.failure;
    if (problem.kind === 'conflict' && problem.code === 'I18N_IMPORT_FILE_NOT_READY') { await runPreview(fileId); return; }
    if (problem.kind === 'conflict') { setStale(true); return; }
    if (problem.kind === 'rejected' && problem.code === 'I18N_NAMESPACE_NOT_FOUND') { await runPreview(fileId); return; }
    if (problem.kind === 'rejected') setFailure(problem.detail || t('common.rejected'));
  }

  const blocker = phase === 'preview' ? importBlocker(preview, stale) : null;
  const rows = preview ? importRows(preview) : [];
  const keysIn = (namespace: string) => file?.summary.namespaces.find(item => item.code === namespace)?.keys ?? 0;
  const busy = phase === 'uploading' || phase === 'checking' || phase === 'scanning';
  const hint = phase !== 'preview' ? null : blocker === 'missing_namespaces' ? t('import.blocker.missing_namespaces', { codes: preview!.missingNamespaces.join(t('common.list_separator')) })
    : blocker ? t(`import.blocker.${blocker}`) : null;

  return <Modal open width={720} onCancel={onClose} mask={{ closable: false }} className="l10n-modal"
    title={<div className="l10n-modal-title">{t('import.title')}<span className="muted">{application.applicationName} · {t('import.subtitle')}</span></div>}
    footer={<div className="l10n-modal-foot"><span className="muted l10n-grow">{hint}</span>
      <Button onClick={onClose}>{t('common.cancel')}</Button>
      <Button type="primary" icon={Icons.upload()} disabled={phase !== 'preview' || blocker !== null} loading={command.saving} onClick={() => void confirm()}>{t('import.confirm')}</Button></div>}>
    <input ref={input} type="file" accept="application/json,.json" hidden onChange={event => void choose(event.target.files?.[0])} />
    <div className="l10n-modal-body">
      {file ? <div className="l10n-file-row">
        <ToneIcon tone="blue">{Icons.file(16)}</ToneIcon>
        <div className="l10n-grow"><strong>{file.name}</strong>
          <div className="muted">{sizeText(file.size)}{file.summary.keys > 0 && <> · {t('import.file_counts', { namespaces: file.summary.namespaces.length, keys: file.summary.keys })}</>}</div></div>
        <button type="button" className="l10n-link" disabled={busy} onClick={() => input.current?.click()}>{t('import.choose_again')}</button>
      </div> : <button type="button" className="l10n-drop" onClick={() => input.current?.click()}
        onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); void choose(event.dataTransfer.files[0]); }}>
        {Icons.upload(18)}<strong>{t('import.drop_title', { file: `${application.applicationCode}.en.json` })}</strong>
        <span className="muted">{t('import.drop_hint')}</span></button>}

      {busy && <div className="l10n-progress-row"><Spin size="small" /> {phase === 'uploading' ? t('import.uploading') : phase === 'scanning' ? t('import.scanning') : t('import.checking')}</div>}
      {failure && <Alert type="error" showIcon title={failure} action={fileId && phase === 'failed'
        ? <Button size="small" onClick={() => void runPreview(fileId)}>{t('import.retry_preview')}</Button> : undefined} />}
      {stale && <Alert type="warning" showIcon title={t('import.stale')} action={<Button size="small" onClick={() => fileId && void runPreview(fileId)}>{t('import.repreview')}</Button>} />}

      {preview && phase === 'preview' && <>
        <div className="l10n-section-label"><strong>{t('import.summary')}</strong>
          <span className="muted">{t('import.summary_hint', { changes: importChangeTotal(preview), problems: preview.missingNamespaces.length + preview.problems.length })}</span></div>
        <div className="l10n-summary-rows">
          {rows.map(row => <SummaryRow key={row.kind} tone={rowTone[row.kind]} icon={rowIcon[row.kind]}
            title={t(`import.row.${row.kind}`, { count: row.items.length })} description={rowDescription(t, row.kind, row.items)}
            actionLabel={t('common.view')} expanded={expanded === row.kind} onAction={() => setExpanded(expanded === row.kind ? null : row.kind)}>
            <ItemList items={row.items} kind={row.kind} /></SummaryRow>)}
          {preview.missingNamespaces.map(code => <SummaryRow key={`ns:${code}`} tone="red" icon={Icons.folder(12)} danger
            title={t('import.row.missing_namespace')} description={t('import.missing_namespace_detail', { code, count: keysIn(code) })}
            actionLabel={t('common.create')} onAction={() => setCreating(code)} />)}
          {preview.problems.length > 0 && <SummaryRow tone="red" icon={Icons.error(12)} danger title={t('import.row.problems', { count: preview.problems.length })}
            description={preview.problems[0].message} actionLabel={t('common.view')} expanded={expanded === 'problems'}
            onAction={() => setExpanded(expanded === 'problems' ? null : 'problems')}>
            <ul className="l10n-item-list">{preview.problems.slice(0, PREVIEW_LIST_LIMIT).map((item, index) => <li key={index}><code>{item.field}</code> {item.message}</li>)}</ul></SummaryRow>}
          {!rows.length && !preview.missingNamespaces.length && !preview.problems.length && <div className="l10n-summary-empty muted">{t('import.no_changes', { count: preview.unchangedCount })}</div>}
        </div>
        <div className="l10n-info-bar">{Icons.info()} {liveReleaseNo ? t('import.info_live', { releaseNo: liveReleaseNo }) : t('import.info_unpublished')}</div>
      </>}
    </div>
    {creating && <NamespaceModal applicationCode={application.applicationCode} applicationName={application.applicationName} namespaces={knownNamespaces}
      initialCode={creating} onAuthorizationChanged={onAuthorizationChanged} onClose={() => setCreating(null)}
      onSaved={namespace => { setCreating(null); setKnownNamespaces(current => [...current, namespace]); onNamespacesChanged(); if (fileId) void runPreview(fileId); }} />}
  </Modal>;
}

type Translate = ReturnType<typeof useAdminT>;
function rowDescription(t: Translate, kind: ImportRowKind, items: KeyImportItem[]): string {
  const first = items[0];
  const more = items.length > 1;
  if (kind === 'source_changed' && first.previousSourceText) {
    return t('import.desc.source_changed', { key: first.keyCode, before: first.previousSourceText, after: first.sourceText, more: more ? 'yes' : 'no' });
  }
  return t(`import.desc.${kind}`, { key: first.keyCode, more: more ? 'yes' : 'no' });
}

/** 摘要行（导入、发布、切换弹窗共用）：色块图标、标题、说明、右侧操作，可展开明细。 */
export function SummaryRow({ tone, icon, title, description, actionLabel, onAction, expanded, danger, children }: {
  tone: Tone; icon: ReactNode; title: ReactNode; description?: ReactNode; actionLabel?: string; onAction?: () => void;
  expanded?: boolean; danger?: boolean; children?: ReactNode;
}) {
  return <div className={`l10n-summary-row${danger ? ' is-danger' : ''}`}>
    <div className="l10n-summary-main">
      <ToneIcon tone={tone}>{icon}</ToneIcon>
      <strong className="l10n-summary-title">{title}</strong>
      <span className="l10n-summary-desc">{description}</span>
      {actionLabel && onAction && <button type="button" className="l10n-link" aria-expanded={children ? !!expanded : undefined} onClick={onAction}>{actionLabel}</button>}
    </div>
    {expanded && children && <div className="l10n-summary-detail">{children}</div>}
  </div>;
}

function ItemList({ items, kind }: { items: KeyImportItem[]; kind: ImportRowKind }) {
  const t = useAdminT();
  return <ul className="l10n-item-list">
    {items.slice(0, PREVIEW_LIST_LIMIT).map(item => <li key={item.keyCode}><code>{item.keyCode}</code>
      {kind === 'source_changed' && item.previousSourceText !== null
        ? <span className="l10n-diff"><del>{item.previousSourceText}</del> <ins>{item.sourceText}</ins></span>
        : <span className="muted">{item.sourceText}</span>}</li>)}
    {items.length > PREVIEW_LIST_LIMIT && <li className="muted">{t('common.and_more', { count: formatNumber(items.length - PREVIEW_LIST_LIMIT) })}</li>}
  </ul>;
}
