import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Descriptions, Form, Input, Modal, Select, Space, Spin, Table, Tabs, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { usePaging } from '../hooks/usePaging';
import { enumLabel, formatDateTime } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import { StatusChangeModal } from '../notification/StatusChangeModal';
import {
  codePattern, decodeMutation, decodePreview, decodeProvider, decodeTemplateDetail, decodeTemplateSummary, manageTemplates,
  configStatuses, joinList, missingRequired, notificationAdminPath, pageOf, placeholders, templateRoute,
} from '../notification/notification';
import type { Preview, ProviderSummary, TemplateContent, TemplateDetail, TemplateSummary } from '../notification/notification';
import { useNotificationCommand } from '../notification/useNotificationCommand';
import '../configuration/configuration.css';
import '../notification/notification.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const statusTag = (t: Translate, status: string | null) => status === null ? <span className="muted">{t('templates.notConfigured')}</span>
  : <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>{enumLabel(t, 'enum.status', status)}</Tag>;
const purposeTag = (t: Translate, transientTemplate: boolean) => transientTemplate
  ? <Tag color="orange">{t('templates.purpose.authentication')}</Tag> : <Tag color="blue">{t('templates.purpose.general')}</Tag>;
const summaryPage = pageOf(decodeTemplateSummary);

/** 通知模板：列表与新建（/notification-templates），详情与编辑（/notification-templates/:templateCode）。 */
export function NotificationTemplates(props: Props) {
  const location = useLocation(); const t = useT('notification');
  const code = decodeURIComponent(location.pathname.slice(templateRoute.length).replace(/^\/|\/$/g, ''));
  if (!code) return <TemplateList {...props} />;
  if (!codePattern.test(code)) return <Alert type="warning" showIcon title={t('templates.invalidCode')} />;
  return <TemplateDetailPage key={code} {...props} templateCode={code} />;
}

/** 模板列表：按编码搜索、按状态筛选；有维护权限时可新建。 */
function TemplateList({ resources, onAuthorizationChanged }: Props) {
  const navigate = useNavigate(); const [form] = Form.useForm<{ keyword?: string; status?: string }>();
  const t = useT('notification'), tCommon = useT('common'), tNav = useT('nav');
  const statusOptions = useMemo(() => configStatuses.map(value => ({ value, label: enumLabel(t, 'enum.status', value) })), [t]);
  const canManage = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === manageTemplates);
  const page = usePaging<TemplateSummary>(`${notificationAdminPath}/templates`, summaryPage);
  const [creating, setCreating] = useState(false);
  function search(values: { keyword?: string; status?: string }) {
    page.applyFilters(Object.fromEntries(Object.entries(values).flatMap(([k, v]) => typeof v === 'string' && v.trim() ? [[k, v.trim()]] : [])));
  }
  return <div className="configuration-page notification-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_NOTIFICATION_TEMPLATES')}</h1>{canManage && <Button type="primary" onClick={() => setCreating(true)}>{t('templates.create')}</Button>}</div>
    <Form form={form} layout="vertical" onFinish={search}><div className="notification-search">
      <Form.Item name="keyword" label={t('templates.field.code')}><Input allowClear maxLength={64} placeholder={t('templates.field.keywordPlaceholder')} /></Form.Item>
      <Form.Item name="status" label={t('templates.column.status')}><Select allowClear placeholder={t('records.field.all')} options={statusOptions} /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('records.search')}</Button><Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('records.reset')}</Button></div>
    </div></Form>
    <section className="table-panel"><div className="table-toolbar"><span>{t('templates.count', { count: page.data.total })}</span><Button onClick={page.refresh} loading={page.loading}>{page.error ? tCommon('action.retry') : tCommon('action.refresh')}</Button></div>
      <Table<TemplateSummary> rowKey="templateCode" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading}
        scroll={{ x: 1000 }} pagination={page.pagination} onRow={row => ({ onClick: () => void navigate(`${templateRoute}/${row.templateCode}`), className: 'clickable-row' })} columns={[
          { title: t('templates.column.code'), width: 300, render: (_, row) => <span className="mono">{row.templateCode}</span> },
          { title: t('templates.column.status'), width: 90, render: (_, row) => statusTag(t, row.status) },
          { title: t('templates.column.purpose'), width: 90, render: (_, row) => purposeTag(t, row.transientTemplate) },
          { title: t('templates.column.locales'), width: 180, render: (_, row) => row.locales.length ? row.locales.join(' · ') : <span className="muted">—</span> },
          { title: t('templates.column.route'), width: 110, render: (_, row) => statusTag(t, row.routeStatus) },
          { title: t('templates.column.updatedAt'), width: 180, render: (_, row) => formatDateTime(row.updateTime) },
        ]} /></section>
    {creating && <CreateTemplateModal onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={code => void navigate(`${templateRoute}/${code}`)} />}
  </div>;
}

