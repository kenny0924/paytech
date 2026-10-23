import { useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, App, Button, Dropdown, Empty, Input, Modal, Pagination, Select, Spin, Tooltip } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { formatNumber, formatShortDateTime } from '../i18n';
import { SummaryRow } from './ImportKeysModal';
import { PREVIEW_LIST_LIMIT } from './importModel';
import { BASE_LOCALE, decodeApplicationDetail, decodeApplications, decodeCurrentRelease, decodeLocales, decodeReleaseDetail, decodeReleaseDiff,
  decodeReleasePage, localizationButtons, localizationPaths } from './localizationApi';
import type { Locale, ReleaseDetail, ReleaseDiff, ReleaseSummary, ReleaseTextChange } from './localizationApi';
import { Icons, LoadFailed, LocalizationFrame, hasButton, localeName, shortAccount, useAdminT, useEnumT, useLoad } from './LocalizationParts';
import type { Tone } from './LocalizationParts';
import { POLL_INTERVAL_MS, bundleSummary, changeRows, hasPending, summaryRows, summaryTotal, pollDecision, progressSteps, releaseDisplay, sizeText,
  switchDirection, switchImpact } from './releaseModel';
import type { ChangeRow, ReleaseDisplay } from './releaseModel';
import { useLocalizationCommand } from './useLocalizationCommand';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const PAGE_SIZE = 20;

/** 发布记录（原型 05、06）：发布表与详情；检查中每 2 秒刷新直至 READY / FAILED；切换线上版本需填原因。 */
export function ReleasesPage(props: Props) {
  return <LocalizationFrame><Releases {...props} /></LocalizationFrame>;
}

