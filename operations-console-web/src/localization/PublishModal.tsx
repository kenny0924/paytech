import { useState } from 'react';
import { Alert, Button, Input, Modal, Spin } from 'antd';
import { formatNumber } from '../i18n';
import { SummaryRow } from './ImportKeysModal';
import { BASE_LOCALE, decodePrecheck, decodeReleaseSummary, localizationPaths } from './localizationApi';
import type { Application, Locale, LocaleKey } from './localizationApi';
import { Icons, LoadFailed, localeName, useAdminT, useLoad } from './LocalizationParts';
import type { Tone } from './LocalizationParts';
import type { ReactNode } from 'react';

const changeTone: Record<ChangeSummaryRow['kind'], Tone> = { added: 'green', translation: 'blue', source: 'orange', removed: 'red' };
const changeIcon: Record<ChangeSummaryRow['kind'], ReactNode> = { added: Icons.plus(12), translation: Icons.translate(12), source: Icons.pencil(12), removed: Icons.minus(12) };
import { PREVIEW_LIST_LIMIT } from './importModel';
import { issueGroups } from './translationModel';
import { summaryRows, summaryTotal } from './releaseModel';
import type { SummaryRow as ChangeSummaryRow } from './releaseModel';
import { useLocalizationCommand } from './useLocalizationCommand';

/**
 * 发布确认（原型 04）：打开即预检；有阻止项时逐条列出并禁用确认；发布说明选填；确认后跳到发布记录并轮询上线进度。
 * 变更摘要取预检的 changeSummary（相对线上发布）；为 null（读取端口未接入）时退回发布内容（各语言覆盖、回退英文与基准已变更）并注明。
 */