/** 新建模板：新模板为停用，需添加语言内容并配置路由后再启用。 */
function CreateTemplateModal({ onClose, onCreated, onAuthorizationChanged }: { onClose: () => void; onCreated: (code: string) => void; onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm<{ templateCode: string; reason: string }>(); const command = useNotificationCommand(onAuthorizationChanged, form);
  const t = useT('notification'), tCommon = useT('common');
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(`${notificationAdminPath}/templates`, 'POST', { templateCode: values.templateCode, reason: values.reason.trim() },
      value => decodeMutation(value, values.templateCode), t('templates.createModal.failed'));
    if (result) onCreated(result.code);
  }
  return <Modal open title={t('templates.create')} okText={t('templates.createModal.ok')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} destroyOnHidden>
    <Form form={form} layout="vertical" disabled={command.saving}>
      <Form.Item name="templateCode" label={t('templates.field.code')} extra={t('templates.createModal.codeHint')}
        rules={[{ required: true, message: t('templates.createModal.codeRequired') }, { pattern: codePattern, message: t('templates.createModal.codePattern') }]}><Input maxLength={64} placeholder={t('templates.createModal.codePlaceholder')} /></Form.Item>
      <Form.Item label={t('templates.createModal.medium')}><Input value={t('templates.createModal.mediumValue')} disabled /></Form.Item>
      <Form.Item name="reason" label={t('providerForm.reason')} rules={[{ required: true, whitespace: true, message: t('providerForm.reasonRequired') }]}><Input.TextArea rows={2} maxLength={500} /></Form.Item>
      <div className="muted">{t('templates.createModal.note')}</div>
    </Form>
  </Modal>;
}

type StatusTarget = { kind: 'template' | 'route'; target: 'ACTIVE' | 'DISABLED' };

