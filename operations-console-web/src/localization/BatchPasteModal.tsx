import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Input, Modal, Progress } from 'antd';
import { ApiError } from '../api/session';
import { requestAllPages } from '../api/paging';
import { formatNumber } from '../i18n';
import { validateTranslation } from './icu';
import { PREVIEW_LIST_LIMIT } from './importModel';
import { decodeSaveTranslations, decodeTranslationPage, localizationPaths } from './localizationApi';
import type { TranslationEntry } from './localizationApi';
import { Icons, issueText, useAdminT } from './LocalizationParts';
import { batchErrorTargets, parsePaste, splitBatches } from './translationModel';
import type { TranslationInputItem } from './translationModel';
import { classifyFailure, localizationCommand, sendCommand } from './useLocalizationCommand';

interface Checked {
  ready: TranslationInputItem[];
  invalid: { keyCode: string; reason: string }[];
  unknown: string[];
  unchanged: number;
  invalidLines: number[];
}
interface Failure { saved: number; batch: number; batches: number; failedCount: number; notAttempted: number; reason: string; items: { keyCode: string | null; message: string }[] }

/**
 * 批量粘贴（需求 R4）：粘贴 JSON 或"key<Tab>译文"，在浏览器内按与 Platform 相同的 ICU 规则逐条检查，
 * 只保存检查通过且有变化的条目；按请求体 ≤ 48 KB、≤ 200 条切批后依次保存，每批全部成功或全部失败。
 * 某批失败时停止，报告已保存条数、失败批的逐条原因与未尝试的条数。
 */
