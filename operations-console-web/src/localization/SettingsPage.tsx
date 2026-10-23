import { useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Checkbox, Dropdown, Empty, Input, InputNumber, Modal, Spin, Switch, Tooltip } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { formatNumber, formatShortDateTime } from '../i18n';
import { applicationCodePattern, codeProblem } from './importModel';
import { BASE_LOCALE, decodeApplication, decodeApplicationDetail, decodeApplications, decodeLocale, decodeLocales, decodeNamespaces, decodeNothing,
  localizationButtons, localizationPaths, localizationRoutes } from './localizationApi';
import type { Application, ApplicationDetail, Locale, Namespace } from './localizationApi';
import { Icons, LoadFailed, LocalizationFrame, Segmented, hasButton, localeName, shortAccount, useAdminT, useEnumT, useLoad } from './LocalizationParts';
import { NamespaceModal } from './NamespaceModal';
import { coverageChecks, draftOf, enabledLocales, lowestRequiredCoverage, settingsDirty, thresholdChoices, thresholdOptions } from './settingsModel';
import type { SettingsDraft } from './settingsModel';
import { useLocalizationCommand } from './useLocalizationCommand';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/** 语言与应用（原型 07—09）：页签"应用 / 语言"；应用表与设置面板（默认语言、必须翻译语言、发布要求、命名空间）；Locale 目录。 */
export function SettingsPage(props: Props) {
  return <LocalizationFrame><Settings {...props} /></LocalizationFrame>;
}

