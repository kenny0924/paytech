import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { KeyboardEvent, ReactNode } from 'react';
import { App, Button, Dropdown, Empty, Input, Popover, Select, Spin, Tooltip } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { IntlMessageFormat } from 'intl-messageformat';
import { requestPage } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { useUnsavedRuleGuard } from '../risk/useUnsavedRuleGuard';
import { formatNumber } from '../i18n';
import { IcuEditor, IcuText } from './IcuEditor';
import { formatValues, parseIcu, renameFix, sampleRows, validateTranslation } from './icu';
import type { ArgumentType, SampleValues } from './icu';
import { ImportKeysModal } from './ImportKeysModal';
import { BASE_LOCALE, decodeApplicationDetail, decodeApplications, decodeLocales, decodeNamespaces, decodePrecheck, decodeReleaseDetail,
  decodeSaveTranslations, decodeTranslationFile, decodeTranslationPage, localizationButtons, localizationPaths, localizationRoutes } from './localizationApi';
import type { Locale, TranslationEntry } from './localizationApi';
import { Icons, LoadFailed, LocalizationFrame, hasButton, issueText, localeName, useAdminT, useLoad } from './LocalizationParts';
import { BatchPasteModal } from './BatchPasteModal';
import { PublishModal } from './PublishModal';
import { TranslationImportModal } from './TranslationImportModal';
import { TRANSLATION_PAGE_SIZE, editorState, entryStatus, issueGroups, namespaceOf, withDraftError, nextAfterSave, saveErrorMessages, siblingKey, tabCounts,
  tabState, translationTabs, unpublishedRevisions } from './translationModel';
import type { EntryStatus, IssueGroups, TranslationTab } from './translationModel';
import { useLocalizationCommand } from './useLocalizationCommand';
import { session } from '../api/session';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform);
const ALL = '';

/** 翻译工作台（原型 01、02）：页头选应用与语言、页签、问题浮层、导入与发布；左侧 key 列表，右侧逐条编辑。 */
export function TranslationsPage(props: Props) {
  return <LocalizationFrame><Workbench {...props} /></LocalizationFrame>;
}