export function PublishModal({ application, liveReleaseNo, locales, onAuthorizationChanged, onClose, onLocate, onPublished }: {
  application: Application; liveReleaseNo: number | null; locales: Locale[]; onAuthorizationChanged: () => void; onClose: () => void;
  onLocate: (keyCode: string, locale: string) => void; onPublished: (releaseNo: number) => void;
}) {
  const t = useAdminT();
  const precheck = useLoad(localizationPaths.precheck(application.applicationCode), decodePrecheck);
  const [note, setNote] = useState('');
  const [expanded, setExpanded] = useState<string | null>(null);
  const [failure, setFailure] = useState<{ title: string; errors: readonly { field: string; message: string }[] } | null>(null);
  const command = useLocalizationCommand(onAuthorizationChanged);
  const data = precheck.data;
  const groups = issueGroups(data ?? null, application.coverageThresholdPercent);
  async function publish() {
    if (!data?.publishable) return;
    setFailure(null);
    const result = await command.run(localizationPaths.releases(application.applicationCode), 'POST',
      note.trim() ? { releaseNote: note.trim() } : {}, decodeReleaseSummary, t('publish.failed'));
    if (!result) return;
    if (result.ok) { onPublished(result.value.releaseNo); return; }
    const problem = result.failure;
    if (problem.kind === 'conflict') { setFailure({ title: problem.code === 'I18N_CONTENT_CHANGED' ? t('publish.content_changed') : t('common.conflict_refresh'), errors: [] }); precheck.refresh(); }
    else if (problem.kind === 'rejected') { setFailure({ title: problem.detail || t('common.rejected'), errors: problem.errors }); precheck.refresh(); }
  }
  const nextNo = data?.nextReleaseNo;
  const keyList = (items: LocaleKey[], total: number) => <ul className="l10n-item-list">
    {items.slice(0, PREVIEW_LIST_LIMIT).map(item => <li key={item.locale + item.keyCode}><code>{item.keyCode}</code></li>)}
    {total > Math.min(items.length, PREVIEW_LIST_LIMIT) && <li className="muted">{t('common.and_more', { count: formatNumber(total - Math.min(items.length, PREVIEW_LIST_LIMIT)) })}</li>}
  </ul>;
  const sampleList = (row: ChangeSummaryRow) => <ul className="l10n-item-list">
    {row.samples.map(key => <li key={key}><code>{key}</code></li>)}
    {row.count > row.samples.length && <li className="muted">{t('common.and_more', { count: formatNumber(row.count - row.samples.length) })}</li>}
  </ul>;
  const targets = (data?.coverage ?? []).filter(item => item.locale !== BASE_LOCALE);
  const requiredNames = targets.filter(item => item.required).map(item => `${localeName(locales, item.locale)} ${item.coveragePercent}%`).join(t('common.list_separator'));
  return <Modal open width={720} onCancel={onClose} mask={{ closable: false }} className="l10n-modal"
    title={<div className="l10n-modal-title">{nextNo ? t('publish.title', { releaseNo: nextNo }) : t('publish.title_plain')}
      <span className="muted">{application.applicationName} · {liveReleaseNo ? t('publish.live', { releaseNo: liveReleaseNo }) : t('publish.never')}</span></div>}
    footer={<div className="l10n-modal-foot"><span className="l10n-grow" />
      <Button onClick={onClose}>{t('common.cancel')}</Button>
      <Button type="primary" icon={Icons.send()} disabled={!data?.publishable} loading={command.saving} onClick={() => void publish()}>{t('publish.confirm')}</Button></div>}>
    <div className="l10n-modal-body">
      {precheck.failure ? <LoadFailed failure={precheck.failure} retry={precheck.refresh} title={t('publish.precheck_failed')} />
        : !data ? <div className="l10n-progress-row"><Spin size="small" /> {t('publish.checking')}</div> : <>
          {data.changeSummary ? <>
            <div className="l10n-section-label"><strong>{t('publish.changes')}</strong>
              <span className="muted">{data.changeSummary.baseReleaseNo ? t('releases.changes_hint', { base: data.changeSummary.baseReleaseNo, count: summaryTotal(data.changeSummary) })
                : t('releases.changes_first', { count: summaryTotal(data.changeSummary) })}</span></div>
            {summaryRows(data.changeSummary).length ? <div className="l10n-summary-rows">
              {summaryRows(data.changeSummary).map(row => <SummaryRow key={row.kind + row.locale} tone={changeTone[row.kind]} icon={changeIcon[row.kind]}
                title={t(`publish.change.${row.kind}`, { count: row.count, language: localeName(locales, row.locale) })} description={t(`publish.change_desc.${row.kind}`)}
                actionLabel={t('common.view')} expanded={expanded === row.kind + row.locale}
                onAction={() => setExpanded(expanded === row.kind + row.locale ? null : row.kind + row.locale)}>{sampleList(row)}</SummaryRow>)}
            </div> : <div className="l10n-summary-rows"><div className="l10n-summary-empty muted">{t('releases.no_changes')}</div></div>}
          </> : <>
          <div className="l10n-section-label"><strong>{t('publish.content')}</strong>
            <span className="muted">{t('publish.content_hint', { keys: data.activeKeyCount })}</span></div>
            <div className="l10n-summary-rows">
            {targets.map(item => {
              const missing = data.missing.filter(entry => entry.locale === item.locale);
              const count = Math.max(item.totalKeys - item.translatedKeys, 0);
              return <SummaryRow key={item.locale} tone="blue" icon={Icons.translate(12)}
                title={t('publish.locale_row', { language: localeName(locales, item.locale), percent: item.coveragePercent })}
                description={count ? t(item.required ? 'publish.locale_missing_required' : 'publish.locale_missing', { count, threshold: application.coverageThresholdPercent })
                  : t('publish.locale_complete', { count: item.translatedKeys })}
                actionLabel={count ? t('common.view') : undefined} expanded={expanded === item.locale}
                onAction={() => setExpanded(expanded === item.locale ? null : item.locale)}>{keyList(missing, count)}</SummaryRow>;
            })}
            {data.sourceChangedTotal > 0 && <SummaryRow tone="orange" icon={Icons.pencil(12)} title={t('publish.source_changed', { count: data.sourceChangedTotal })}
              description={t('publish.source_changed_hint')} actionLabel={t('common.view')} expanded={expanded === 'source'}
              onAction={() => setExpanded(expanded === 'source' ? null : 'source')}>{keyList(data.sourceChanged, data.sourceChangedTotal)}</SummaryRow>}
          </div>
            <p className="muted l10n-note">{t('publish.no_diff_note')}</p>
          </>}

          <div className="l10n-section-label"><strong>{t('publish.check')}</strong></div>
          {data.publishable ? <div className="l10n-check is-ok">{Icons.checkCircle()} <strong>{t('publish.zero_issues')}</strong>
            <span>{requiredNames ? t('publish.ok_detail', { coverage: requiredNames }) : t('publish.ok_detail_plain')}</span></div>
            : <div className="l10n-check is-blocked">
              <div className="l10n-check-head">{Icons.error()} <strong>{t('issues.badge', { count: groups?.blocking || 1 })}</strong><span>{t('publish.blocked')}</span></div>
              <ul>
                {groups?.coverageGaps.map(gap => <li key={gap.locale}>{t('publish.gap', { language: localeName(locales, gap.locale), count: gap.missing,
                  percent: gap.percent, threshold: gap.threshold })}</li>)}
                {groups?.errors.slice(0, 10).map(item => <li key={item.locale + item.keyCode}><code>{item.keyCode}</code> {item.message}
                  {item.locale && <button type="button" className="l10n-link" onClick={() => onLocate(item.keyCode, item.locale)}>{t('issues.locate')}</button>}</li>)}
                {groups?.noKeys && <li>{t('issues.no_keys')}</li>}
              </ul></div>}

          <div className="l10n-section-label"><strong>{t('publish.note')}</strong><span className="muted">{t('publish.note_hint')}</span></div>
          <Input.TextArea rows={3} maxLength={512} value={note} placeholder={t('publish.note_placeholder')} onChange={event => setNote(event.target.value)} />
          {failure && <Alert type="error" showIcon title={failure.title} description={failure.errors.length > 0 && <ul className="l10n-item-list">
            {failure.errors.slice(0, PREVIEW_LIST_LIMIT).map((item, index) => <li key={index}>{gapLine(item)}</li>)}</ul>} />}
          <div className="l10n-info-bar">{Icons.info()} {liveReleaseNo ? t('publish.info_live', { releaseNo: liveReleaseNo }) : t('publish.info_first')}</div>
        </>}
    </div>
  </Modal>;
}

/**
 * 422 I18N_COVERAGE_INSUFFICIENT 的 errors（卡 B）：missing.{语言} 的 message 是缺翻译的 key，missingTotal 的 message 是总数说明；
 * 其他字段原样显示。
 */
function gapLine(item: { field: string; message: string }) {
  const missing = /^missing\.(.+)$/.exec(item.field);
  if (missing) return <><code>{item.message}</code><span className="l10n-locale-tag">{missing[1]}</span></>;
  if (item.field === 'missingTotal') return <span className="muted">{item.message}</span>;
  return <><code>{item.field}</code> {item.message}</>;
}
