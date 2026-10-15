import { useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Descriptions, Form, Input, Modal, Select, Space, Spin, Table, Tabs, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { usePaging } from '../hooks/usePaging';
import { StatusChangeModal } from '../notification/StatusChangeModal';
import {
  codePattern, decodeMutation, decodePreview, decodeProvider, decodeTemplateDetail, decodeTemplateSummary, manageTemplates,
  missingRequired, notificationAdminPath, pageOf, placeholders, statusLabels, templateRoute,
} from '../notification/notification';
import type { Preview, ProviderSummary, TemplateContent, TemplateDetail, TemplateSummary } from '../notification/notification';
import { useNotificationCommand } from '../notification/useNotificationCommand';
import '../configuration/configuration.css';
import '../notification/notification.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
const statusTag = (status: string | null) => status === null ? <span className="muted">未配置</span>
  : <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>{statusLabels[status]}</Tag>;
const purposeTag = (transientTemplate: boolean) => transientTemplate ? <Tag color="orange">认证</Tag> : <Tag color="blue">普通</Tag>;
const summaryPage = pageOf(decodeTemplateSummary);

/** 通知模板：列表与新建（/notification-templates），详情与编辑（/notification-templates/:templateCode）。 */
export function NotificationTemplates(props: Props) {
  const location = useLocation();
  const code = decodeURIComponent(location.pathname.slice(templateRoute.length).replace(/^\/|\/$/g, ''));
  if (!code) return <TemplateList {...props} />;
  if (!codePattern.test(code)) return <Alert type="warning" showIcon title="模板编码不合法" />;
  return <TemplateDetailPage key={code} {...props} templateCode={code} />;
}

/** 模板列表：按编码搜索、按状态筛选；有维护权限时可新建。 */
function TemplateList({ resources, onAuthorizationChanged }: Props) {
  const navigate = useNavigate(); const [form] = Form.useForm<{ keyword?: string; status?: string }>();
  const canManage = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === manageTemplates);
  const page = usePaging<TemplateSummary>(`${notificationAdminPath}/templates`, summaryPage);
  const [creating, setCreating] = useState(false);
  function search(values: { keyword?: string; status?: string }) {
    page.applyFilters(Object.fromEntries(Object.entries(values).flatMap(([k, v]) => typeof v === 'string' && v.trim() ? [[k, v.trim()]] : [])));
  }
  return <div className="configuration-page notification-page">
    <div className="page-heading"><h1>通知模板</h1>{canManage && <Button type="primary" onClick={() => setCreating(true)}>新建模板</Button>}</div>
    <Form form={form} layout="vertical" onFinish={search}><div className="notification-search">
      <Form.Item name="keyword" label="模板编码"><Input allowClear maxLength={64} placeholder="输入编码关键字，如 REFUND" /></Form.Item>
      <Form.Item name="status" label="状态"><Select allowClear placeholder="全部" options={Object.entries(statusLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">查询</Button><Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button></div>
    </div></Form>
    <section className="table-panel"><div className="table-toolbar"><span>共 {page.data.total} 个模板</span><Button onClick={page.refresh} loading={page.loading}>{page.error ? '重试' : '刷新'}</Button></div>
      <Table<TemplateSummary> rowKey="templateCode" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading}
        scroll={{ x: 1000 }} pagination={page.pagination} onRow={row => ({ onClick: () => void navigate(`${templateRoute}/${row.templateCode}`), className: 'clickable-row' })} columns={[
          { title: '编码', width: 300, render: (_, row) => <span className="mono">{row.templateCode}</span> },
          { title: '状态', width: 90, render: (_, row) => statusTag(row.status) },
          { title: '用途', width: 90, render: (_, row) => purposeTag(row.transientTemplate) },
          { title: '语言', width: 180, render: (_, row) => row.locales.length ? row.locales.join(' · ') : <span className="muted">—</span> },
          { title: '路由', width: 110, render: (_, row) => statusTag(row.routeStatus) },
          { title: '更新时间', width: 180, render: (_, row) => date(row.updateTime) },
        ]} /></section>
    {creating && <CreateTemplateModal onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={code => void navigate(`${templateRoute}/${code}`)} />}
  </div>;
}

/** 新建模板：新模板为停用，需添加语言内容并配置路由后再启用。 */
function CreateTemplateModal({ onClose, onCreated, onAuthorizationChanged }: { onClose: () => void; onCreated: (code: string) => void; onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm<{ templateCode: string; reason: string }>(); const command = useNotificationCommand(onAuthorizationChanged, form);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(`${notificationAdminPath}/templates`, 'POST', { templateCode: values.templateCode, reason: values.reason.trim() },
      value => decodeMutation(value, values.templateCode), '新建模板失败');
    if (result) onCreated(result.code);
  }
  return <Modal open title="新建模板" okText="创建" cancelText="取消" confirmLoading={command.saving} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} destroyOnHidden>
    <Form form={form} layout="vertical" disabled={command.saving}>
      <Form.Item name="templateCode" label="模板编码" extra="大写字母开头，只含大写字母、数字和下划线，最多 64 位；创建后不可修改。"
        rules={[{ required: true, message: '请填写模板编码' }, { pattern: codePattern, message: '编码格式不正确' }]}><Input maxLength={64} placeholder="例如 ORDER_PAID_EMAIL" /></Form.Item>
      <Form.Item label="介质"><Input value="EMAIL（本期仅支持邮件）" disabled /></Form.Item>
      <Form.Item name="reason" label="原因" rules={[{ required: true, whitespace: true, message: '请填写原因' }]}><Input.TextArea rows={2} maxLength={500} /></Form.Item>
      <div className="muted">新建后模板为「停用」，添加至少一条语言内容并配置路由后再启用。</div>
    </Form>
  </Modal>;
}

