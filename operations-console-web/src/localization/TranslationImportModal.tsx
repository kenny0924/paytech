import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Modal, Spin } from 'antd';
import { ApiError } from '../api/session';
import { PREVIEW_LIST_LIMIT, TRANSLATION_FORMAT, summarizeImportFile } from './importModel';
import { decodeImportTranslations, localizationPaths } from './localizationApi';
import { Icons, ToneIcon, useAdminT } from './LocalizationParts';
import { sizeText } from './releaseModel';
import { ImportUploadError, classifyFailure, isFileNotReady, localizationCommand, sendCommand, uploadImportFile, withScanRetry } from './useLocalizationCommand';

/**
 * 导入一个语言的翻译文件（需求 R4、AC-09；格式与导出相同）：全部通过校验才写入，任一失败整体拒绝并列出逐 key 错误。
 * 空字符串表示不改动。
 */
export function TranslationImportModal({ applicationCode, applicationName, locale, languageName, onAuthorizationChanged, onClose, onImported }: {
  applicationCode: string; applicationName: string; locale: string; languageName: string;
  onAuthorizationChanged: () => void; onClose: () => void; onImported: () => void;
}) {
  const t = useAdminT(); const { message } = App.useApp();
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<{ name: string; size: number; keys: number } | null>(null);
  const [busy, setBusy] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [failure, setFailure] = useState<{ title: string; errors: readonly { field: string; message: string }[] } | null>(null);
  const generation = useRef(0);
  useEffect(() => () => { generation.current++; }, []);
  async function choose(selected: File | undefined) {
    if (input.current) input.current.value = '';
    if (!selected) return;
    const current = ++generation.current;
    setFailure(null);
    const summary = summarizeImportFile(await selected.text(), selected.size, { application: applicationCode, format: TRANSLATION_FORMAT, locale });
    if (current !== generation.current) return;
    setFile({ name: selected.name, size: selected.size, keys: summary.keys });
    if (summary.problem) {
      setFailure({ title: t(`import.file_problem.${summary.problem}`, { application: summary.application ?? '—', expected: applicationCode, locale }), errors: [] });
      return;
    }
    setBusy(true); setScanning(false);
    try {
      const fileId = await uploadImportFile(selected, 'TRANSLATION_IMPORT');
      const result = await withScanRetry(() => sendCommand(localizationCommand(localizationPaths.translationImports(applicationCode), 'POST', { fileId, locale }),
        decodeImportTranslations), () => current !== generation.current, () => { if (current === generation.current) setScanning(true); });
      if (current !== generation.current) return;
      void message.success(t('translation_import.done', { updated: result.updatedCount, unchanged: result.unchangedCount, empty: result.emptyCount }));
      onImported();
    } catch (error) {
      if (current !== generation.current) return;
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      const classified = classifyFailure(error);
      if (isFileNotReady(error)) { setFailure({ title: t('import.still_scanning'), errors: [] }); return; }
      if (error instanceof ImportUploadError) { setFailure({ title: t(error.reason === 'rejected' ? 'import.file_rejected' : 'import.upload_failed'), errors: [] }); return; }
      setFailure(classified.kind === 'rejected' ? { title: classified.detail || t('common.rejected'), errors: classified.errors }
        : classified.kind === 'conflict' ? { title: t('common.conflict_refresh'), errors: [] }
        : { title: error instanceof ApiError ? error.message : t('import.upload_failed'), errors: [] });
    } finally { if (current === generation.current) setBusy(false); }
  }
  return <Modal open width={600} onCancel={onClose} mask={{ closable: false }}
    title={<div className="l10n-modal-title">{t('translation_import.title', { language: languageName })}<span className="muted">{applicationName} · {t('translation_import.subtitle')}</span></div>}
    footer={<div className="l10n-modal-foot"><span className="l10n-grow" /><Button onClick={onClose}>{t('common.close')}</Button></div>}>
    <input ref={input} type="file" accept="application/json,.json" hidden onChange={event => void choose(event.target.files?.[0])} />
    <div className="l10n-modal-body">
      {file ? <div className="l10n-file-row"><ToneIcon tone="blue">{Icons.file(16)}</ToneIcon>
        <div className="l10n-grow"><strong>{file.name}</strong><div className="muted">{sizeText(file.size)}{file.keys > 0 && <> · {t('translation_import.keys', { count: file.keys })}</>}</div></div>
        <button type="button" className="l10n-link" disabled={busy} onClick={() => input.current?.click()}>{t('import.choose_again')}</button></div>
        : <button type="button" className="l10n-drop" onClick={() => input.current?.click()}
          onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); void choose(event.dataTransfer.files[0]); }}>
          {Icons.upload(18)}<strong>{t('translation_import.drop_title', { file: `${applicationCode}.${locale}.json` })}</strong>
          <span className="muted">{t('translation_import.drop_hint')}</span></button>}
      {busy && <div className="l10n-progress-row"><Spin size="small" /> {scanning ? t('import.scanning') : t('translation_import.working')}</div>}
      {failure && <Alert type="error" showIcon title={failure.title} description={failure.errors.length > 0 && <ul className="l10n-item-list">
        {failure.errors.slice(0, PREVIEW_LIST_LIMIT).map((item, index) => <li key={index}><code>{item.field}</code> {item.message}</li>)}</ul>} />}
      <div className="l10n-info-bar">{Icons.info()} {t('translation_import.info')}</div>
    </div>
  </Modal>;
}