function Releases({ resources, onAuthorizationChanged }: Props) {
  const t = useAdminT();
  const [params, setParams] = useSearchParams();
  const canPublish = hasButton(resources, localizationButtons.activate);
  const locales = useLoad(localizationPaths.locales, decodeLocales);
  const applications = useLoad(localizationPaths.applications, decodeApplications);
  const appCode = params.get('app') && applications.data?.some(item => item.applicationCode === params.get('app'))
    ? params.get('app')! : applications.data?.[0]?.applicationCode ?? null;
  const selectedNo = Number(params.get('release')) || null;
  const [page, setPage] = useState(1);
  const detail = useLoad(appCode ? localizationPaths.application(appCode) : null, decodeApplicationDetail);
  const list = useLoad(appCode ? `${localizationPaths.releases(appCode)}?page=${page}&size=${PAGE_SIZE}` : null, decodeReleasePage);
  const [switching, setSwitching] = useState<ReleaseSummary | null>(null);
  function update(patch: Record<string, string | null>) {
    setParams(current => {
      const next = new URLSearchParams(current);
      for (const [name, value] of Object.entries(patch)) { if (value === null) next.delete(name); else next.set(name, value); }
      return next;
    }, { replace: true });
  }
  // 列表里有检查中的发布时每 2 秒刷新列表与页头（终止条件见 releaseModel.pollDecision）。
  const pending = hasPending(list.data?.items ?? []);
  const listRefresh = useRef(list.refresh); listRefresh.current = () => { list.refresh(); detail.refresh(); };
  useEffect(() => {
    if (!pending) return;
    const timer = setInterval(() => listRefresh.current(), POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [pending]);

  if (locales.failure || applications.failure) return <LoadFailed failure={locales.failure ?? applications.failure}
    retry={() => { locales.refresh(); applications.refresh(); }} />;
  if (!applications.data || !locales.data) return <div className="content-loading"><Spin /></div>;
  if (!applications.data.length || !appCode) return <Empty className="l10n-empty-page" description={t('translations.no_applications')} />;
  const current = detail.data?.currentRelease ?? null;
  const items = list.data?.items ?? [];
  const selected = items.find(item => item.releaseNo === selectedNo) ?? null;
  return <div className="l10n-workbench-page">
    <header className="l10n-head">
      <h1>{t('releases.title')}</h1>
      <Select className="l10n-head-select" value={appCode} popupMatchSelectWidth={false} aria-label={t('translations.application')}
        options={applications.data.map(item => ({ value: item.applicationCode, label: item.applicationName }))}
        onChange={value => { setPage(1); update({ app: value, release: null }); }} />
      <span className="l10n-grow" />
      {current ? <span className="l10n-publish-state"><span className="l10n-dot is-green" /><strong>{t('releases.live', { releaseNo: current.releaseNo })}</strong>
        {current.readyTime && <span className="muted"> · {t('releases.live_since', { time: formatShortDateTime(current.readyTime) })}</span>}</span>
        : detail.data ? <span className="l10n-publish-state"><span className="l10n-dot is-gray" />{t('translations.never_published')}</span> : null}
    </header>
    {list.failure ? <LoadFailed failure={list.failure} retry={list.refresh} /> : null}
    <section className={`l10n-card l10n-releases${selected ? ' has-detail' : ''}`}>
      <div className="l10n-release-table">
        <table>
          <thead><tr><th>{t('releases.col.release')}</th><th>{t('releases.col.status')}</th><th>{t('releases.col.change')}</th>
            <th>{t('releases.col.publisher')}</th><th>{t('releases.col.time')}</th></tr></thead>
          <tbody>{items.map(item => {
            const display = releaseDisplay(item);
            return <tr key={item.releaseNo} className={`is-${display}${item.releaseNo === selectedNo ? ' is-selected' : ''}`} tabIndex={0}
              aria-selected={item.releaseNo === selectedNo} onClick={() => update({ release: String(item.releaseNo) })}
              onKeyDown={event => { if (event.key === 'Enter') update({ release: String(item.releaseNo) }); }}>
              <td className="l10n-release-no">#{item.releaseNo}</td>
              <td><StatusBadge display={display} /></td>
              <td className={display === 'failed' ? 'l10n-danger-text' : ''}>{display === 'failed' && item.failureReason ? item.failureReason
                : [item.changeSummary ? t('releases.change_count', { count: summaryTotal(item.changeSummary) }) : t('releases.key_count', { count: item.keyCount }),
                  item.releaseNote].filter(Boolean).join(' · ')}</td>
              <td title={item.createdBy ?? undefined}>{shortAccount(item.createdBy)}</td>
              <td className="muted">{formatShortDateTime(item.createTime)}</td>
            </tr>;
          })}</tbody>
        </table>
        {list.loading && !items.length && <div className="content-loading"><Spin /></div>}
        {!list.loading && !items.length && !list.failure && <Empty className="l10n-pad" description={t('releases.empty')} />}
        {(list.data?.total ?? 0) > PAGE_SIZE && <div className="l10n-pagination"><Pagination size="small" current={page} pageSize={PAGE_SIZE}
          total={list.data?.total ?? 0} showSizeChanger={false} onChange={setPage} /></div>}
      </div>
      {selected && <ReleaseDetailPanel key={`${appCode}:${selected.releaseNo}`} appCode={appCode} summary={selected} locales={locales.data}
        currentReleaseNo={current?.releaseNo ?? null} canPublish={canPublish} onClose={() => update({ release: null })}
        onStatusChanged={() => listRefresh.current()} onSwitch={() => setSwitching(selected)} />}
    </section>
    {switching && <ActivateModal appCode={appCode} applicationName={applications.data.find(item => item.applicationCode === appCode)?.applicationName ?? appCode}
      target={switching} currentReleaseNo={current?.releaseNo ?? null} locales={locales.data} onAuthorizationChanged={onAuthorizationChanged}
      onClose={() => setSwitching(null)} onDone={() => { setSwitching(null); listRefresh.current(); }} />}
  </div>;
}

const statusIcon: Record<ReleaseDisplay, ReactNode> = { checking: Icons.spinner(12), live: Icons.checkCircle(12), offline: Icons.circle(12), failed: Icons.error(12) };
export function StatusBadge({ display, extra }: { display: ReleaseDisplay; extra?: string }) {
  const e = useEnumT();
  return <span className={`l10n-badge is-${display}`}>{statusIcon[display]} {e(`release_display.${display}`)}{extra ? ` ${extra}` : ''}</span>;
}

const changeTone: Record<ChangeRow['kind'], Tone> = { added: 'green', translation: 'blue', source: 'orange', removed: 'red' };
const changeIcon: Record<ChangeRow['kind'], ReactNode> = { added: Icons.plus(12), translation: Icons.translate(12), source: Icons.pencil(12), removed: Icons.minus(12) };

/** 发布详情（原型 05 右侧）：上线进度、相对上一版的变更摘要、语言包；底栏切换线上版本。 */
function ReleaseDetailPanel({ appCode, summary, locales, currentReleaseNo, canPublish, onClose, onStatusChanged, onSwitch }: {
  appCode: string; summary: ReleaseSummary; locales: Locale[]; currentReleaseNo: number | null; canPublish: boolean;
  onClose: () => void; onStatusChanged: () => void; onSwitch: () => void;
}) {
  const t = useAdminT(); const { message } = App.useApp();
  const detail = useLoad(localizationPaths.release(appCode, summary.releaseNo), decodeReleaseDetail);
  // 变更摘要优先用发布自带的 changeSummary（卡 A）；没有时（早期发布）才调两次发布的差异接口。
  const changeSummary = summary.changeSummary;
  const diff = useLoad(changeSummary ? null : localizationPaths.releaseDiff(appCode, summary.releaseNo), decodeReleaseDiff);
  const [timedOut, setTimedOut] = useState(false);
  const release = detail.data?.release ?? summary;
  // 检查中每 2 秒刷新本发布；READY / FAILED 终止并通知列表，超过上限停止。
  const startedAt = useRef(Date.now()); const refresh = useRef(detail.refresh); refresh.current = detail.refresh;
  const lastStatus = useRef(release.status);
  useEffect(() => {
    if (lastStatus.current !== release.status) { lastStatus.current = release.status; onStatusChanged(); }
    const decision = pollDecision(release.status, Date.now() - startedAt.current);
    setTimedOut(decision === 'timeout');
    if (decision !== 'continue') return;
    const timer = setTimeout(() => refresh.current(), POLL_INTERVAL_MS);
    return () => clearTimeout(timer);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [release.status, detail.data]);
  const display = releaseDisplay({ status: release.status, current: release.releaseNo === currentReleaseNo || release.current });
  const [expanded, setExpanded] = useState<string | null>(null);
  const [bundlesOpen, setBundlesOpen] = useState(false);
  const rows: { kind: ChangeRow['kind']; locale: string; count: number; content: ReactNode }[] = changeSummary
    ? summaryRows(changeSummary).map(row => ({ ...row, content: <SampleKeys samples={row.samples} count={row.count} /> }))
    : diff.data ? changeRows(diff.data).map(row => ({ kind: row.kind, locale: row.locale, count: row.items.length, content: <ChangeItems items={row.items} /> })) : [];
  const baseNo = changeSummary ? changeSummary.baseReleaseNo : diff.data?.baseReleaseNo ?? null;
  const total = rows.reduce((sum, row) => sum + row.count, 0);
  const summaryReady = !!changeSummary || !!diff.data;
  const bundles = detail.data ? bundleSummary(detail.data.bundles) : null;
  const direction = switchDirection(release.releaseNo, currentReleaseNo);
  return <aside className="l10n-release-detail" aria-label={`#${release.releaseNo}`}>
    <div className="l10n-detail-scroll">
      <header className="l10n-release-head">
        <div className="l10n-grow"><h2>#{release.releaseNo} <StatusBadge display={display} /></h2>
          <p className="muted">{[shortAccount(release.createdBy), formatShortDateTime(release.createTime), release.releaseNote].filter(Boolean).join(' · ')}</p></div>
        <button type="button" className="l10n-icon-button" aria-label={t('common.close')} onClick={onClose}>{Icons.close()}</button>
      </header>
      <div className="l10n-detail-body">
        {detail.failure ? <LoadFailed failure={detail.failure} retry={detail.refresh} /> : null}
        {release.status !== 'READY' && <Progress release={release} detail={detail.data} currentReleaseNo={currentReleaseNo} timedOut={timedOut} />}
        {release.status === 'READY' && release.releaseNo !== currentReleaseNo && currentReleaseNo !== null && release.releaseNo > currentReleaseNo &&
          <Alert type="info" showIcon title={t('releases.ready_not_live', { current: currentReleaseNo })} />}

        <div className="l10n-section-label"><strong>{t('releases.changes')}</strong>
          {summaryReady && <span className="muted">{baseNo ? t('releases.changes_hint', { base: baseNo, count: total })
            : t('releases.changes_first', { count: total })}</span>}</div>
        {diff.failure ? <LoadFailed failure={diff.failure} retry={diff.refresh} /> : !summaryReady ? <Spin size="small" />
          : !rows.length ? <p className="muted">{t('releases.no_changes')}</p>
          : <div className="l10n-change-list">{rows.map(row => {
            const id = `${row.kind}:${row.locale}`;
            return <div key={id} className="l10n-change">
              <button type="button" className="l10n-change-row" aria-expanded={expanded === id} onClick={() => setExpanded(expanded === id ? null : id)}>
                <span className={`l10n-tone-icon is-${changeTone[row.kind]}`}>{changeIcon[row.kind]}</span>
                <span className="l10n-grow">{row.kind === 'translation' ? t('releases.change.translation', { language: localeName(locales, row.locale) }) : t(`releases.change.${row.kind}`)}</span>
                <strong>{formatNumber(row.count)}</strong>{expanded === id ? Icons.down(12) : Icons.right(12)}</button>
              {expanded === id && row.content}
            </div>;
          })}</div>}

        {bundles && bundles.files > 0 && <>
          <div className="l10n-section-label"><strong>{t('releases.bundles')}</strong></div>
          <div className="l10n-bundle-row">{Icons.box()}
            <span className="l10n-grow">{t('releases.bundle_summary', { locales: bundles.locales.join(t('common.list_separator')), namespaces: bundles.namespaces, size: sizeText(bundles.bytes) })}</span>
            <button type="button" className="l10n-link" aria-expanded={bundlesOpen} onClick={() => setBundlesOpen(value => !value)}>{t('common.view')}</button></div>
          {bundlesOpen && <table className="l10n-bundle-table"><tbody>{detail.data!.bundles.map(bundle => <tr key={bundle.locale + bundle.namespaceCode}>
            <td><code>{bundle.locale}/{bundle.namespaceCode}</code></td><td className="muted">{sizeText(bundle.byteSize)}</td>
            <td><Tooltip title={bundle.contentHash}><code className="muted">{bundle.contentHash.slice(0, 12)}</code></Tooltip></td></tr>)}</tbody></table>}
        </>}
      </div>
    </div>
    <footer className="l10n-detail-foot">
      <Dropdown trigger={['click']} menu={{ items: [
        { key: 'refresh', label: t('common.refresh'), onClick: () => { detail.refresh(); if (!changeSummary) diff.refresh(); } },
        { key: 'copy', label: t('releases.copy_hashes'), disabled: !detail.data, onClick: () => {
          const text = (detail.data?.bundles ?? []).map(bundle => `${bundle.locale}/${bundle.namespaceCode} ${bundle.contentHash}`).join('\n');
          void navigator.clipboard?.writeText(`#${release.releaseNo}\n${text}`).then(() => message.success(t('common.copied')));
        } },
      ] }}><button type="button" className="l10n-square-button" aria-label={t('common.more')}>{Icons.more()}</button></Dropdown>
      <span className="l10n-grow" />
      {display === 'checking' ? <span className="muted">{t('releases.foot.checking')}</span>
        : display === 'failed' ? <span className="muted">{t('releases.foot.failed')}</span>
        : display === 'live' ? <span className="muted">{t('releases.foot.live')}</span>
        : canPublish ? <Button type="primary" icon={Icons.undo()} onClick={onSwitch}>{direction === 'back' ? t('releases.switch_back') : t('releases.switch_forward')}</Button>
        : <span className="muted">{t('releases.foot.no_permission')}</span>}
    </footer>
  </aside>;
}

function Progress({ release, detail, currentReleaseNo, timedOut }: { release: ReleaseSummary; detail?: ReleaseDetail; currentReleaseNo: number | null; timedOut: boolean }) {
  const t = useAdminT();
  const steps = progressSteps(release);
  const files = detail?.bundles.length ?? 0;
  const sub: Record<string, string> = {
    generate: t('releases.step.generate_sub', { count: files }),
    store: t('releases.step.store_sub', { count: files }),
    check: release.status === 'FAILED' ? release.failureReason ?? t('releases.step.check_failed')
      : detail?.checkProgress && detail.checkProgress.total > 0 ? t('releases.step.check_progress', { available: detail.checkProgress.available, total: detail.checkProgress.total })
      : release.status === 'PENDING_CHECK' ? t('releases.step.check_active') : t('releases.step.check_done'),
    activate: release.status === 'READY' ? t('releases.step.activate_done') : currentReleaseNo ? t('releases.step.activate_wait', { current: currentReleaseNo }) : t('releases.step.activate_first'),
  };
  return <>
    <div className="l10n-section-label"><strong>{t('releases.progress')}</strong>{release.status === 'PENDING_CHECK' && <span className="muted">{t('releases.progress_hint')}</span>}</div>
    <ol className="l10n-steps">{steps.map(step => <li key={step.key} className={`is-${step.state}`}>
      <span className="l10n-step-dot">{step.state === 'done' ? Icons.check(11) : step.state === 'failed' ? Icons.close(11) : step.state === 'active' ? Icons.spinner(12) : null}</span>
      <div><strong>{t(`releases.step.${step.key}`)}</strong><div className={step.state === 'active' ? 'l10n-info-text' : step.state === 'failed' ? 'l10n-danger-text' : 'muted'}>{sub[step.key]}
        {step.key === 'check' && (detail?.checkProgress?.rejected ?? 0) > 0 && <span className="l10n-danger-text"> · {t('releases.step.check_rejected', { count: detail!.checkProgress!.rejected })}</span>}</div></div>
    </li>)}</ol>
    {timedOut && <Alert type="warning" showIcon title={t('releases.poll_timeout')} />}
  </>;
}

function SampleKeys({ samples, count }: { samples: string[]; count: number }) {
  const t = useAdminT();
  return <ul className="l10n-item-list">
    {samples.map(key => <li key={key}><code>{key}</code></li>)}
    {count > samples.length && <li className="muted">{t('common.and_more', { count: formatNumber(count - samples.length) })}</li>}
  </ul>;
}

function ChangeItems({ items }: { items: ReleaseTextChange[] }) {
  const t = useAdminT();
  return <ul className="l10n-item-list">
    {items.slice(0, PREVIEW_LIST_LIMIT).map(item => <li key={item.locale + item.keyCode}><code>{item.keyCode}</code>
      {item.locale !== BASE_LOCALE && <span className="l10n-locale-tag">{item.locale}</span>}
      <span className="l10n-diff">{item.before !== null && <del>{item.before}</del>} {item.after !== null && <ins>{item.after}</ins>}</span></li>)}
    {items.length > PREVIEW_LIST_LIMIT && <li className="muted">{t('common.and_more', { count: formatNumber(items.length - PREVIEW_LIST_LIMIT) })}</li>}
  </ul>;
}

/** 切换线上版本（原型 06）：影响说明来自"目标发布相对当前线上"的差异；原因必填并写审计；立即生效。 */
function ActivateModal({ appCode, applicationName, target, currentReleaseNo, locales, onAuthorizationChanged, onClose, onDone }: {
  appCode: string; applicationName: string; target: ReleaseSummary; currentReleaseNo: number | null; locales: Locale[];
  onAuthorizationChanged: () => void; onClose: () => void; onDone: () => void;
}) {
  const t = useAdminT(); const { message } = App.useApp();
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const command = useLocalizationCommand(onAuthorizationChanged);
  const [diff, setDiff] = useState<ReleaseDiff | null>(null); const [diffFailed, setDiffFailed] = useState(false);
  useEffect(() => {
    if (currentReleaseNo === null) return;
    const controller = new AbortController();
    session.request(`${localizationPaths.releaseDiff(appCode, target.releaseNo)}?baseReleaseNo=${currentReleaseNo}`, { signal: controller.signal }, decodeReleaseDiff)
      .then(value => { if (!controller.signal.aborted) setDiff(value); }).catch(() => { if (!controller.signal.aborted) setDiffFailed(true); });
    return () => controller.abort();
  }, [appCode, target.releaseNo, currentReleaseNo]);
  const impact = useMemo(() => diff ? switchImpact(diff) : null, [diff]);
  const direction = switchDirection(target.releaseNo, currentReleaseNo);
  async function submit() {
    if (!reason.trim()) return;
    setError(null);
    const result = await command.run(localizationPaths.currentRelease(appCode), 'POST', { releaseNo: target.releaseNo, reason: reason.trim() },
      decodeCurrentRelease, t('activate.failed'));
    if (!result) return;
    if (result.ok) { void message.success(t('activate.done', { releaseNo: target.releaseNo })); onDone(); return; }
    setError(result.failure.kind === 'conflict' ? t('common.conflict_refresh') : result.failure.kind === 'rejected' ? result.failure.detail || t('common.rejected') : null);
  }
  const title = direction === 'back' ? t('activate.title_back', { releaseNo: target.releaseNo }) : t('activate.title_forward', { releaseNo: target.releaseNo });
  return <Modal open width={600} onCancel={onClose} mask={{ closable: false }}
    title={<div className="l10n-modal-title">{title}<span className="muted">{applicationName} · {currentReleaseNo ? t('activate.current', { releaseNo: currentReleaseNo }) : t('publish.never')}</span></div>}
    footer={<div className="l10n-modal-foot"><span className="l10n-grow" /><Button onClick={onClose}>{t('common.cancel')}</Button>
      <Button type="primary" icon={Icons.undo()} disabled={!reason.trim()} loading={command.saving} onClick={() => void submit()}>
        {direction === 'back' ? t('activate.confirm_back', { releaseNo: target.releaseNo }) : t('activate.confirm_forward', { releaseNo: target.releaseNo })}</Button></div>}>
    <div className="l10n-modal-body">
      <div className="l10n-section-label"><strong>{t('activate.after')}</strong></div>
      <div className="l10n-summary-rows">
        {currentReleaseNo !== null && !impact && !diffFailed && <div className="l10n-progress-row"><Spin size="small" /> {t('activate.loading')}</div>}
        {diffFailed && <div className="l10n-summary-empty muted">{t('activate.diff_failed')}</div>}
        {impact && impact.missingKeys > 0 && <SummaryRow tone="orange" icon={Icons.warning(12)}
          title={t('activate.missing_keys', { count: impact.missingKeys, releaseNo: target.releaseNo })} description={t('activate.missing_keys_hint')} />}
        {impact && impact.extraKeys > 0 && <SummaryRow tone="green" icon={Icons.plus(12)}
          title={t('activate.extra_keys', { count: impact.extraKeys, releaseNo: target.releaseNo })} description={t('activate.extra_keys_hint')} />}
        {impact?.translations.map(item => <SummaryRow key={item.locale} tone="blue" icon={Icons.translate(12)}
          title={t('activate.translations', { count: item.count, language: localeName(locales, item.locale), releaseNo: target.releaseNo })}
          description={item.example ? t('activate.example', { before: item.example.before ?? '', after: item.example.after ?? '' }) : undefined} />)}
        {currentReleaseNo !== null && <SummaryRow tone="green" icon={Icons.checkCircle(12)}
          title={t('activate.kept', { releaseNo: currentReleaseNo })} description={t('activate.kept_hint')} />}
      </div>
      <div className="l10n-section-label"><strong>{t('activate.reason')}</strong><span className="muted">{t('activate.reason_hint')}</span></div>
      <Input.TextArea rows={3} maxLength={256} value={reason} autoFocus placeholder={t('activate.reason_placeholder')}
        status={error ? 'error' : undefined} onChange={event => setReason(event.target.value)} />
      <p className="muted">{t('activate.effect', { releaseNo: target.releaseNo })}</p>
      {error && <Alert type="error" showIcon title={error} />}
    </div>
  </Modal>;
}