export function BatchPasteModal({ applicationCode, applicationName, locale, languageName, onAuthorizationChanged, onClose, onSaved }: {
  applicationCode: string; applicationName: string; locale: string; languageName: string;
  onAuthorizationChanged: () => void; onClose: () => void; onSaved: () => void;
}) {
  const t = useAdminT(); const { message } = App.useApp();
  const [text, setText] = useState('');
  const [checking, setChecking] = useState(false);
  const [checked, setChecked] = useState<Checked | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [progress, setProgress] = useState<{ batch: number; batches: number } | null>(null);
  const [failure, setFailure] = useState<Failure | null>(null);
  const mounted = useRef(true);
  useEffect(() => () => { mounted.current = false; }, []);

  async function check() {
    setChecking(true); setChecked(null); setFailure(null); setLoadFailed(false);
    const parsed = parsePaste(text);
    try {
      const controller = new AbortController();
      const entries = await requestAllPages(localizationPaths.translations(applicationCode), controller.signal, undefined, { locale }, decodeTranslationPage);
      if (!mounted.current) return;
      const byKey = new Map<string, TranslationEntry>(entries.map(entry => [entry.keyCode, entry]));
      const result: Checked = { ready: [], invalid: [], unknown: [], unchanged: 0, invalidLines: parsed.invalidLines };
      for (const item of parsed.entries) {
        const entry = byKey.get(item.keyCode);
        if (!entry) { result.unknown.push(item.keyCode); continue; }
        if (item.text === entry.text && !entry.sourceChanged) { result.unchanged++; continue; }
        const issues = validateTranslation(entry.sourceText, item.text);
        if (issues.length) result.invalid.push({ keyCode: item.keyCode, reason: issues.map(issue => issueText(t, issue)).join(t('issues.separator')) });
        else result.ready.push({ keyCode: item.keyCode, text: item.text, expectedVersion: entry.version });
      }
      setChecked(result);
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      if (mounted.current) setLoadFailed(true);
    } finally { if (mounted.current) setChecking(false); }
  }

  async function save() {
    if (!checked?.ready.length) return;
    const batches = splitBatches(locale, checked.ready);
    let saved = 0;
    setFailure(null);
    for (let index = 0; index < batches.length; index++) {
      setProgress({ batch: index + 1, batches: batches.length });
      try {
        await sendCommand(localizationCommand(localizationPaths.translationBatch(applicationCode), 'POST', { locale, translations: batches[index] }), decodeSaveTranslations);
        saved += batches[index].length;
      } catch (error) {
        if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
        const classified = classifyFailure(error);
        const notAttempted = batches.slice(index + 1).reduce((sum, batch) => sum + batch.length, 0);
        if (mounted.current) {
          setFailure({ saved, batch: index + 1, batches: batches.length, failedCount: batches[index].length, notAttempted,
            reason: classified.kind === 'conflict' ? t('batch.conflict') : classified.kind === 'rejected' ? classified.detail || t('common.rejected')
              : error instanceof ApiError ? error.message : t('common.try_later'),
            items: classified.kind === 'rejected' ? batchErrorTargets(classified.errors, batches[index]) : [] });
          setProgress(null); setChecked(null);
        }
        if (saved) onSaved();
        return;
      }
    }
    if (!mounted.current) return;
    setProgress(null);
    void message.success(t('batch.done', { count: saved }));
    onSaved(); onClose();
  }

  const busy = checking || progress !== null;
  return <Modal open width={720} onCancel={busy ? undefined : onClose} mask={{ closable: false }} closable={!busy}
    title={<div className="l10n-modal-title">{t('batch.title', { language: languageName })}<span className="muted">{applicationName} · {t('batch.subtitle')}</span></div>}
    footer={<div className="l10n-modal-foot"><span className="muted l10n-grow">{checked && !progress ? t('batch.split_hint') : null}</span>
      <Button onClick={onClose} disabled={busy}>{t('common.cancel')}</Button>
      {checked ? <Button type="primary" disabled={!checked.ready.length || busy} loading={progress !== null} onClick={() => void save()}>
        {t('batch.save', { count: checked.ready.length })}</Button>
        : <Button type="primary" disabled={!text.trim()} loading={checking} onClick={() => void check()}>{t('batch.check')}</Button>}</div>}>
    <div className="l10n-modal-body">
      <Input.TextArea rows={8} value={text} disabled={busy} className="l10n-mono-input" placeholder={t('batch.placeholder')}
        onChange={event => { setText(event.target.value); setChecked(null); }} aria-label={t('batch.title', { language: languageName })} />
      {loadFailed && <Alert type="error" showIcon title={t('batch.load_failed')} />}
      {progress && <div className="l10n-progress-row"><Progress size="small" percent={Math.round((progress.batch - 1) * 100 / progress.batches)} showInfo={false} style={{ width: 160 }} />
        {t('batch.progress', { batch: progress.batch, batches: progress.batches })}</div>}
      {failure && <Alert type="error" showIcon title={t('batch.partial', { saved: failure.saved, batch: failure.batch, batches: failure.batches,
        failed: failure.failedCount, rest: failure.notAttempted })}
        description={<><div>{failure.reason}</div>
          {failure.items.length > 0 && <ul className="l10n-item-list">{failure.items.slice(0, PREVIEW_LIST_LIMIT).map((item, index) =>
            <li key={index}>{item.keyCode && <code>{item.keyCode}</code>} {item.message}</li>)}</ul>}
          <div className="muted">{t('batch.partial_hint')}</div></>} />}
      {checked && <>
        <div className="l10n-section-label"><strong>{t('batch.result')}</strong></div>
        <div className="l10n-batch-stats">
          <span className="l10n-ok-text">{Icons.checkCircle(12)} {t('batch.ready', { count: checked.ready.length })}</span>
          {checked.invalid.length > 0 && <span className="l10n-danger-text">{Icons.error(12)} {t('batch.invalid', { count: checked.invalid.length })}</span>}
          {checked.unknown.length > 0 && <span className="l10n-warn-text">{Icons.warning(12)} {t('batch.unknown', { count: checked.unknown.length })}</span>}
          {checked.unchanged > 0 && <span className="muted">{t('batch.unchanged', { count: checked.unchanged })}</span>}
          {checked.invalidLines.length > 0 && <span className="l10n-warn-text">{t('batch.bad_lines', { lines: checked.invalidLines.slice(0, 10).join(', ') })}</span>}
        </div>
        {(checked.invalid.length > 0 || checked.unknown.length > 0) && <ul className="l10n-item-list">
          {checked.invalid.slice(0, PREVIEW_LIST_LIMIT).map(item => <li key={item.keyCode}><code>{item.keyCode}</code> <span className="l10n-danger-text">{item.reason}</span></li>)}
          {checked.unknown.slice(0, PREVIEW_LIST_LIMIT).map(key => <li key={key}><code>{key}</code> <span className="l10n-warn-text">{t('batch.unknown_key')}</span></li>)}
          {checked.invalid.length + checked.unknown.length > PREVIEW_LIST_LIMIT * 2 && <li className="muted">{t('common.and_more', { count: formatNumber(checked.invalid.length + checked.unknown.length - PREVIEW_LIST_LIMIT * 2) })}</li>}
        </ul>}
      </>}
      <div className="l10n-info-bar">{Icons.info()} {t('batch.info')}</div>
    </div>
  </Modal>;
}