function Workbench({ resources, onAuthorizationChanged }: Props) {
  const t = useAdminT();
  const { modal, message } = App.useApp();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const canEdit = hasButton(resources, localizationButtons.translationEdit);
  const canImport = hasButton(resources, localizationButtons.keyImport);
  const canPublish = hasButton(resources, localizationButtons.publish);
  const locales = useLoad(localizationPaths.locales, decodeLocales);
  const applications = useLoad(localizationPaths.applications, decodeApplications);
  const targets = useMemo(() => (locales.data ?? []).filter(locale => locale.status === 'ENABLED' && locale.localeCode !== BASE_LOCALE)
    .toSorted((left, right) => left.sortOrder - right.sortOrder), [locales.data]);
  const appCode = params.get('app') && applications.data?.some(item => item.applicationCode === params.get('app'))
    ? params.get('app')! : applications.data?.[0]?.applicationCode ?? null;
  const locale = params.get('locale') && targets.some(item => item.localeCode === params.get('locale')) ? params.get('locale')! : targets[0]?.localeCode ?? null;
  const namespace = params.get('ns') ?? ALL;
  const tab = (translationTabs as string[]).includes(params.get('tab') ?? '') ? params.get('tab') as TranslationTab : 'all';
  const keyword = params.get('q') ?? '';
  const selectedKey = params.get('key');

  const detail = useLoad(appCode ? localizationPaths.application(appCode) : null, decodeApplicationDetail);
  const currentReleaseNo = detail.data?.application.currentReleaseNo ?? null;
  // 旧响应没有 pendingChangeCount 时才读线上发布，按文案修订号估算。
  const live = useLoad(appCode && currentReleaseNo && detail.data?.application.pendingChangeCount === null
    ? localizationPaths.release(appCode, currentReleaseNo) : null, decodeReleaseDetail);
  const namespaces = useLoad(appCode ? localizationPaths.namespaces(appCode) : null, decodeNamespaces);
  const precheck = useLoad(appCode ? localizationPaths.precheck(appCode) : null, decodePrecheck);

  const [entries, setEntries] = useState<TranslationEntry[]>([]);
  const [entriesTotal, setEntriesTotal] = useState(0);
  const [entriesLoading, setEntriesLoading] = useState(false);
  const [entriesFailure, setEntriesFailure] = useState<unknown>();
  const [entriesReload, setEntriesReload] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const loadedPage = useRef(0);
  const listFilters = useMemo(() => {
    const state = tabState[tab];
    return { locale: locale ?? '', ...(namespace ? { namespaceCode: namespace } : {}), ...(state ? { state } : {}), ...(keyword ? { keyword } : {}) };
  }, [locale, namespace, tab, keyword]);
  // 只读取第一页（列表路由按用户限流，不能一次取完全部 key），其余在列表底部或切换到末尾时按需加载。
  useEffect(() => {
    if (!appCode || !locale) { setEntries([]); setEntriesTotal(0); return; }
    const controller = new AbortController(); setEntriesLoading(true); setEntriesFailure(undefined); loadedPage.current = 0;
    requestPage(localizationPaths.translations(appCode), { page: 1, size: TRANSLATION_PAGE_SIZE }, controller.signal, undefined, listFilters, decodeTranslationPage)
      .then(result => { if (!controller.signal.aborted) { setEntries(result.items); setEntriesTotal(result.total); loadedPage.current = 1; } })
      .catch(error => { if (!controller.signal.aborted) setEntriesFailure(error); })
      .finally(() => { if (!controller.signal.aborted) setEntriesLoading(false); });
    return () => controller.abort();
  }, [appCode, locale, listFilters, entriesReload]);
  const hasMore = entries.length < entriesTotal;
  const loadingMoreRef = useRef(false);
  /** 加载下一页并追加；返回新增的条目（失败或没有更多时为空）。 */
  const loadMore = useCallback(async (): Promise<TranslationEntry[]> => {
    if (!appCode || !locale || loadingMoreRef.current || entries.length >= entriesTotal) return [];
    loadingMoreRef.current = true; setLoadingMore(true);
    try {
      const result = await requestPage(localizationPaths.translations(appCode), { page: loadedPage.current + 1, size: TRANSLATION_PAGE_SIZE },
        undefined, undefined, listFilters, decodeTranslationPage);
      const known = new Set(entries.map(item => item.keyCode));
      const added = result.items.filter(item => !known.has(item.keyCode));
      loadedPage.current += 1; setEntries(current => [...current, ...added]); setEntriesTotal(result.total);
      return added;
    } catch (error) { setEntriesFailure(error); return []; }
    finally { loadingMoreRef.current = false; setLoadingMore(false); }
  }, [appCode, locale, entries, entriesTotal, listFilters]);

  const entry = entries.find(item => item.keyCode === selectedKey) ?? null;
  const [draft, setDraft] = useState<{ keyCode: string; text: string } | null>(null);
  const draftText = entry && draft?.keyCode === entry.keyCode ? draft.text : entry?.text ?? '';
  const dirty = !!entry && draftText !== (entry.text ?? '');
  // 列表加载后选中 URL 中的 key，否则第一条。
  useEffect(() => {
    if (entriesLoading || !entries.length) return;
    if (!selectedKey || !entries.some(item => item.keyCode === selectedKey)) update({ key: entries[0].keyCode }, true);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entries, entriesLoading]);

  function update(patch: Record<string, string | null>, replace = false) {
    setParams(current => {
      const next = new URLSearchParams(current);
      for (const [name, value] of Object.entries(patch)) { if (value === null || value === '') next.delete(name); else next.set(name, value); }
      return next;
    }, { replace });
  }
  const confirmDiscard = useCallback(() => new Promise<boolean>(resolve => {
    modal.confirm({ title: t('editor.leave_title'), content: t('editor.leave_body'), okText: t('editor.leave_ok'), cancelText: t('editor.leave_cancel'),
      okButtonProps: { danger: true }, onOk: () => resolve(true), onCancel: () => resolve(false) });
  }), [modal, t]);
  useUnsavedRuleGuard(dirty, confirmDiscard);
  /** 切换前确认放弃未保存的修改。 */
  async function guarded(action: () => void) {
    if (dirty && !(await confirmDiscard())) return;
    setDraft(null); action();
  }

  const command = useLocalizationCommand(onAuthorizationChanged);
  const [saveFailure, setSaveFailure] = useState<{ keyCode: string; messages: string[]; conflict: boolean } | null>(null);
  const refreshTimer = useRef<ReturnType<typeof setTimeout>>(undefined);
  useEffect(() => () => clearTimeout(refreshTimer.current), []);
  /** 保存后合并刷新计数、问题与未发布提示，连续保存只刷新一次。 */
  const refreshSummary = useCallback(() => {
    clearTimeout(refreshTimer.current);
    refreshTimer.current = setTimeout(() => { detail.refresh(); precheck.refresh(); }, 600);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function save(goNext: boolean) {
    if (!entry || !appCode || !locale || !canEdit) return;
    const state = editorState(entry, draftText);
    if (!state.canSave) return;
    setSaveFailure(null);
    const result = await command.run(localizationPaths.translationBatch(appCode), 'POST',
      { locale, translations: [{ keyCode: entry.keyCode, text: draftText, expectedVersion: entry.version }] }, decodeSaveTranslations, t('editor.save_failed'));
    if (!result) return;
    if (!result.ok) {
      const failure = result.failure;
      if (failure.kind === 'conflict') setSaveFailure({ keyCode: entry.keyCode, messages: [], conflict: true });
      else if (failure.kind === 'rejected') setSaveFailure({ keyCode: entry.keyCode, conflict: false,
        messages: saveErrorMessages(failure.errors).length ? saveErrorMessages(failure.errors) : [failure.detail || t('editor.save_rejected')] });
      return;
    }
    const version = result.value.translations[0]?.version ?? entry.version + 1;
    setEntries(current => current.map(item => item.keyCode === entry.keyCode ? { ...item, text: draftText, version, sourceChanged: false } : item));
    setDraft(null);
    refreshSummary();
    void message.success({ content: t('editor.saved', { key: entry.keyCode }), duration: 1.5 });
    const next = goNext ? nextAfterSave(entries, entry.keyCode) ?? (hasMore ? (await loadMore())[0]?.keyCode ?? null : null) : null;
    if (next) update({ key: next });
  }

  async function move(direction: 1 | -1) {
    let next = siblingKey(entries, selectedKey, direction);
    if (direction === 1 && next === selectedKey && hasMore) next = (await loadMore())[0]?.keyCode ?? next;
    if (next && next !== selectedKey) void guarded(() => update({ key: next }));
  }
  /** 定位到某条：切到其语言与命名空间、全部页签，并以 key 搜索（该条可能不在已加载的页中）。 */
  function locate(keyCode: string, targetLocale = locale) {
    void guarded(() => update({ locale: targetLocale, ns: namespaceOf(keyCode), tab: null, q: keyCode, key: keyCode }));
  }

  const [importing, setImporting] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [translationImport, setTranslationImport] = useState(false);
  const [batchPaste, setBatchPaste] = useState(false);
  const exportTranslations = useCallback(async () => {
    if (!appCode || !locale) return;
    try {
      const file = await session.request(localizationPaths.translationExport(appCode, locale), {}, decodeTranslationFile);
      const blob = new Blob([JSON.stringify(file, null, 2) + '\n'], { type: 'application/json' });
      const url = URL.createObjectURL(blob); const link = document.createElement('a');
      link.href = url; link.download = `${appCode}.${locale}.json`; link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch { void message.error(t('editor.export_failed')); }
  }, [appCode, locale, message, t]);

  // 键盘：⌥↑ / ⌥↓ 切换上一条 / 下一条（编辑框内同样生效）。
  const moveRef = useRef(move); moveRef.current = move;
  useEffect(() => {
    const listener = (event: globalThis.KeyboardEvent) => {
      if (!event.altKey || (event.key !== 'ArrowUp' && event.key !== 'ArrowDown')) return;
      if (document.querySelector('.ant-modal-wrap')) return;
      event.preventDefault(); moveRef.current(event.key === 'ArrowUp' ? -1 : 1);
    };
    window.addEventListener('keydown', listener);
    return () => window.removeEventListener('keydown', listener);
  }, []);

  if (locales.failure || applications.failure) return <LoadFailed failure={locales.failure ?? applications.failure}
    retry={() => { locales.refresh(); applications.refresh(); }} title={t('translations.load_failed')} />;
  if (!locales.data || !applications.data) return <div className="content-loading"><Spin /></div>;
  if (!applications.data.length) return <Empty className="l10n-empty-page" description={t('translations.no_applications')}>
    <Button type="primary" onClick={() => void navigate(localizationRoutes.settings)}>{t('translations.go_settings')}</Button></Empty>;
  if (!targets.length) return <Empty className="l10n-empty-page" description={t('translations.no_target_locales')}>
    <Button onClick={() => void navigate(`${localizationRoutes.settings}?tab=locales`)}>{t('translations.go_locales')}</Button></Empty>;

  const coverage = detail.data?.coverage.find(item => item.locale === locale);
  const counts = tabCounts(coverage);
  const application = detail.data?.application;
  const draftIssues = entry && locale && dirty && draftText.trim() ? validateTranslation(entry.sourceText, draftText) : [];
  const groups = withDraftError(issueGroups(precheck.data ?? null, application?.coverageThresholdPercent ?? 100),
    entry && locale && draftIssues.length ? { locale, keyCode: entry.keyCode, message: issueText(t, draftIssues[0]) } : null);
  const languageName = localeName(locales.data, locale ?? '');
  const noKeys = detail.data?.activeKeyCount === 0;

  return <div className="l10n-workbench-page">
    <header className="l10n-head">
      <h1>{t('translations.title')}</h1>
      <Select className="l10n-head-select" value={appCode ?? undefined} popupMatchSelectWidth={false} aria-label={t('translations.application')}
        options={applications.data.map(item => ({ value: item.applicationCode, label: item.applicationName }))}
        onChange={value => void guarded(() => update({ app: value, ns: null, key: null, q: null }))} />
      <Select className="l10n-head-select" value={locale ?? undefined} popupMatchSelectWidth={false} aria-label={t('translations.locale')}
        options={targets.map(item => ({ value: item.localeCode, label: item.nativeName }))}
        onChange={value => void guarded(() => update({ locale: value, key: null }))} />
      <nav className="l10n-tabs" aria-label={t('translations.tabs')}>
        {translationTabs.map(value => <button type="button" key={value} className={tab === value ? 'is-active' : ''} aria-pressed={tab === value}
          onClick={() => void guarded(() => update({ tab: value === 'all' ? null : value, key: null }))}>
          {t(`translations.tab.${value}`)}<span className="l10n-count">{counts ? formatNumber(counts[value]) : '–'}</span></button>)}
      </nav>
      <div className="l10n-head-actions">
      {groups && groups.blocking > 0
        ? <IssuesBadge groups={groups} locales={locales.data} threshold={application?.coverageThresholdPercent ?? 100}
          onLocate={locate} onWorkThrough={gapLocale => void guarded(() => update({ locale: gapLocale, tab: 'untranslated', ns: null, q: null, key: null }))}
          onReview={() => void guarded(() => update({ tab: 'source_changed', ns: null, q: null, key: null }))} />
        : <PublishState application={application} basedOn={live.data?.basedOnContentRevision ?? null} />}
      {canImport && <Button icon={Icons.upload()} onClick={() => setImporting(true)} disabled={!appCode}>{t('translations.import_keys')}</Button>}
      {canPublish && <Button type="primary" icon={Icons.send()} onClick={() => setPublishing(true)} disabled={!appCode}>{t('translations.publish')}</Button>}
      </div>
    </header>
    {detail.failure ? <LoadFailed failure={detail.failure} retry={detail.refresh} /> : null}
    {noKeys ? <section className="l10n-card l10n-empty-card"><Empty description={<>
      <strong>{t('translations.no_keys_title')}</strong><p className="muted">{t('translations.no_keys_body')}</p></>}>
      {canImport && <Button type="primary" icon={Icons.upload()} onClick={() => setImporting(true)}>{t('translations.import_keys')}</Button>}</Empty></section>
      : <section className="l10n-card l10n-workbench">
        <KeyList entries={entries} total={entriesTotal} loading={entriesLoading} loadingMore={loadingMore} onLoadMore={() => void loadMore()}
          failure={entriesFailure} retry={() => setEntriesReload(value => value + 1)}
          selectedKey={selectedKey} draft={entry && dirty ? { keyCode: entry.keyCode, text: draftText } : null} tab={tab}
          namespace={namespace} namespaces={namespaces.data ?? []} keyword={keyword} filtered={!!(namespace || keyword)}
          onNamespace={value => void guarded(() => update({ ns: value || null, key: null }))}
          onKeyword={value => void guarded(() => update({ q: value || null, key: null }))}
          onSelect={keyCode => keyCode !== selectedKey && void guarded(() => update({ key: keyCode }))} />
        {entry && locale ? <EntryEditor key={`${entry.keyCode}:${locale}`} entry={entry} locale={locale} languageName={languageName}
          draft={draftText} onDraft={text => { setDraft({ keyCode: entry.keyCode, text }); setSaveFailure(null); }} canEdit={canEdit}
          saving={command.saving} failure={saveFailure?.keyCode === entry.keyCode ? saveFailure : null}
          onReload={() => { setSaveFailure(null); setDraft(null); setEntriesReload(value => value + 1); }}
          hasPrev={entries[0]?.keyCode !== entry.keyCode} hasNext={hasMore || entries.at(-1)?.keyCode !== entry.keyCode}
          onMove={direction => void move(direction)} onSave={goNext => void save(goNext)} onExport={() => void exportTranslations()}
          onImportTranslations={() => setTranslationImport(true)} onBatchPaste={() => setBatchPaste(true)} />
          : <div className="l10n-detail l10n-detail-empty">{entriesLoading ? <Spin /> : <Empty description={t('editor.pick')} />}</div>}
      </section>}
    {importing && appCode && <ImportKeysModal application={applications.data.find(item => item.applicationCode === appCode)!}
      liveReleaseNo={currentReleaseNo} namespaces={namespaces.data ?? []} onAuthorizationChanged={onAuthorizationChanged}
      onClose={() => setImporting(false)} onNamespacesChanged={namespaces.refresh}
      onImported={() => { setImporting(false); namespaces.refresh(); detail.refresh(); precheck.refresh(); setEntriesReload(value => value + 1); }} />}
    {publishing && appCode && <PublishModal application={applications.data.find(item => item.applicationCode === appCode)!}
      liveReleaseNo={currentReleaseNo} locales={locales.data} onAuthorizationChanged={onAuthorizationChanged} onClose={() => setPublishing(false)}
      onLocate={(keyCode, problemLocale) => { setPublishing(false); locate(keyCode, problemLocale); }}
      onPublished={releaseNo => { setPublishing(false); void navigate(`${localizationRoutes.releases}?app=${encodeURIComponent(appCode)}&release=${releaseNo}`); }} />}
    {batchPaste && appCode && locale && <BatchPasteModal applicationCode={appCode} applicationName={application?.applicationName ?? appCode}
      locale={locale} languageName={languageName} onAuthorizationChanged={onAuthorizationChanged} onClose={() => setBatchPaste(false)}
      onSaved={() => { setDraft(null); detail.refresh(); precheck.refresh(); setEntriesReload(value => value + 1); }} />}
    {translationImport && appCode && locale && <TranslationImportModal applicationCode={appCode}
      applicationName={application?.applicationName ?? appCode} locale={locale} languageName={languageName}
      onAuthorizationChanged={onAuthorizationChanged} onClose={() => setTranslationImport(false)}
      onImported={() => { setTranslationImport(false); detail.refresh(); precheck.refresh(); setEntriesReload(value => value + 1); }} />}
  </div>;
}

/** 页头右侧：未发布的修改与线上版本（原型 01）。 */
function PublishState({ application, basedOn }: { application?: { contentRevision: number; currentReleaseNo: number | null; pendingChangeCount: number | null }; basedOn: number | null }) {
  const t = useAdminT();
  if (!application) return null;
  if (application.currentReleaseNo === null) return <span className="l10n-publish-state"><span className="l10n-dot is-gray" />{t('translations.never_published')}</span>;
  // 未发布变更数：相对线上发布有变化的 key 数（去重，卡 A）；旧响应按修订号估算。
  const pending = application.pendingChangeCount ?? (basedOn === null ? null : unpublishedRevisions(application.contentRevision, basedOn));
  return <span className="l10n-publish-state">
    <span className={`l10n-dot ${pending ? 'is-orange' : 'is-green'}`} />
    {pending === null ? null : pending ? t('translations.unpublished', { count: pending }) : t('translations.all_published')}
    {pending !== null && ' · '}{t('translations.live_release', { releaseNo: application.currentReleaseNo })}
  </span>;
}

/** "N 个问题"与浮层（原型 02）：译文错误逐条定位、覆盖率缺口逐条处理、基准已变更复核（不阻止发布）。 */
function IssuesBadge({ groups, locales, threshold, onLocate, onWorkThrough, onReview }: {
  groups: IssueGroups; locales: Locale[]; threshold: number;
  onLocate: (keyCode: string, locale: string) => void; onWorkThrough: (locale: string) => void; onReview: () => void;
}) {
  const t = useAdminT(); const [open, setOpen] = useState(false);
  const close = (action: () => void) => () => { setOpen(false); action(); };
  const content = <div className="l10n-issues">
    <div className="l10n-issues-title">{t('issues.title', { count: groups.blocking })}</div>
    {groups.errors.length > 0 && <div className="l10n-issue">
      <span className="l10n-issue-icon is-red">{Icons.error()}</span>
      <div><strong>{t('issues.errors', { count: groups.errors.length })}</strong>
        {groups.errors.slice(0, 5).map(item => <div key={item.locale + item.keyCode} className="l10n-issue-line">
          <span className="muted">{item.keyCode} {item.message}</span>
          {item.locale && <button type="button" className="l10n-link" onClick={close(() => onLocate(item.keyCode, item.locale))}>{Icons.locate(12)} {t('issues.locate')}</button>}
        </div>)}
        {groups.errors.length > 5 && <div className="muted">{t('issues.more', { count: groups.errors.length - 5 })}</div>}</div>
    </div>}
    {groups.coverageGaps.map(gap => <div key={gap.locale} className="l10n-issue">
      <span className="l10n-issue-icon is-orange">{Icons.warning()}</span>
      <div className="l10n-grow"><div className="l10n-issue-line"><strong>{t('issues.untranslated', { count: gap.missing, language: localeName(locales, gap.locale) })}</strong>
        <button type="button" className="l10n-link" onClick={close(() => onWorkThrough(gap.locale))}>{Icons.locate(12)} {t('issues.work_through')}</button></div>
        <div className="muted">{t('issues.requirement', { language: localeName(locales, gap.locale), threshold })}
          {gap.byNamespace.length > 0 && <>{t('issues.separator')}{gap.byNamespace.map(item => `${item.namespace} ${formatNumber(item.count)}`).join(' · ')}</>}</div></div>
    </div>)}
    {groups.noKeys && <div className="l10n-issue"><span className="l10n-issue-icon is-orange">{Icons.warning()}</span><strong>{t('issues.no_keys')}</strong></div>}
    {groups.sourceChangedTotal > 0 && <button type="button" className="l10n-issue is-info" onClick={close(onReview)}>
      <span className="l10n-issue-icon">{Icons.info()}</span>
      <div><div>{t('issues.source_changed', { count: groups.sourceChangedTotal })}</div><div className="muted">{t('issues.source_changed_hint')}</div></div>
    </button>}
  </div>;
  return <Popover open={open} onOpenChange={setOpen} trigger="click" placement="bottomRight" content={content} arrow={false} classNames={{ root: 'l10n-issues-popover' }}>
    <button type="button" className={`l10n-issues-badge${open ? ' is-open' : ''}`} aria-expanded={open}>
      {Icons.warning(13)} {t('issues.badge', { count: groups.blocking })} {open ? Icons.up(12) : Icons.down(12)}</button>
  </Popover>;
}

const statusIcon: Record<EntryStatus, ReactNode> = {
  translated: Icons.check(13), untranslated: Icons.translate(13), source_changed: Icons.warning(13), editing: Icons.pencil(13), error: Icons.error(13),
};

/** 左侧 key 列表：命名空间筛选、搜索、状态图标；当前条高亮，编辑中显示草稿（原型 01）。 */
function KeyList({ entries, total, loading, loadingMore, onLoadMore, failure, retry, selectedKey, draft, tab, namespace, namespaces, keyword, filtered,
  onNamespace, onKeyword, onSelect }: {
  entries: TranslationEntry[]; total: number; loading: boolean; loadingMore: boolean; onLoadMore: () => void; failure: unknown; retry: () => void;
  selectedKey: string | null;
  draft: { keyCode: string; text: string } | null; tab: TranslationTab; namespace: string; namespaces: { namespaceCode: string; namespaceName: string }[];
  keyword: string; filtered: boolean; onNamespace: (value: string) => void; onKeyword: (value: string) => void; onSelect: (keyCode: string) => void;
}) {
  const t = useAdminT();
  const [searching, setSearching] = useState(!!keyword); const [text, setText] = useState(keyword);
  useEffect(() => setText(keyword), [keyword]);
  useEffect(() => {
    if (text.trim() === keyword) return;
    const timer = setTimeout(() => onKeyword(text.trim()), 350);
    return () => clearTimeout(timer);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text]);
  const selectedRef = useRef<HTMLButtonElement>(null);
  useEffect(() => { selectedRef.current?.scrollIntoView({ block: 'nearest' }); }, [selectedKey]);
  // 滚动到列表底部时自动加载下一页（加载中或已全部加载时不触发）。
  const sentinel = useRef<HTMLDivElement>(null);
  const hasMore = entries.length < total;
  useEffect(() => {
    const node = sentinel.current;
    if (!node || !hasMore || loading || loadingMore || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(items => { if (items.some(item => item.isIntersecting)) onLoadMore(); });
    observer.observe(node);
    return () => observer.disconnect();
  }, [hasMore, loading, loadingMore, entries.length, onLoadMore]);
  return <aside className="l10n-keys" aria-label={t('list.label')}>
    <div className="l10n-keys-head">
      {searching ? <Input autoFocus allowClear size="small" value={text} placeholder={t('list.search_placeholder')} prefix={Icons.search(12)}
        onChange={event => setText(event.target.value)} onBlur={() => { if (!text) setSearching(false); }}
        onKeyDown={event => { if (event.key === 'Escape') { setText(''); setSearching(false); } }} />
        : <>
          <Select size="small" variant="filled" className="l10n-ns-select" value={namespace} popupMatchSelectWidth={false}
            labelRender={({ label }) => <span><span className="muted">{t('list.namespace')}</span> {label}</span>}
            options={[{ value: '', label: t('list.all_namespaces') }, ...namespaces.map(item => ({ value: item.namespaceCode, label: item.namespaceCode }))]}
            onChange={onNamespace} />
          <span className="l10n-grow" />
          <Tooltip title={t('list.search')}><button type="button" className="l10n-icon-button" aria-label={t('list.search')} onClick={() => setSearching(true)}>{Icons.search()}</button></Tooltip>
        </>}
    </div>
    {failure ? <div className="l10n-pad"><LoadFailed failure={failure} retry={retry} /></div> : null}
    <div className="l10n-keys-scroll">
      {loading && !entries.length ? <div className="content-loading"><Spin /></div>
        : !entries.length ? <Empty className="l10n-pad" description={filtered ? t('list.empty_filtered') : tab === 'untranslated' ? t('list.empty_untranslated')
          : tab === 'source_changed' ? t('list.empty_source_changed') : t('list.empty')} />
        : entries.map(item => {
          const draftText = draft?.keyCode === item.keyCode ? draft.text : undefined;
          const status = entryStatus(item, draftText);
          const shown = draftText ?? item.text;
          const selected = item.keyCode === selectedKey;
          return <button type="button" key={item.keyCode} ref={selected ? selectedRef : undefined} aria-current={selected}
            className={`l10n-key-row is-${status}${selected ? ' is-selected' : ''}`} onClick={() => onSelect(item.keyCode)}>
            <span className={`l10n-status-icon is-${status}`} title={t(`list.status.${status}`)}>{statusIcon[status]}</span>
            <span className="l10n-key-body">
              <code className="l10n-key-code">{item.keyCode}</code>
              <span className="l10n-key-source">{item.sourceText}</span>
              <span className={`l10n-key-target is-${status}`}>{shown ? shown : t('list.untranslated')}</span>
            </span>
          </button>;
        })}
      {hasMore && !loading && <div ref={sentinel} className="l10n-load-more">
        <Button size="small" type="text" loading={loadingMore} onClick={onLoadMore}>
          {t('list.load_more', { loaded: formatNumber(entries.length), total: formatNumber(total) })}</Button></div>}
    </div>
  </aside>;
}

const argumentTypeKey: Record<ArgumentType, string> = {
  text: 'text', number: 'number', plural: 'number', selectordinal: 'number', select: 'select', date: 'date', time: 'time',
};

/** 右侧逐条编辑（原型 01、02）：英文基准与参数、译文编辑器与即时校验、示例值预览、底栏保存。 */
function EntryEditor({ entry, locale, languageName, draft, onDraft, canEdit, saving, failure, onReload, hasPrev, hasNext, onMove, onSave, onExport, onImportTranslations, onBatchPaste }: {
  entry: TranslationEntry; locale: string; languageName: string; draft: string; onDraft: (text: string) => void; canEdit: boolean; saving: boolean;
  failure: { messages: string[]; conflict: boolean } | null; onReload: () => void; hasPrev: boolean; hasNext: boolean; onMove: (direction: 1 | -1) => void;
  onSave: (goNext: boolean) => void; onExport: () => void; onImportTranslations: () => void; onBatchPaste: () => void;
}) {
  const t = useAdminT(); const { message } = App.useApp();
  const editor = useRef<HTMLTextAreaElement>(null);
  const state = editorState(entry, draft);
  const source = useMemo(() => parseIcu(entry.sourceText), [entry.sourceText]);
  const fix = renameFix(draft, state.issues);
  const pluralHint = source.arguments.some(item => item.type === 'plural') && onlyOtherPlural(locale);
  useEffect(() => { if (canEdit) editor.current?.focus({ preventScroll: true }); }, [canEdit]);
  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) { event.preventDefault(); if (state.canSave && !saving) onSave(true); }
  }
  const types = new Map(source.arguments.map(item => [item.name, item.type]));
  const label = state.issues.length && draft.trim() ? <span className="l10n-danger-text">{t('editor.error_count', { count: state.issues.length })}</span>
    : state.dirty ? <span className="muted">{t('editor.modified')}</span>
    : entry.sourceChanged && entry.text ? <span className="l10n-warn-text">{t('editor.source_changed')}</span>
    : !entry.text ? <span className="l10n-warn-text">{t('editor.untranslated')}</span> : null;
  const shortcut = isMac ? '⌘↵' : 'Ctrl+↵';
  return <section className="l10n-detail" aria-label={entry.keyCode}>
    <div className="l10n-detail-scroll">
      <header className="l10n-detail-head">
        <div className="l10n-grow">
          <h2><code>{entry.keyCode}</code>
            <Tooltip title={t('editor.copy_key')}><button type="button" className="l10n-icon-button" aria-label={t('editor.copy_key')}
              onClick={() => { void navigator.clipboard?.writeText(entry.keyCode).then(() => message.success(t('common.copied'))); }}>{Icons.copy()}</button></Tooltip></h2>
          <p className="muted">{entry.description ? <>{entry.description} · {t('editor.developer_note')}</> : t('editor.no_note')}</p>
        </div>
        <Tooltip title={`${t('editor.prev')} ⌥↑`}><button type="button" className="l10n-square-button" disabled={!hasPrev} aria-label={t('editor.prev')} onClick={() => onMove(-1)}>{Icons.up()}</button></Tooltip>
        <Tooltip title={`${t('editor.next')} ⌥↓`}><button type="button" className="l10n-square-button" disabled={!hasNext} aria-label={t('editor.next')} onClick={() => onMove(1)}>{Icons.down()}</button></Tooltip>
      </header>
      <div className="l10n-detail-body">
        <div className="l10n-section-label"><strong>English</strong><span className="muted">{t('editor.base_hint')}</span></div>
        <div className="l10n-source-box"><IcuText text={entry.sourceText} /></div>
        {entry.parameters.length > 0 && <div className="l10n-params"><span className="muted">{t('editor.parameters')}</span>
          {entry.parameters.map(name => <span key={name} className="l10n-param-chip"><code>{name}</code> {t(`editor.argument_type.${argumentTypeKey[types.get(name) ?? 'text']}`)}</span>)}</div>}

        <div className="l10n-section-label is-target"><strong>{languageName}</strong>{label}</div>
        {failure?.conflict && <div className="l10n-inline-alert is-warn">{Icons.warning()} <span className="l10n-grow">{t('editor.conflict')}</span>
          <Button size="small" onClick={onReload}>{t('common.refresh')}</Button></div>}
        <IcuEditor ref={editor} value={draft} onChange={onDraft} issues={state.issues} invalid={state.issues.length > 0 && !!draft.trim()} readOnly={!canEdit}
          placeholder={canEdit ? t('editor.placeholder', { language: languageName }) : t('editor.readonly_placeholder')} label={languageName} onKeyDown={onKeyDown} lang={locale} />
        {draft.trim() && state.issues.length > 0 ? <div className="l10n-issue-row">
          <ul className="l10n-issue-list">{state.issues.map((issue, index) => <li key={index}>{Icons.error()} {issueText(t, issue)}</li>)}</ul>
          {fix && canEdit && <Button size="small" icon={Icons.wand()} className="l10n-fix-button" onClick={() => { onDraft(fix.text); editor.current?.focus(); }}>
            {t('editor.fix_rename', { name: `{${fix.expected}}` })}</Button>}
        </div> : failure && failure.messages.length > 0 ? <ul className="l10n-issue-list l10n-issue-row">{failure.messages.map(item => <li key={item}>{Icons.error()} {item}</li>)}</ul>
          : pluralHint ? <p className="l10n-hint">{Icons.info()} {t('editor.plural_hint', { language: languageName })}</p> : null}

        <div className="l10n-section-label"><strong>{t('editor.preview')}</strong>
          {source.arguments.length > 0 && <span className="muted">{t('editor.preview_hint', { name: source.arguments[0].name })}</span>}</div>
        <Preview key={entry.keyCode} source={entry.sourceText} translation={draft} locale={locale} blocked={state.issues.length > 0 && !!draft.trim()} />
      </div>
    </div>
    <footer className="l10n-detail-foot">
      <EditorStatus state={state} draft={draft} entry={entry} canEdit={canEdit} saving={saving} />
      <span className="l10n-grow" />
      <Dropdown trigger={['click']} menu={{ items: [
        { key: 'copy', label: t('editor.more.copy_source'), disabled: !canEdit, onClick: () => onDraft(entry.sourceText) },
        { key: 'revert', label: t('editor.more.revert'), disabled: !state.dirty, onClick: () => onDraft(entry.text ?? '') },
        { type: 'divider' },
        { key: 'export', label: t('editor.more.export', { language: languageName }), onClick: onExport },
        { key: 'paste', label: t('editor.more.paste', { language: languageName }), disabled: !canEdit, onClick: onBatchPaste },
        { key: 'import', label: t('editor.more.import', { language: languageName }), disabled: !canEdit, onClick: onImportTranslations },
      ] }}><Button>{t('editor.more.label')} {Icons.down(12)}</Button></Dropdown>
      {canEdit && <Button type="primary" disabled={!state.canSave} loading={saving} onClick={() => onSave(true)}>
        {state.confirmOnly ? t('editor.confirm_next') : hasNext ? t('editor.save_next') : t('editor.save')}<kbd className="l10n-kbd">{shortcut}</kbd></Button>}
    </footer>
  </section>;
}

function EditorStatus({ state, draft, entry, canEdit, saving }: { state: ReturnType<typeof editorState>; draft: string; entry: TranslationEntry; canEdit: boolean; saving: boolean }) {
  const t = useAdminT();
  if (!canEdit) return <span className="l10n-status muted">{Icons.info()} {t('editor.status.readonly')}</span>;
  if (saving) return <span className="l10n-status muted">{t('editor.status.saving')}</span>;
  if (!draft.trim()) return <span className="l10n-status muted">{Icons.translate()} {entry.text ? t('editor.status.empty') : t('editor.status.untranslated')}</span>;
  if (state.issues.length) return <span className="l10n-status l10n-danger-text">{Icons.error()} {t('editor.status.fix_first')}</span>;
  if (state.confirmOnly) return <span className="l10n-status l10n-warn-text">{Icons.warning()} {t('editor.status.confirm_source')}</span>;
  return <span className="l10n-status l10n-ok-text">{Icons.checkCircle()} {t('editor.status.ok')}</span>;
}


/** 该语言的复数规则是否只有 other（如中文），用于"不区分单复数"提示。 */
function onlyOtherPlural(locale: string): boolean {
  try { const categories = new Intl.PluralRules(locale).resolvedOptions().pluralCategories; return categories.length === 1 && categories[0] === 'other'; }
  catch { return false; }
}

/** 示例值预览：每行一组示例值，可改值看结果（原型 01）；有错误时显示占位（原型 02）。 */
function Preview({ source, translation, locale, blocked }: { source: string; translation: string; locale: string; blocked: boolean }) {
  const t = useAdminT();
  const [rows, setRows] = useState<SampleValues[]>(() => sampleRows(source));
  if (blocked) return <div className="l10n-preview-placeholder">{t('editor.preview_blocked')}</div>;
  const format = (text: string, code: string, values: SampleValues) => {
    if (!text.trim()) return null;
    try { return String(new IntlMessageFormat(text, code, undefined, { ignoreTag: true }).format(formatValues(source, values))); }
    catch { return null; }
  };
  return <table className="l10n-preview">
    <tbody>{rows.map((row, index) => <tr key={index}>
      <td className="l10n-preview-values">{Object.keys(row).length ? Object.entries(row).map(([name, value]) => <label key={name}>
        <code>{name} =</code><input value={value} aria-label={name} size={Math.max(value.length, 2)}
          onChange={event => setRows(current => current.map((item, position) => position === index ? { ...item, [name]: event.target.value } : item))} />
      </label>) : <span className="muted">{t('editor.preview_no_params')}</span>}</td>
      <td className="l10n-preview-source">{format(source, BASE_LOCALE, row) ?? '—'}</td>
      <td className="l10n-preview-target">{format(translation, locale, row) ?? <span className="muted">{t('editor.preview_empty')}</span>}</td>
    </tr>)}</tbody>
  </table>;
}