function Settings({ resources, onAuthorizationChanged }: Props) {
  const t = useAdminT();
  const [params, setParams] = useSearchParams();
  const canEdit = hasButton(resources, localizationButtons.settingsEdit);
  const canCreate = hasButton(resources, localizationButtons.applicationCreate);
  const canManageNamespaces = hasButton(resources, localizationButtons.namespaceManage);
  const tab = params.get('tab') === 'locales' ? 'locales' : 'applications';
  const locales = useLoad(localizationPaths.locales, decodeLocales);
  const applications = useLoad(localizationPaths.applications, decodeApplications);
  const [details, setDetails] = useState<Record<string, ApplicationDetail>>({});
  const [creatingApp, setCreatingApp] = useState(false);
  const [localeEditor, setLocaleEditor] = useState<Locale | 'new' | null>(null);
  // 应用表的覆盖率与线上版本来自各应用详情（首期 4 个应用）。逐个加载：Operations 路由对每个登录用户的并发上限为 2，
  // 并行请求会被 Gateway 以 429 拒绝；单个应用失败不影响其余行。
  useEffect(() => {
    if (!applications.data) return;
    const controller = new AbortController();
    const codes = applications.data.map(item => item.applicationCode);
    void (async () => {
      for (const code of codes) {
        if (controller.signal.aborted) return;
        try {
          const detail = await session.request(localizationPaths.application(code), { signal: controller.signal },
            decodeApplicationDetail);
          if (!controller.signal.aborted) setDetails(current => ({ ...current, [detail.application.applicationCode]: detail }));
        } catch { /* 该行保持占位，其余应用继续加载 */ }
      }
    })();
    return () => controller.abort();
  }, [applications.data]);
  const selectedCode = params.get('app');
  const selected = applications.data?.find(item => item.applicationCode === selectedCode) ?? null;
  function update(patch: Record<string, string | null>) {
    setParams(current => {
      const next = new URLSearchParams(current);
      for (const [name, value] of Object.entries(patch)) { if (value === null) next.delete(name); else next.set(name, value); }
      return next;
    }, { replace: true });
  }
  if (locales.failure || applications.failure) return <LoadFailed failure={locales.failure ?? applications.failure} retry={() => { locales.refresh(); applications.refresh(); }} />;
  if (!locales.data || !applications.data) return <div className="content-loading"><Spin /></div>;
  const refreshAll = () => { applications.refresh(); };
  return <div className="l10n-workbench-page">
    <header className="l10n-head">
      <h1>{t('settings.title')}</h1>
      <nav className="l10n-tabs" aria-label={t('settings.title')}>
        <button type="button" className={tab === 'applications' ? 'is-active' : ''} onClick={() => update({ tab: null })}>
          {t('settings.tab.applications')}<span className="l10n-count">{applications.data.length}</span></button>
        <button type="button" className={tab === 'locales' ? 'is-active' : ''} onClick={() => update({ tab: 'locales', app: null })}>
          {t('settings.tab.locales')}<span className="l10n-count">{locales.data.length}</span></button>
      </nav>
      <span className="l10n-grow" />
      {tab === 'applications' ? canCreate && <Button type="primary" icon={Icons.plus()} onClick={() => setCreatingApp(true)}>{t('settings.create_application')}</Button>
        : canEdit && <Button type="primary" icon={Icons.plus()} onClick={() => setLocaleEditor('new')}>{t('locales.create')}</Button>}
    </header>
    {tab === 'applications' ? <section className={`l10n-card l10n-settings${selected ? ' has-detail' : ''}`}>
      <div className="l10n-release-table">
        {!applications.data.length ? <Empty className="l10n-pad" description={t('translations.no_applications')} /> : <table>
          <thead><tr><th>{t('settings.col.application')}</th><th>{t('settings.col.default_locale')}</th><th>{t('settings.col.live')}</th>
            <th>{t('settings.col.coverage')}</th><th>{t('settings.col.updated')}</th></tr></thead>
          <tbody>{applications.data.map(item => {
            const detail = details[item.applicationCode];
            const coverage = detail ? lowestRequiredCoverage(detail.coverage) : null;
            return <tr key={item.applicationCode} tabIndex={0} className={item.applicationCode === selectedCode ? 'is-selected' : ''}
              onClick={() => update({ app: item.applicationCode })} onKeyDown={event => { if (event.key === 'Enter') update({ app: item.applicationCode }); }}>
              <td><strong>{item.applicationName}</strong><div><code className="muted">{item.applicationCode}</code></div></td>
              <td>{localeName(locales.data, item.defaultLocale)}</td>
              <td>{item.currentReleaseNo ? <strong>#{item.currentReleaseNo}</strong> : <span className="muted">{t('settings.unpublished')}</span>}</td>
              <td>{coverage ? <Tooltip title={localeName(locales.data, coverage.locale)}>
                <strong className={coverage.satisfied ? 'l10n-ok-text' : 'l10n-warn-text'}>{coverage.coveragePercent}%</strong></Tooltip> : <span className="muted">—</span>}</td>
              <td className="muted"><span title={item.updatedBy ?? undefined}>{shortAccount(item.updatedBy)}</span> {formatShortDateTime(item.updateTime)}</td>
            </tr>;
          })}</tbody>
        </table>}
      </div>
      {selected && <ApplicationPanel key={selected.applicationCode + ':' + selected.version} application={selected} detail={details[selected.applicationCode]}
        locales={locales.data} canEdit={canEdit} canManageNamespaces={canManageNamespaces} onAuthorizationChanged={onAuthorizationChanged} onClose={() => update({ app: null })} onSaved={refreshAll} />}
    </section> : <LocaleCatalog locales={locales.data} canEdit={canEdit} onAuthorizationChanged={onAuthorizationChanged} onEdit={setLocaleEditor} onChanged={locales.refresh} />}
    {creatingApp && <CreateApplicationModal locales={locales.data} existing={applications.data.map(item => item.applicationCode)}
      onAuthorizationChanged={onAuthorizationChanged} onClose={() => setCreatingApp(false)}
      onCreated={application => { setCreatingApp(false); applications.refresh(); update({ app: application.applicationCode }); }} />}
    {localeEditor && <LocaleModal editing={localeEditor === 'new' ? undefined : localeEditor} existing={locales.data}
      onAuthorizationChanged={onAuthorizationChanged} onClose={() => setLocaleEditor(null)} onSaved={() => { setLocaleEditor(null); locales.refresh(); }} />}
  </div>;
}

/** 应用设置面板（原型 07）。 */
function ApplicationPanel({ application, detail, locales, canEdit, canManageNamespaces, onAuthorizationChanged, onClose, onSaved }: {
  application: Application; detail?: ApplicationDetail; locales: Locale[]; canEdit: boolean; canManageNamespaces: boolean;
  onAuthorizationChanged: () => void; onClose: () => void; onSaved: () => void;
}) {
  const t = useAdminT(); const { message, modal } = App.useApp(); const navigate = useNavigate();
  const [draft, setDraft] = useState<SettingsDraft>(() => draftOf(application));
  const [error, setError] = useState<string | null>(null);
  const [renaming, setRenaming] = useState(false);
  const namespaces = useLoad(localizationPaths.namespaces(application.applicationCode), decodeNamespaces);
  const [namespaceEditor, setNamespaceEditor] = useState<Namespace | 'new' | null>(null);
  const command = useLocalizationCommand(onAuthorizationChanged);
  const options = enabledLocales(locales);
  const dirty = settingsDirty(application, draft);
  const checks = detail ? coverageChecks(detail.coverage, draft) : [];
  async function save(name = application.applicationName) {
    setError(null);
    const result = await command.run(localizationPaths.application(application.applicationCode), 'PUT', { expectedVersion: application.version,
      applicationName: name, defaultLocale: draft.defaultLocale, requiredLocales: draft.requiredLocales,
      coverageThresholdPercent: draft.coverageThresholdPercent }, decodeApplication, t('settings.save_failed'));
    if (!result) return false;
    if (result.ok) { void message.success(t('settings.saved')); onSaved(); return true; }
    setError(result.failure.kind === 'conflict' ? t('common.conflict_refresh') : result.failure.kind === 'rejected' ? result.failure.detail || t('common.rejected') : null);
    return false;
  }
  function removeNamespace(namespace: Namespace) {
    modal.confirm({ title: t('namespace.delete_title', { code: namespace.namespaceCode }), content: t('namespace.delete_body'),
      okText: t('common.delete'), okButtonProps: { danger: true }, cancelText: t('common.cancel'),
      onOk: async () => {
        const result = await command.run(`${localizationPaths.namespace(application.applicationCode, namespace.namespaceCode)}?expectedVersion=${namespace.version}`,
          'DELETE', undefined, decodeNothing, t('namespace.delete_failed'));
        if (result?.ok) { void message.success(t('namespace.deleted')); namespaces.refresh(); onSaved(); }
        else if (result && !result.ok && result.failure.kind !== 'other') void message.error(result.failure.kind === 'conflict' ? t('common.conflict_refresh') : result.failure.detail || t('common.rejected'));
      } });
  }
  const sortedNamespaces = (namespaces.data ?? []).toSorted((left, right) => left.sortOrder - right.sortOrder || left.namespaceCode.localeCompare(right.namespaceCode));
  return <aside className="l10n-release-detail l10n-app-panel" aria-label={application.applicationName}>
    <div className="l10n-detail-scroll">
      <header className="l10n-release-head">
        <div className="l10n-grow"><h2>{application.applicationName}</h2>
          <p className="muted"><code>{application.applicationCode}</code> · {t('settings.code_usage')}</p></div>
        <button type="button" className="l10n-icon-button" aria-label={t('common.close')} onClick={onClose}>{Icons.close()}</button>
      </header>
      <div className="l10n-detail-body">
        <div className="l10n-section-label"><strong>{t('settings.default_locale')}</strong><span className="muted">{t('settings.default_locale_hint')}</span></div>
        <Segmented value={draft.defaultLocale} disabled={!canEdit} label={t('settings.default_locale')}
          options={options.map(locale => ({ value: locale.localeCode, label: locale.nativeName }))} onChange={value => setDraft({ ...draft, defaultLocale: value })} />

        <div className="l10n-section-label"><strong>{t('settings.required_locales')}</strong><span className="muted">{t('settings.required_locales_hint')}</span></div>
        <div className="l10n-checks">
          <Checkbox checked disabled>{localeName(locales, BASE_LOCALE)} · {t('settings.base')}</Checkbox>
          {options.filter(locale => locale.localeCode !== BASE_LOCALE).map(locale => <Checkbox key={locale.localeCode} disabled={!canEdit}
            checked={draft.requiredLocales.includes(locale.localeCode)} onChange={event => setDraft({ ...draft, requiredLocales: event.target.checked
              ? [...draft.requiredLocales, locale.localeCode] : draft.requiredLocales.filter(code => code !== locale.localeCode) })}>{locale.nativeName}</Checkbox>)}
        </div>

        <div className="l10n-section-label"><strong>{t('settings.threshold')}</strong><span className="muted">{t('settings.threshold_hint')}</span></div>
        <Segmented value={draft.coverageThresholdPercent} disabled={!canEdit} label={t('settings.threshold')}
          options={thresholdChoices(application.coverageThresholdPercent).map(value => ({ value, label: `${value}%` }))} onChange={value => setDraft({ ...draft, coverageThresholdPercent: value })} />
        <div className="l10n-coverage-checks">
          {!detail ? <Spin size="small" /> : !checks.length ? <span className="muted">{t('settings.no_required')}</span>
            : checks.map(check => <div key={check.locale} className={check.satisfied ? 'l10n-ok-text' : 'l10n-warn-text'}>
              {check.satisfied ? Icons.checkCircle(12) : Icons.warning(12)} {check.satisfied
                ? t('settings.check_ok', { language: localeName(locales, check.locale), percent: check.percent })
                : t('settings.check_short', { language: localeName(locales, check.locale), percent: check.percent, count: check.missing })}</div>)}
        </div>

        <div className="l10n-section-label"><strong>{t('settings.namespaces')}</strong><span className="muted">{t('settings.namespace_count', { count: sortedNamespaces.length })}</span>
          <span className="l10n-grow" />{canManageNamespaces && <button type="button" className="l10n-link" onClick={() => setNamespaceEditor('new')}>{Icons.plus(12)} {t('settings.new_namespace')}</button>}</div>
        {namespaces.failure ? <LoadFailed failure={namespaces.failure} retry={namespaces.refresh} />
          : !namespaces.data ? <Spin size="small" />
          : !sortedNamespaces.length ? <div className="l10n-summary-empty muted">{t('settings.no_namespaces')}</div>
          : <ul className="l10n-namespace-list">{sortedNamespaces.map(namespace => <li key={namespace.namespaceCode}>
            <button type="button" className="l10n-namespace-row" onClick={() => void navigate(`${localizationRoutes.translations}?app=${encodeURIComponent(application.applicationCode)}&ns=${encodeURIComponent(namespace.namespaceCode)}`)}>
              <span className="l10n-grow"><strong>{namespace.namespaceName}</strong> <code className="muted">{namespace.namespaceCode}</code>
                {namespace.description && <span className="muted l10n-namespace-desc">{namespace.description}</span>}</span>
              <span className="muted">{t('settings.key_count', { count: formatNumber(namespace.activeKeyCount) })}</span>{Icons.right(12)}</button>
            {canManageNamespaces && <Dropdown trigger={['click']} menu={{ items: [
              { key: 'edit', label: t('common.edit'), onClick: () => setNamespaceEditor(namespace) },
              { key: 'delete', label: t('common.delete'), danger: true, disabled: namespace.activeKeyCount + namespace.deprecatedKeyCount > 0, onClick: () => removeNamespace(namespace) },
            ] }}><button type="button" className="l10n-icon-button l10n-row-menu" aria-label={t('common.more')}>{Icons.more()}</button></Dropdown>}
          </li>)}</ul>}
        {error && <Alert type="error" showIcon title={error} />}
      </div>
    </div>
    <footer className="l10n-detail-foot">
      <Dropdown trigger={['click']} menu={{ items: [
        { key: 'rename', label: t('settings.rename'), disabled: !canEdit, onClick: () => setRenaming(true) },
        { key: 'copy', label: t('settings.copy_code'), onClick: () => { void navigator.clipboard?.writeText(application.applicationCode).then(() => message.success(t('common.copied'))); } },
      ] }}><button type="button" className="l10n-square-button" aria-label={t('common.more')}>{Icons.more()}</button></Dropdown>
      <span className="l10n-grow" />
      {dirty && <span className="l10n-warn-text">{t('settings.unsaved')}</span>}
      {dirty && <Button onClick={() => setDraft(draftOf(application))}>{t('settings.discard')}</Button>}
      {canEdit && <Button type="primary" disabled={!dirty} loading={command.saving} onClick={() => void save()}>{t('common.save')}</Button>}
    </footer>
    {namespaceEditor && <NamespaceModal applicationCode={application.applicationCode} applicationName={application.applicationName} namespaces={namespaces.data ?? []}
      editing={namespaceEditor === 'new' ? undefined : namespaceEditor} onAuthorizationChanged={onAuthorizationChanged} onClose={() => setNamespaceEditor(null)}
      onSaved={() => { setNamespaceEditor(null); namespaces.refresh(); onSaved(); }} />}
    {renaming && <RenameModal name={application.applicationName} saving={command.saving} onClose={() => setRenaming(false)}
      onSubmit={async name => { if (await save(name)) setRenaming(false); }} />}
  </aside>;
}

function RenameModal({ name, saving, onClose, onSubmit }: { name: string; saving: boolean; onClose: () => void; onSubmit: (name: string) => void }) {
  const t = useAdminT(); const [value, setValue] = useState(name);
  return <Modal open width={440} title={t('settings.rename')} onCancel={onClose} okText={t('common.save')} cancelText={t('common.cancel')}
    okButtonProps={{ disabled: !value.trim() || value.trim() === name, loading: saving }} onOk={() => onSubmit(value.trim())}>
    <Input value={value} maxLength={128} autoFocus onChange={event => setValue(event.target.value)} onPressEnter={() => value.trim() && onSubmit(value.trim())} />
  </Modal>;
}

/** 新建应用（原型 08）。命名空间在后台创建（需求 C9），说明据此调整。 */
function CreateApplicationModal({ locales, existing, onAuthorizationChanged, onClose, onCreated }: {
  locales: Locale[]; existing: string[]; onAuthorizationChanged: () => void; onClose: () => void; onCreated: (application: Application) => void;
}) {
  const t = useAdminT(); const { message } = App.useApp();
  const options = enabledLocales(locales);
  const [name, setName] = useState(''); const [code, setCode] = useState(''); const [touched, setTouched] = useState(false);
  const [draft, setDraft] = useState<SettingsDraft>({ defaultLocale: BASE_LOCALE,
    requiredLocales: options.filter(locale => locale.localeCode !== BASE_LOCALE).map(locale => locale.localeCode), coverageThresholdPercent: 100 });
  const [error, setError] = useState<string | null>(null);
  const command = useLocalizationCommand(onAuthorizationChanged);
  const problem = codeProblem(code, applicationCodePattern, existing);
  const valid = !!name.trim() && !problem;
  async function submit() {
    setTouched(true);
    if (!valid) return;
    setError(null);
    const result = await command.run(localizationPaths.applications, 'POST', { applicationCode: code, applicationName: name.trim(), ...draft },
      decodeApplication, t('app_form.failed'));
    if (!result) return;
    if (result.ok) { void message.success(t('app_form.created', { name: result.value.applicationName })); onCreated(result.value); return; }
    setError(result.failure.kind === 'conflict' ? t('app_form.duplicate') : result.failure.kind === 'rejected' ? result.failure.detail || t('common.rejected') : null);
  }
  return <Modal open width={600} onCancel={onClose} mask={{ closable: false }}
    title={<div className="l10n-modal-title">{t('app_form.title')}<span className="muted">{t('app_form.subtitle')}</span></div>}
    footer={<div className="l10n-modal-foot"><span className="l10n-grow" /><Button onClick={onClose}>{t('common.cancel')}</Button>
      <Button type="primary" loading={command.saving} disabled={touched && !valid} onClick={() => void submit()}>{t('common.create')}</Button></div>}>
    <div className="l10n-form">
      <label className="l10n-form-field"><span className="l10n-form-label">{t('app_form.name')}</span>
        <Input value={name} maxLength={128} autoFocus placeholder={t('app_form.name_placeholder')} status={touched && !name.trim() ? 'error' : undefined}
          onChange={event => setName(event.target.value)} /></label>
      <label className="l10n-form-field"><span className="l10n-form-label">{t('app_form.code')}<span className="muted">{t('app_form.code_hint')}</span></span>
        <Input className="l10n-mono-input" value={code} maxLength={64} placeholder={t('app_form.code_placeholder')} status={(touched || code) && problem ? 'error' : undefined}
          onChange={event => setCode(event.target.value.trim())} />
        {code && (problem ? <span className="l10n-field-note l10n-danger-text">{Icons.error(12)} {t(`app_form.problem.${problem}`)}</span>
          : <span className="l10n-field-note l10n-ok-text">{Icons.checkCircle(12)} {t('app_form.available')}</span>)}</label>
      <div className="l10n-form-field"><span className="l10n-form-label">{t('settings.default_locale')}</span>
        <Segmented value={draft.defaultLocale} label={t('settings.default_locale')} options={options.map(locale => ({ value: locale.localeCode, label: locale.nativeName }))}
          onChange={value => setDraft({ ...draft, defaultLocale: value })} /></div>
      <div className="l10n-form-field"><span className="l10n-form-label">{t('settings.required_locales')}</span>
        <div className="l10n-checks is-inline"><Checkbox checked disabled>{localeName(locales, BASE_LOCALE)} · {t('settings.base')}</Checkbox>
          {options.filter(locale => locale.localeCode !== BASE_LOCALE).map(locale => <Checkbox key={locale.localeCode} checked={draft.requiredLocales.includes(locale.localeCode)}
            onChange={event => setDraft({ ...draft, requiredLocales: event.target.checked ? [...draft.requiredLocales, locale.localeCode]
              : draft.requiredLocales.filter(item => item !== locale.localeCode) })}>{locale.nativeName}</Checkbox>)}</div></div>
      <div className="l10n-form-field"><span className="l10n-form-label">{t('settings.threshold')}<span className="muted">{t('settings.threshold_hint')}</span></span>
        <Segmented value={draft.coverageThresholdPercent} label={t('settings.threshold')} options={thresholdOptions.map(value => ({ value, label: `${value}%` }))}
          onChange={value => setDraft({ ...draft, coverageThresholdPercent: value })} /></div>
      <div className="l10n-info-bar">{Icons.info()} {t('app_form.info')}</div>
      {error && <Alert type="error" showIcon title={error} />}
    </div>
  </Modal>;
}

/** Locale 目录（页签"语言"）：启停、文字方向、排序；en 是基准，不能停用。 */
function LocaleCatalog({ locales, canEdit, onAuthorizationChanged, onEdit, onChanged }: {
  locales: Locale[]; canEdit: boolean; onAuthorizationChanged: () => void; onEdit: (locale: Locale) => void; onChanged: () => void;
}) {
  const t = useAdminT(); const e = useEnumT(); const { message } = App.useApp();
  const command = useLocalizationCommand(onAuthorizationChanged);
  const [pending, setPending] = useState<string | null>(null);
  const sorted = useMemo(() => locales.toSorted((left, right) => left.sortOrder - right.sortOrder || left.localeCode.localeCompare(right.localeCode)), [locales]);
  async function toggle(locale: Locale, enabled: boolean) {
    setPending(locale.localeCode);
    const result = await command.run(localizationPaths.locale(locale.localeCode), 'PUT', { expectedVersion: locale.version, nativeName: locale.nativeName,
      englishName: locale.englishName, textDirection: locale.textDirection, status: enabled ? 'ENABLED' : 'DISABLED', sortOrder: locale.sortOrder },
      decodeLocale, t('locales.save_failed'));
    setPending(null);
    if (!result) return;
    if (result.ok) { void message.success(enabled ? t('locales.enabled', { language: locale.nativeName }) : t('locales.disabled', { language: locale.nativeName })); onChanged(); return; }
    if (result.failure.kind !== 'other') void message.error(result.failure.kind === 'conflict' ? t('common.conflict_refresh') : result.failure.detail || t('common.rejected'));
  }
  return <section className="l10n-card l10n-release-table">
    <table>
      <thead><tr><th>{t('locales.col.language')}</th><th>{t('locales.col.code')}</th><th>{t('locales.col.direction')}</th>
        <th>{t('locales.col.status')}</th><th>{t('locales.col.sort')}</th><th>{t('locales.col.updated')}</th><th /></tr></thead>
      <tbody>{sorted.map(locale => <tr key={locale.localeCode} className="is-static">
        <td><strong>{locale.nativeName}</strong><div className="muted">{locale.englishName}</div></td>
        <td><code>{locale.localeCode}</code>{locale.localeCode === BASE_LOCALE && <span className="l10n-locale-tag">{t('settings.base')}</span>}</td>
        <td>{e(`text_direction.${locale.textDirection.toLowerCase()}`)}</td>
        <td><Tooltip title={locale.localeCode === BASE_LOCALE ? t('locales.base_always_on') : undefined}>
          <span className="l10n-inline"><Switch size="small" checked={locale.status === 'ENABLED'} disabled={!canEdit || locale.localeCode === BASE_LOCALE}
            loading={pending === locale.localeCode} onChange={checked => void toggle(locale, checked)} aria-label={t('locales.col.status')} />
            {e(`locale_status.${locale.status.toLowerCase()}`)}</span></Tooltip></td>
        <td>{locale.sortOrder}</td>
        <td className="muted"><span title={locale.updatedBy ?? undefined}>{shortAccount(locale.updatedBy)}</span> {formatShortDateTime(locale.updateTime)}</td>
        <td className="l10n-right">{canEdit && <Button size="small" onClick={() => onEdit(locale)}>{t('common.edit')}</Button>}</td>
      </tr>)}</tbody>
    </table>
    <p className="muted l10n-pad">{t('locales.note')}</p>
  </section>;
}

/** 新增 / 编辑语言：代码创建后不可修改；文字方向本期只登记（不做 RTL 布局）。 */
function LocaleModal({ editing, existing, onAuthorizationChanged, onClose, onSaved }: {
  editing?: Locale; existing: Locale[]; onAuthorizationChanged: () => void; onClose: () => void; onSaved: () => void;
}) {
  const t = useAdminT(); const e = useEnumT();
  const [code, setCode] = useState(editing?.localeCode ?? '');
  const [nativeName, setNativeName] = useState(editing?.nativeName ?? '');
  const [englishName, setEnglishName] = useState(editing?.englishName ?? '');
  const [direction, setDirection] = useState<'LTR' | 'RTL'>(editing?.textDirection ?? 'LTR');
  const [sortOrder, setSortOrder] = useState<number>(editing?.sortOrder ?? Math.min(existing.reduce((max, item) => Math.max(max, item.sortOrder), 0) + 10, 9999));
  const [error, setError] = useState<string | null>(null);
  const command = useLocalizationCommand(onAuthorizationChanged);
  const codeValid = !!editing || /^[a-z]{2,3}(-[A-Z][a-z]{3})?(-([A-Z]{2}|[0-9]{3}))?$/.test(code) && !existing.some(item => item.localeCode === code);
  const valid = codeValid && !!nativeName.trim() && !!englishName.trim();
  async function submit() {
    if (!valid) return;
    setError(null);
    const body = { nativeName: nativeName.trim(), englishName: englishName.trim(), textDirection: direction, sortOrder };
    const result = editing
      ? await command.run(localizationPaths.locale(editing.localeCode), 'PUT', { ...body, status: editing.status, expectedVersion: editing.version }, decodeLocale, t('locales.save_failed'))
      : await command.run(localizationPaths.locales, 'POST', { ...body, localeCode: code }, decodeLocale, t('locales.save_failed'));
    if (!result) return;
    if (result.ok) { onSaved(); return; }
    setError(result.failure.kind === 'conflict' ? t('common.conflict_refresh') : result.failure.kind === 'rejected' ? result.failure.detail || t('common.rejected') : null);
  }
  return <Modal open width={520} onCancel={onClose} mask={{ closable: false }} title={editing ? t('locales.edit_title', { code: editing.localeCode }) : t('locales.create_title')}
    footer={<div className="l10n-modal-foot"><span className="l10n-grow" /><Button onClick={onClose}>{t('common.cancel')}</Button>
      <Button type="primary" disabled={!valid} loading={command.saving} onClick={() => void submit()}>{editing ? t('common.save') : t('common.create')}</Button></div>}>
    <div className="l10n-form">
      <label className="l10n-form-field"><span className="l10n-form-label">{t('locales.code')}<span className="muted">{t('locales.code_hint')}</span></span>
        <Input className="l10n-mono-input" value={code} disabled={!!editing} maxLength={35} autoFocus={!editing} placeholder="pt-BR"
          status={code && !codeValid ? 'error' : undefined} onChange={event => setCode(event.target.value.trim())} />
        {!editing && code && !codeValid && <span className="l10n-field-note l10n-danger-text">{Icons.error(12)} {t('locales.code_invalid')}</span>}</label>
      <label className="l10n-form-field"><span className="l10n-form-label">{t('locales.native_name')}</span>
        <Input value={nativeName} maxLength={64} autoFocus={!!editing} onChange={event => setNativeName(event.target.value)} /></label>
      <label className="l10n-form-field"><span className="l10n-form-label">{t('locales.english_name')}</span>
        <Input value={englishName} maxLength={64} onChange={event => setEnglishName(event.target.value)} /></label>
      <div className="l10n-form-field"><span className="l10n-form-label">{t('locales.col.direction')}<span className="muted">{t('locales.direction_hint')}</span></span>
        <Segmented value={direction} label={t('locales.col.direction')} options={(['LTR', 'RTL'] as const).map(value => ({ value, label: e(`text_direction.${value.toLowerCase()}`) }))}
          onChange={setDirection} /></div>
      <label className="l10n-form-field"><span className="l10n-form-label">{t('locales.col.sort')}</span>
        <InputNumber min={0} max={9999} precision={0} value={sortOrder} onChange={value => setSortOrder(value ?? 0)} /></label>
      {error && <Alert type="error" showIcon title={error} />}
    </div>
  </Modal>;
}