type StatusTarget = { kind: 'template' | 'route'; target: 'ACTIVE' | 'DISABLED' };

/** 模板详情：认证提示、发送路由卡片、语言标签页（编辑与预览）。 */
function TemplateDetailPage({ templateCode, resources, onAuthorizationChanged }: Props & { templateCode: string }) {
  const notify = useErrorNotification(); const navigate = useNavigate();
  const canManage = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === manageTemplates);
  const [detail, setDetail] = useState<TemplateDetail>(); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  const [status, setStatus] = useState<StatusTarget | null>(null); const [editingRoute, setEditingRoute] = useState(false);
  useEffect(() => {
    const abort = new AbortController(); setFailed(false);
    session.request(`${notificationAdminPath}/templates/${templateCode}`, { signal: abort.signal }, value => decodeTemplateDetail(value, templateCode))
      .then(value => { if (!abort.signal.aborted) setDetail(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, '模板加载失败'); } });
    return () => abort.abort();
  }, [templateCode, reload, notify]);
  const refresh = () => setReload(v => v + 1);
  if (failed && !detail) return <div className="configuration-page"><Alert type="warning" showIcon title="模板暂时无法加载"
    action={<Button size="small" onClick={refresh}>重试</Button>} /></div>;
  if (!detail) return <Spin />;
  const purpose = templateCode === 'CLIENT_PASSWORD_RESET' ? '找回密码' : '注册邮箱验证';
  return <div className="configuration-page notification-page">
    <div className="cell-meta"><a onClick={() => void navigate(templateRoute)}>通知模板</a> / {templateCode}</div>
    <div className="page-heading"><h1>{templateCode}</h1>
      <Space>{detail.transientTemplate && <Tag color="orange">认证邮件</Tag>}{statusTag(detail.status)}<span className="muted">版本 {detail.version}</span>
        <Button onClick={refresh}>刷新</Button>
        {canManage && <Button danger={detail.status === 'ACTIVE'} onClick={() => setStatus({ kind: 'template', target: detail.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })}>
          {detail.status === 'ACTIVE' ? '停用模板' : '启用模板'}</Button>}</Space></div>
    {detail.transientTemplate && <Alert type="warning" showIcon className="notification-hint"
      title={`${purpose}邮件使用此模板。正文必须包含 \${link}；停用模板或路由后，用户将收不到${purpose}邮件。`} />}
    <section className="notification-card">
      <div className="notification-card-head"><h2>发送路由</h2>{canManage && <Space>
        <Button onClick={() => setEditingRoute(true)}>{detail.route ? '修改路由' : '配置路由'}</Button>
        {detail.route && <Button danger={detail.route.status === 'ACTIVE'} onClick={() => setStatus({ kind: 'route', target: detail.route!.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })}>
          {detail.route.status === 'ACTIVE' ? '停用路由' : '启用路由'}</Button>}</Space>}</div>
      {detail.route ? <Descriptions column={5} items={[
        { key: 'primary', label: '主账号', children: <span className="mono">{detail.route.primaryProviderCode}</span> },
        { key: 'fallback', label: '备用账号', children: detail.route.fallbackProviderCode ?? '—' },
        { key: 'status', label: '状态', children: statusTag(detail.route.status) },
        { key: 'version', label: '版本', children: detail.route.version },
        { key: 'time', label: '更新时间', children: date(detail.route.updateTime) },
      ]} /> : <div className="muted">尚未配置路由，模板无法发送。</div>}
    </section>
    <ContentTabs detail={detail} canManage={canManage} onSaved={refresh} onAuthorizationChanged={onAuthorizationChanged} />
    {status && <StatusChangeModal title={status.kind === 'template' ? `${status.target === 'ACTIVE' ? '启用' : '停用'}模板 ${templateCode}？` : `${status.target === 'ACTIVE' ? '启用' : '停用'}路由？`}
      path={`${notificationAdminPath}/templates/${templateCode}${status.kind === 'route' ? '/route' : ''}/status`} code={templateCode} target={status.target}
      version={status.kind === 'template' ? detail.version : detail.route!.version}
      impact={status.target === 'DISABLED' && detail.transientTemplate ? `这是认证邮件模板。停用后用户申请${purpose}时收不到邮件，接口仍返回受理；已发出的链接不受影响，重新启用后立即恢复。` : undefined}
      onClose={() => setStatus(null)} onDone={() => { setStatus(null); refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
    {editingRoute && <RouteModal detail={detail} onClose={() => setEditingRoute(false)} onSaved={() => { setEditingRoute(false); refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 修改或配置路由：主备账号从启用的邮件账号中选择。 */
function RouteModal({ detail, onClose, onSaved, onAuthorizationChanged }: { detail: TemplateDetail; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm<{ primaryProviderCode: string; fallbackProviderCode?: string; reason: string }>();
  const command = useNotificationCommand(onAuthorizationChanged, form); const notify = useErrorNotification();
  const [providers, setProviders] = useState<ProviderSummary[]>([]);
  useEffect(() => {
    const abort = new AbortController();
    session.request(`${notificationAdminPath}/providers?page=1&size=100`, { signal: abort.signal }, pageOf(decodeProvider))
      .then(value => { if (!abort.signal.aborted) setProviders(value.items); })
      .catch(error => { if (!abort.signal.aborted) notify(error, '发送账号加载失败'); });
    return () => abort.abort();
  }, [notify]);
  const options = providers.filter(p => p.status === 'ACTIVE' && p.medium === 'EMAIL').map(p => ({ value: p.providerCode, label: p.providerCode }));
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(`${notificationAdminPath}/templates/${detail.templateCode}/route`, 'PUT', {
      primaryProviderCode: values.primaryProviderCode, fallbackProviderCode: values.fallbackProviderCode ?? null,
      expectedVersion: detail.route?.version ?? 0, reason: values.reason.trim() }, value => decodeMutation(value, detail.templateCode), '保存路由失败');
    if (result) onSaved();
  }
  return <Modal open title={detail.route ? '修改发送路由' : '配置发送路由'} okText="保存路由" cancelText="取消" confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }} destroyOnHidden>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict}
      initialValues={{ primaryProviderCode: detail.route?.primaryProviderCode, fallbackProviderCode: detail.route?.fallbackProviderCode ?? undefined }}>
      <Form.Item name="primaryProviderCode" label="主账号" extra="只能选择「启用」的邮件账号。" rules={[{ required: true, message: '请选择主账号' }]}>
        <Select options={options} placeholder="请选择" /></Form.Item>
      <Form.Item name="fallbackProviderCode" label="备用账号（可选）" dependencies={['primaryProviderCode']}
        extra="不能与主账号相同。普通通知在主账号拒绝或无法确认时改用备用账号；认证邮件只用主账号。"
        rules={[({ getFieldValue }) => ({ validator: async (_, value) => { if (value && value === getFieldValue('primaryProviderCode')) throw new Error('备用账号不能与主账号相同'); } })]}>
        <Select allowClear options={options} placeholder="请选择" /></Form.Item>
      <Form.Item name="reason" label="原因" rules={[{ required: true, whitespace: true, message: '请填写原因' }]}><Input.TextArea rows={2} maxLength={500} /></Form.Item>
      <div className="muted">{detail.route ? `当前版本 ${detail.route.version}，保存时版本不一致将提示冲突。` : '新建的路由为启用状态。'}</div>
    </Form>
    {command.conflict && <Alert type="error" showIcon title="路由已被他人修改，请关闭后刷新再编辑。" />}
  </Modal>;
}

const NEW_LOCALE = '__new__';

/** 语言标签页：切换语言前若有未保存修改需确认。 */
function ContentTabs({ detail, canManage, onSaved, onAuthorizationChanged }: { detail: TemplateDetail; canManage: boolean; onSaved: () => void; onAuthorizationChanged: () => void }) {
  const { modal } = App.useApp();
  const [active, setActive] = useState(detail.contents[0]?.locale ?? NEW_LOCALE); const [dirty, setDirty] = useState(false);
  const tabs = detail.contents.map(c => ({ key: c.locale, label: c.locale }));
  if (canManage) tabs.push({ key: NEW_LOCALE, label: '+ 新增语言' });
  function change(key: string) {
    if (!dirty) { setActive(key); return; }
    modal.confirm({ title: '放弃未保存的修改？', okText: '放弃修改', cancelText: '继续编辑', onOk: () => { setDirty(false); setActive(key); } });
  }
  const content = detail.contents.find(c => c.locale === active) ?? null;
  return <section className="notification-card"><div className="notification-card-head"><h2>语言内容</h2></div>
    {tabs.length === 0 ? <div className="muted">尚无语言内容。</div> : <>
      <Tabs activeKey={active} onChange={change} items={tabs} />
      {(content || active === NEW_LOCALE) && <ContentEditor key={`${active}:${content?.version ?? 0}`} detail={detail} content={content} canManage={canManage}
        onDirty={setDirty} onSaved={locale => { setDirty(false); setActive(locale); onSaved(); }} onAuthorizationChanged={onAuthorizationChanged} />}
    </>}
  </section>;
}

interface ContentValues { locale?: string; subjectTemplate: string; bodyTemplate: string; reason: string }

/** 内容编辑与预览：停顿约 500ms 自动预览；保存前按发送规则由 Platform 校验，冲突时保留草稿。 */
function ContentEditor({ detail, content, canManage, onDirty, onSaved, onAuthorizationChanged }: {
  detail: TemplateDetail; content: TemplateContent | null; canManage: boolean; onDirty: (dirty: boolean) => void;
  onSaved: (locale: string) => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<ContentValues>(); const command = useNotificationCommand(onAuthorizationChanged, form);
  const subject = Form.useWatch('subjectTemplate', form) ?? content?.subjectTemplate ?? '';
  const body = Form.useWatch('bodyTemplate', form) ?? content?.bodyTemplate ?? '';
  const [samples, setSamples] = useState<Record<string, string>>({});
  const names = useMemo(() => placeholders(subject, body), [subject, body]);
  const missing = missingRequired(body, detail.requiredParameters);
  const preview = usePreview(canManage, subject, body, samples);
  async function save(values: ContentValues) {
    const locale = content?.locale ?? values.locale!.trim();
    const result = await command.run(`${notificationAdminPath}/templates/${detail.templateCode}/contents/${encodeURIComponent(locale)}`, 'PUT', {
      subjectTemplate: values.subjectTemplate, bodyTemplate: values.bodyTemplate, expectedVersion: content?.version ?? 0, reason: values.reason.trim(),
    }, value => decodeMutation(value), '保存内容失败');
    if (result) onSaved(result.code);
  }
  return <div className="notification-editor">
    {command.conflict && <Alert type="error" showIcon className="notification-hint" title="该内容已被他人修改"
      description="你的草稿仍保留在编辑框中。重新加载会显示最新内容，可先复制草稿。"
      action={<Space orientation="vertical"><Button size="small" onClick={() => void navigator.clipboard?.writeText(`${subject}\n\n${body}`)}>复制我的草稿</Button>
        <Button size="small" type="primary" onClick={() => onSaved(content?.locale ?? '')}>重新加载</Button></Space>} />}
    <div className="notification-split">
      <Form form={form} layout="vertical" className="notification-form" disabled={!canManage || command.saving} onFinish={save}
        onValuesChange={() => onDirty(true)} initialValues={{ subjectTemplate: content?.subjectTemplate ?? '', bodyTemplate: content?.bodyTemplate ?? '', reason: '' }}>
        {!content && <Form.Item name="locale" label="语言" extra="BCP 47 语言标签，如 zh-CN、en-US" rules={[{ required: true, whitespace: true, message: '请填写语言' },
          { validator: async (_, value) => { if (value && detail.contents.some(c => c.locale.toLowerCase() === String(value).trim().toLowerCase())) throw new Error('该语言已存在'); } }]}>
          <Input maxLength={35} placeholder="例如 en-US" /></Form.Item>}
        <Form.Item name="subjectTemplate" label="标题模板" rules={[{ required: true, whitespace: true, message: '请填写标题' },
          { validator: async (_, value) => { if (/[\r\n]/.test(value ?? '')) throw new Error('标题不能换行'); } }]}>
          <Input maxLength={1024} /></Form.Item>
        <Form.Item name="bodyTemplate" label="正文模板" rules={[{ required: true, whitespace: true, message: '请填写正文' }]}>
          <Input.TextArea rows={9} maxLength={60000} /></Form.Item>
        <div className="notification-slots"><span className="muted">占位符</span>
          {detail.requiredParameters.map(name => <Tag key={name} color={missing.includes(name) ? 'red' : 'geekblue'}>{`\${${name}} 必填${missing.includes(name) ? ' · 缺失' : ''}`}</Tag>)}
          {names.filter(n => !detail.requiredParameters.includes(n)).map(name => <Tag key={name}>{`\${${name}}`}</Tag>)}
          <span className="muted">语法 {'${name}'}；标题不可换行；正文最多 60000 字节</span></div>
        {canManage && <><Form.Item name="reason" label="修改原因" rules={[{ required: true, whitespace: true, message: '请填写原因' }]}>
          <Input maxLength={500} placeholder="必填" /></Form.Item>
          <div className="configuration-actions"><Button disabled={command.saving} onClick={() => { form.resetFields(); onDirty(false); }}>放弃修改</Button>
            <Button type="primary" htmlType="submit" loading={command.saving} disabled={command.conflict || missing.length > 0}>
              保存{content ? ` ${content.locale}` : ''}</Button></div></>}
      </Form>
      {canManage && <aside className="notification-preview"><h3>预览（不发送）</h3>
        {names.map(name => <div key={name} className="notification-sample"><span className="muted">参数 {name}</span>
          <Input size="small" maxLength={2048} value={samples[name] ?? ''} onChange={e => setSamples(s => ({ ...s, [name]: e.target.value }))} /></div>)}
        <PreviewResult preview={preview} />
      </aside>}
    </div>
  </div>;
}

/** 编辑停顿约 500ms 后请求预览；输入为空时不请求。 */
function usePreview(enabled: boolean, subject: string, body: string, samples: Record<string, string>) {
  const [preview, setPreview] = useState<Preview | null>(null);
  const parameters = useMemo(() => Object.fromEntries(Object.entries(samples).filter(([, v]) => v)), [samples]);
  useEffect(() => {
    if (!enabled || !subject || !body) { setPreview(null); return; }
    const abort = new AbortController();
    const timer = window.setTimeout(() => {
      session.request(`${notificationAdminPath}/template-preview`, { method: 'POST', signal: abort.signal,
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({ subjectTemplate: subject, bodyTemplate: body, parameters }) }, decodePreview)
        .then(value => { if (!abort.signal.aborted) setPreview(value); })
        .catch(() => { if (!abort.signal.aborted) setPreview(null); });
    }, 500);
    return () => { window.clearTimeout(timer); abort.abort(); };
  }, [enabled, subject, body, parameters]);
  return preview;
}

/** 渲染后的标题与正文，以及未提供的参数。 */
function PreviewResult({ preview }: { preview: Preview | null }) {
  if (!preview) return <div className="muted">填写标题与正文后显示预览。</div>;
  return <><div className="notification-mail"><strong>{preview.subject}</strong><pre>{preview.body}</pre></div>
    <div className={preview.missingParameters.length ? 'notification-missing' : 'muted'}>
      未提供参数：{preview.missingParameters.length ? preview.missingParameters.join('、') : '无'}</div></>;
}