/** 模板详情：认证提示、发送路由卡片、语言标签页（编辑与预览）。 */
function TemplateDetailPage({ templateCode, resources, onAuthorizationChanged }: Props & { templateCode: string }) {
  const notify = useErrorNotification(); const navigate = useNavigate(); const t = useT('notification'), tCommon = useT('common'), tNav = useT('nav');
  const canManage = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === manageTemplates);
  const [detail, setDetail] = useState<TemplateDetail>(); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  const [status, setStatus] = useState<StatusTarget | null>(null); const [editingRoute, setEditingRoute] = useState(false);
  useEffect(() => {
    const abort = new AbortController(); setFailed(false);
    session.request(`${notificationAdminPath}/templates/${templateCode}`, { signal: abort.signal }, value => decodeTemplateDetail(value, templateCode))
      .then(value => { if (!abort.signal.aborted) setDetail(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, t('templates.detail.loadFailed')); } });
    return () => abort.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [templateCode, reload, notify]);
  const refresh = () => setReload(v => v + 1);
  if (failed && !detail) return <div className="configuration-page"><Alert type="warning" showIcon title={t('templates.detail.unavailable')}
    action={<Button size="small" onClick={refresh}>{tCommon('action.retry')}</Button>} /></div>;
  if (!detail) return <Spin />;
  const passwordReset = templateCode === 'CLIENT_PASSWORD_RESET';
  return <div className="configuration-page notification-page">
    <div className="cell-meta"><a onClick={() => void navigate(templateRoute)}>{tNav('resource.OPERATIONS_NOTIFICATION_TEMPLATES')}</a> / {templateCode}</div>
    <div className="page-heading"><h1>{templateCode}</h1>
      <Space>{detail.transientTemplate && <Tag color="orange">{t('templates.detail.authenticationEmail')}</Tag>}{statusTag(t, detail.status)}<span className="muted">{t('templates.detail.version', { version: detail.version })}</span>
        <Button onClick={refresh}>{tCommon('action.refresh')}</Button>
        {canManage && <Button danger={detail.status === 'ACTIVE'} onClick={() => setStatus({ kind: 'template', target: detail.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })}>
          {detail.status === 'ACTIVE' ? t('templates.detail.disableTemplate') : t('templates.detail.enableTemplate')}</Button>}</Space></div>
    {detail.transientTemplate && <Alert type="warning" showIcon className="notification-hint"
      title={passwordReset ? t('templates.detail.transientHint.passwordReset', { slot: '${link}' })
        : t('templates.detail.transientHint.emailVerification', { slot: '${link}' })} />}
    <section className="notification-card">
      <div className="notification-card-head"><h2>{t('templates.route.title')}</h2>{canManage && <Space>
        <Button onClick={() => setEditingRoute(true)}>{detail.route ? t('templates.route.edit') : t('templates.route.configure')}</Button>
        {detail.route && <Button danger={detail.route.status === 'ACTIVE'} onClick={() => setStatus({ kind: 'route', target: detail.route!.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })}>
          {detail.route.status === 'ACTIVE' ? t('templates.route.disable') : t('templates.route.enable')}</Button>}</Space>}</div>
      {detail.route ? <Descriptions column={5} items={[
        { key: 'primary', label: t('templates.route.primary'), children: <span className="mono">{detail.route.primaryProviderCode}</span> },
        { key: 'fallback', label: t('templates.route.fallback'), children: detail.route.fallbackProviderCode ?? '—' },
        { key: 'status', label: t('templates.column.status'), children: statusTag(t, detail.route.status) },
        { key: 'version', label: t('templates.route.version'), children: detail.route.version },
        { key: 'time', label: t('templates.column.updatedAt'), children: formatDateTime(detail.route.updateTime) },
      ]} /> : <div className="muted">{t('templates.route.none')}</div>}
    </section>
    <ContentTabs detail={detail} canManage={canManage} onSaved={refresh} onAuthorizationChanged={onAuthorizationChanged} />
    {status && <StatusChangeModal title={status.kind === 'template'
      ? (status.target === 'ACTIVE' ? t('templates.status.enableTemplate', { code: templateCode }) : t('templates.status.disableTemplate', { code: templateCode }))
      : (status.target === 'ACTIVE' ? t('templates.status.enableRoute') : t('templates.status.disableRoute'))}
      path={`${notificationAdminPath}/templates/${templateCode}${status.kind === 'route' ? '/route' : ''}/status`} code={templateCode} target={status.target}
      version={status.kind === 'template' ? detail.version : detail.route!.version}
      impact={status.target === 'DISABLED' && detail.transientTemplate
        ? (passwordReset ? t('templates.status.impact.passwordReset') : t('templates.status.impact.emailVerification')) : undefined}
      onClose={() => setStatus(null)} onDone={() => { setStatus(null); refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
    {editingRoute && <RouteModal detail={detail} onClose={() => setEditingRoute(false)} onSaved={() => { setEditingRoute(false); refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 修改或配置路由：主备账号从启用的邮件账号中选择。 */
function RouteModal({ detail, onClose, onSaved, onAuthorizationChanged }: { detail: TemplateDetail; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm<{ primaryProviderCode: string; fallbackProviderCode?: string; reason: string }>();
  const command = useNotificationCommand(onAuthorizationChanged, form); const notify = useErrorNotification();
  const t = useT('notification'), tCommon = useT('common');
  const [providers, setProviders] = useState<ProviderSummary[]>([]);
  useEffect(() => {
    const abort = new AbortController();
    session.request(`${notificationAdminPath}/providers?page=1&size=100`, { signal: abort.signal }, pageOf(decodeProvider))
      .then(value => { if (!abort.signal.aborted) setProviders(value.items); })
      .catch(error => { if (!abort.signal.aborted) notify(error, t('templates.route.providersLoadFailed')); });
    return () => abort.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [notify]);
  const options = providers.filter(p => p.status === 'ACTIVE' && p.medium === 'EMAIL').map(p => ({ value: p.providerCode, label: p.providerCode }));
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(`${notificationAdminPath}/templates/${detail.templateCode}/route`, 'PUT', {
      primaryProviderCode: values.primaryProviderCode, fallbackProviderCode: values.fallbackProviderCode ?? null,
      expectedVersion: detail.route?.version ?? 0, reason: values.reason.trim() }, value => decodeMutation(value, detail.templateCode), t('templates.route.saveFailed'));
    if (result) onSaved();
  }
  return <Modal open title={detail.route ? t('templates.route.editTitle') : t('templates.route.configureTitle')} okText={t('templates.route.save')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }} destroyOnHidden>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict}
      initialValues={{ primaryProviderCode: detail.route?.primaryProviderCode, fallbackProviderCode: detail.route?.fallbackProviderCode ?? undefined }}>
      <Form.Item name="primaryProviderCode" label={t('templates.route.primary')} extra={t('templates.route.primaryHint')} rules={[{ required: true, message: t('templates.route.primaryRequired') }]}>
        <Select options={options} placeholder={t('templates.route.selectPlaceholder')} /></Form.Item>
      <Form.Item name="fallbackProviderCode" label={t('templates.route.fallbackOptional')} dependencies={['primaryProviderCode']}
        extra={t('templates.route.fallbackHint')}
        rules={[({ getFieldValue }) => ({ validator: async (_, value) => { if (value && value === getFieldValue('primaryProviderCode')) throw new Error(t('templates.route.fallbackSame')); } })]}>
        <Select allowClear options={options} placeholder={t('templates.route.selectPlaceholder')} /></Form.Item>
      <Form.Item name="reason" label={t('providerForm.reason')} rules={[{ required: true, whitespace: true, message: t('providerForm.reasonRequired') }]}><Input.TextArea rows={2} maxLength={500} /></Form.Item>
      <div className="muted">{detail.route ? t('templates.route.versionNote', { version: detail.route.version }) : t('templates.route.newNote')}</div>
    </Form>
    {command.conflict && <Alert type="error" showIcon title={t('templates.route.conflict')} />}
  </Modal>;
}

const NEW_LOCALE = '__new__';

/** 语言标签页：切换语言前若有未保存修改需确认。 */
function ContentTabs({ detail, canManage, onSaved, onAuthorizationChanged }: { detail: TemplateDetail; canManage: boolean; onSaved: () => void; onAuthorizationChanged: () => void }) {
  const { modal } = App.useApp(); const t = useT('notification');
  const [active, setActive] = useState(detail.contents[0]?.locale ?? NEW_LOCALE); const [dirty, setDirty] = useState(false);
  const tabs = detail.contents.map(c => ({ key: c.locale, label: c.locale }));
  if (canManage) tabs.push({ key: NEW_LOCALE, label: t('templates.content.addLocale') });
  function change(key: string) {
    if (!dirty) { setActive(key); return; }
    modal.confirm({ title: t('templates.content.discardTitle'), okText: t('templates.content.discard'), cancelText: t('templates.content.keepEditing'), onOk: () => { setDirty(false); setActive(key); } });
  }
  const content = detail.contents.find(c => c.locale === active) ?? null;
  return <section className="notification-card"><div className="notification-card-head"><h2>{t('templates.content.title')}</h2></div>
    {tabs.length === 0 ? <div className="muted">{t('templates.content.empty')}</div> : <>
      <Tabs activeKey={active} onChange={change} items={tabs} />
      {(content || active === NEW_LOCALE) && <ContentEditor key={`${active}:${content?.version ?? 0}`} detail={detail} content={content} canManage={canManage}
        onDirty={setDirty} onSaved={locale => { setDirty(false); setActive(locale); onSaved(); }} onAuthorizationChanged={onAuthorizationChanged} />}
    </>}
  </section>;
}

interface ContentValues { locale?: string; subjectTemplate: string; bodyTemplate: string; reason: string }

/** 内容编辑：点「预览」在弹窗中渲染当前草稿；保存前按发送规则由 Platform 校验，冲突时保留草稿。 */
function ContentEditor({ detail, content, canManage, onDirty, onSaved, onAuthorizationChanged }: {
  detail: TemplateDetail; content: TemplateContent | null; canManage: boolean; onDirty: (dirty: boolean) => void;
  onSaved: (locale: string) => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<ContentValues>(); const command = useNotificationCommand(onAuthorizationChanged, form);
  const t = useT('notification');
  const subject = Form.useWatch('subjectTemplate', form) ?? content?.subjectTemplate ?? '';
  const body = Form.useWatch('bodyTemplate', form) ?? content?.bodyTemplate ?? '';
  const [samples, setSamples] = useState<Record<string, string>>({}); const [previewing, setPreviewing] = useState(false);
  const names = useMemo(() => placeholders(subject, body), [subject, body]);
  const missing = missingRequired(body, detail.requiredParameters);
  async function save(values: ContentValues) {
    const locale = content?.locale ?? values.locale!.trim();
    const result = await command.run(`${notificationAdminPath}/templates/${detail.templateCode}/contents/${encodeURIComponent(locale)}`, 'PUT', {
      subjectTemplate: values.subjectTemplate, bodyTemplate: values.bodyTemplate, expectedVersion: content?.version ?? 0, reason: values.reason.trim(),
    }, value => decodeMutation(value), t('templates.content.saveFailed'));
    if (result) onSaved(result.code);
  }
  return <div className="notification-editor">
    {command.conflict && <Alert type="error" showIcon className="notification-hint" title={t('templates.content.conflict')}
      description={t('templates.content.conflictHint')}
      action={<Space orientation="vertical"><Button size="small" onClick={() => void navigator.clipboard?.writeText(`${subject}\n\n${body}`)}>{t('templates.content.copyDraft')}</Button>
        <Button size="small" type="primary" onClick={() => onSaved(content?.locale ?? '')}>{t('templates.content.reload')}</Button></Space>} />}
    <Form form={form} layout="vertical" disabled={!canManage || command.saving} onFinish={save}
        onValuesChange={() => onDirty(true)} initialValues={{ subjectTemplate: content?.subjectTemplate ?? '', bodyTemplate: content?.bodyTemplate ?? '', reason: '' }}>
        {!content && <Form.Item name="locale" label={t('templates.content.locale')} extra={t('templates.content.localeHint')} rules={[{ required: true, whitespace: true, message: t('templates.content.localeRequired') },
          { validator: async (_, value) => { if (value && detail.contents.some(c => c.locale.toLowerCase() === String(value).trim().toLowerCase())) throw new Error(t('templates.content.localeExists')); } }]}>
          <Input maxLength={35} placeholder={t('templates.content.localePlaceholder')} /></Form.Item>}
        <Form.Item name="subjectTemplate" label={t('templates.content.subject')} rules={[{ required: true, whitespace: true, message: t('templates.content.subjectRequired') },
          { validator: async (_, value) => { if (/[\r\n]/.test(value ?? '')) throw new Error(t('templates.content.subjectNewline')); } }]}>
          <Input maxLength={1024} /></Form.Item>
        <Form.Item name="bodyTemplate" label={t('templates.content.body')} rules={[{ required: true, whitespace: true, message: t('templates.content.bodyRequired') }]}>
          <Input.TextArea rows={9} maxLength={60000} /></Form.Item>
        <div className="notification-slots"><span className="muted">{t('templates.content.placeholders')}</span>
          {detail.requiredParameters.map(name => <Tag key={name} color={missing.includes(name) ? 'red' : 'geekblue'}>
            {missing.includes(name) ? t('templates.content.requiredMissing', { slot: `\${${name}}` }) : t('templates.content.required', { slot: `\${${name}}` })}</Tag>)}
          {names.filter(n => !detail.requiredParameters.includes(n)).map(name => <Tag key={name}>{`\${${name}}`}</Tag>)}
          <span className="muted">{t('templates.content.syntax', { syntax: '${name}' })}</span></div>
        {canManage && <><Form.Item name="reason" label={t('templates.content.reason')} rules={[{ required: true, whitespace: true, message: t('providerForm.reasonRequired') }]}>
          <Input maxLength={500} placeholder={t('templates.content.reasonPlaceholder')} /></Form.Item>
          <div className="configuration-actions"><Button disabled={command.saving || !subject.trim() || !body.trim()} onClick={() => setPreviewing(true)}>{t('templates.content.preview')}</Button>
            <Button disabled={command.saving} onClick={() => { form.resetFields(); onDirty(false); }}>{t('templates.content.discard')}</Button>
            <Button type="primary" htmlType="submit" loading={command.saving} disabled={command.conflict || missing.length > 0}>
              {content ? t('templates.content.saveLocale', { locale: content.locale }) : t('templates.content.save')}</Button></div></>}
    </Form>
    {previewing && <PreviewModal subject={subject} body={body} names={names} samples={samples} onSamples={setSamples} onClose={() => setPreviewing(false)} />}
  </div>;
}

/** 预览弹窗（不发送）：打开时按当前草稿渲染一次；修改示例参数后点「更新预览」重新渲染，示例参数在关闭后保留。 */
function PreviewModal({ subject, body, names, samples, onSamples, onClose }: {
  subject: string; body: string; names: string[]; samples: Record<string, string>;
  onSamples: (update: (samples: Record<string, string>) => Record<string, string>) => void; onClose: () => void;
}) {
  const { preview, loading, render } = usePreview(subject, body); const t = useT('notification');
  const initial = useRef(samples);
  useEffect(() => { void render(initial.current); }, [render]);
  return <Modal open title={t('templates.preview.title')} width={680} onCancel={onClose} destroyOnHidden footer={<Button onClick={onClose}>{t('testSend.close')}</Button>}>
    {names.length > 0 && <div className="notification-samples">
      {names.map(name => <label key={name} className="notification-sample"><span className="muted">{t('templates.preview.parameter', { name })}</span>
        <Input size="small" maxLength={2048} value={samples[name] ?? ''} onChange={e => onSamples(s => ({ ...s, [name]: e.target.value }))} /></label>)}
      <div><Button size="small" loading={loading} onClick={() => void render(samples)}>{t('templates.preview.update')}</Button></div>
    </div>}
    <Spin spinning={loading}><PreviewResult preview={preview} /></Spin>
  </Modal>;
}

/** 按需请求预览：只发送已填写的示例参数；关闭弹窗后丢弃未返回的结果。 */
function usePreview(subject: string, body: string) {
  const notify = useErrorNotification(); const t = useT('notification');
  const [preview, setPreview] = useState<Preview | null>(null); const [loading, setLoading] = useState(false);
  const pending = useRef<AbortController | null>(null);
  useEffect(() => () => pending.current?.abort(), []);
  const render = useCallback(async (samples: Record<string, string>) => {
    pending.current?.abort();
    const abort = new AbortController(); pending.current = abort;
    const parameters = Object.fromEntries(Object.entries(samples).filter(([, v]) => v));
    setLoading(true);
    try {
      const value = await session.request(`${notificationAdminPath}/template-preview`, { method: 'POST', signal: abort.signal,
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({ subjectTemplate: subject, bodyTemplate: body, parameters }) }, decodePreview);
      if (!abort.signal.aborted) setPreview(value);
    } catch (error) {
      if (!abort.signal.aborted) { setPreview(null); notify(error, t('templates.preview.failed')); }
    } finally {
      if (!abort.signal.aborted) setLoading(false);
    }
  // t 只用于失败通知标题；加入依赖会在语言包刷新时重新渲染预览
  }, [subject, body, notify]);
  return { preview, loading, render };
}

/** 渲染后的标题与正文，以及未提供的参数。 */
function PreviewResult({ preview }: { preview: Preview | null }) {
  const t = useT('notification');
  if (!preview) return <div className="notification-mail muted">{t('templates.preview.empty')}</div>;
  return <><div className="notification-mail"><strong>{preview.subject}</strong><pre>{preview.body}</pre></div>
    <div className={preview.missingParameters.length ? 'notification-missing' : 'muted'}>
      {t('templates.preview.missing', { parameters: preview.missingParameters.length
        ? joinList(preview.missingParameters) : t('templates.preview.none') })}</div></>;
}
