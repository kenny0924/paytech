import { ScopeSelect } from '../clientApi/ScopeSelect';
import { useEffect, useState } from 'react';
import { flushSync } from 'react-dom';
import { Alert, App, Button, Form, Input, InputNumber, Select, Spin, Tag, Typography } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import type { GatewayTargetSummaryResponseBody } from '../api/types';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { validateGatewayRoute, validateGatewayTarget } from '../pages/gatewayConfigurationModel';
import type { GatewayPageProps } from '../pages/GatewayConfiguration';
import type { GatewayCapabilities, GatewayProfileCapability, ManagedRoute, ManagedTarget, PagingResponseBody } from './types';
import { PermissionSelect } from './PermissionSelect';
import { useConfigurationForm } from './useConfigurationForm';
import { initialGuards, routeWrite } from './gatewayEditorModel';
import { editable } from './configurationModel';
const required = [{ required: true, message: '请填写此项' }];
const options = (values: readonly string[] = []) => values.map(value => ({ value, label: value }));
interface Values extends Partial<ManagedRoute> { discoveryServiceName?: string; reason: string }

export function GatewayEditor({ resources, onAuthorizationChanged, capabilities, kind }: GatewayPageProps & { capabilities: GatewayCapabilities; kind: 'target' | 'route' }) {
  const location = useLocation(); const navigate = useNavigate(); const notify = useErrorNotification(); const { modal } = App.useApp();
  const [form] = Form.useForm<Values>(); const editor = useConfigurationForm(onAuthorizationChanged, form);
  const query = new URLSearchParams(location.search); const creating = query.get('mode') === 'create'; const code = query.get('code') || '';
  const canManage = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === 'OPERATIONS_GATEWAY_CONFIGURATION_MANAGE');
  const readOnly = query.get('mode') !== 'edit' && !creating || !canManage;
  const [detail, setDetail] = useState<ManagedRoute | ManagedTarget | null>(null); const [loading, setLoading] = useState(!creating); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  const [targets, setTargets] = useState<GatewayTargetSummaryResponseBody[]>([]);
  const [targetsFailed, setTargetsFailed] = useState(false); const [targetsReload, setTargetsReload] = useState(0);
  const collection = kind === 'target' ? 'target-services' : 'routes'; const path = `/gateway-admin/v1/${collection}`;
  const channel = Form.useWatch('channel', form) as string | undefined;
  const profileCode = Form.useWatch('securityProfileCode', form) as string | undefined;
  const authorizationMode = Form.useWatch('authorizationMode', form) as string | undefined;
  const profile = capabilities.channels.find(c => c.channel === channel)?.profiles.find(p => p.securityProfileCode === profileCode);
  const route = detail && 'routeCode' in detail ? detail : null;
  useEffect(() => {
    if (creating) return;
    const controller = new AbortController(); setLoading(true); setFailed(false);
    session.request<ManagedRoute | ManagedTarget>(`${path}/${encodeURIComponent(code)}`, { signal: controller.signal }).then(result => {
      if (controller.signal.aborted) return;
      if ('routeCode' in result) validateGatewayRoute(result, code); else validateGatewayTarget(result, code);
      setDetail(result); form.setFieldsValue({ ...result, reason: '' });
    }).catch(error => { if (!controller.signal.aborted) { setFailed(true); notify(error, '配置详情加载失败'); if (error instanceof ApiError && error.status === 403) onAuthorizationChanged(); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [code, creating, form, notify, path, reload]);
  useEffect(() => {
    if (kind !== 'route' || !channel) return;
    const abort = new AbortController(); setTargets([]); setTargetsFailed(false);
    void (async () => {
      const rows: GatewayTargetSummaryResponseBody[] = [];
      for (let page = 1; ; page++) {
        const result = await session.request<PagingResponseBody<GatewayTargetSummaryResponseBody>>(`/gateway-admin/v1/target-services?channel=${encodeURIComponent(channel)}&page=${page}&size=100`, { signal: abort.signal });
        rows.push(...result.items); if (rows.length >= result.total || !result.items.length) break;
      }
      if (!abort.signal.aborted) setTargets(rows);
    })().catch(error => { if (!abort.signal.aborted) { setTargetsFailed(true); notify(error, '目标服务加载失败'); } });
    return () => abort.abort();
  }, [kind, channel, notify, targetsReload]);
  function locked(field: string) { return readOnly || editor.saving || !creating && !editable(detail?.managementProtection, field); }
  async function switchProfile(nextChannel: string, nextProfile?: string) {
    const selectedChannel = capabilities.channels.find(c => c.channel === nextChannel);
    const selected = selectedChannel?.profiles.find(p => p.securityProfileCode === nextProfile) || selectedChannel?.profiles[0];
    if (!selected) return;
    // 选择器受控回显原值，确认后才清理旧 Channel/Profile 下的草稿字段。
    form.setFieldsValue({ channel, securityProfileCode: profileCode });
    if (editor.dirty) {
      const confirmed = await modal.confirm({ title: '切换安全配置？', content: '将重设授权、Cookie 和 Guard 配置。切换 Channel 还会清空目标服务。', okText: '切换', cancelText: '保留当前配置' });
      if (!confirmed) return;
    }
    form.setFieldsValue({ channel: nextChannel, securityProfileCode: selected.securityProfileCode,
      authorizationMode: selected.authorizationModes[0], permissionId: null, requiredScopeCode: null,
      requestCookieMode: selected.requestCookieModes[0], responseCookieMode: selected.responseCookieModes[0], guardRules: initialGuards(selected),
      ...(channel !== nextChannel ? { targetServiceCode: undefined } : {}) }); editor.setDirty(true);
  }
  async function save(values: Values) {
    if (readOnly || (!creating && !detail)) return;
    const payload = kind === 'route' ? routeWrite({ ...values }, route) : {
      discoveryServiceName: values.discoveryServiceName, reason: values.reason,
      ...(creating ? { targetServiceCode: values.targetServiceCode, channel: values.channel } : { expectedVersion: detail!.version }),
    };
    const saved = await editor.save(creating ? path : `${path}/${encodeURIComponent(code)}`, creating ? 'POST' : 'PUT', payload);
    if (!saved) return;
    flushSync(() => editor.reset());
    const next = new URLSearchParams(location.search); next.set('code', saved.objectCode); next.set('mode', 'view');
    void navigate(`?${next}`, { replace: true }); onAuthorizationChanged();
  }
  async function reloadLatest() {
    if (editor.dirty && !await modal.confirm({ title: '重新加载当前配置？', content: '这将丢弃当前草稿。', okText: '重新加载', cancelText: '继续编辑' })) return;
    editor.reset(); setReload(v => v + 1);
  }
  if (loading) return <Spin />;
  if (failed || (!creating && !detail)) return <Button onClick={() => setReload(v => v + 1)}>重新加载详情</Button>;
  return <div className="configuration-page configuration-wide-editor"><div className="page-heading"><h1>{creating ? '新增' : readOnly ? '' : '编辑'}{kind === 'target' ? '目标服务' : 'API 路由'}{readOnly ? '详情' : ''}</h1></div>
    <section className="configuration-editor"><header className="configuration-editor-heading"><div><h2>{creating ? kind === 'target' ? '新目标服务' : '新路由' : code}<Tag color={detail?.status === 'ACTIVE' ? 'green' : 'default'}>{detail?.status === 'ACTIVE' ? '启用' : '停用'}</Tag></h2>
      {detail && <Typography.Text className="configuration-code" copyable>{code}</Typography.Text>}</div>
      {readOnly && canManage && !!detail?.managementProtection.editableFields.length && <Button onClick={() => { const next = new URLSearchParams(location.search); next.set('mode', 'edit'); void navigate(`?${next}`); }}>编辑</Button>}</header>
      {detail?.managementProtection.reason && <p className="configuration-protection">{detail.managementProtection.reason}</p>}
      {editor.conflict && <Alert type="warning" showIcon title="配置已变化，当前草稿已保留。请重新加载后编辑。" action={<Button onClick={() => void reloadLatest()}>重新加载</Button>} />}
      <Form form={form} layout="vertical" disabled={readOnly || editor.saving} onValuesChange={() => editor.setDirty(true)} onFinish={save}
        initialValues={{ reason: '', headers: [], queryParameterNames: [], guardRules: [], bodyLimitBytes: Math.min(65536, capabilities.limits.maxBodyLimitBytes), bodyReceiveTimeoutMillis: capabilities.limits.maxBodyReceiveTimeoutMillis, responseTimeoutMillis: capabilities.limits.maxResponseTimeoutMillis }}>
        <h3 className="configuration-section-heading">基本信息</h3><div className="configuration-grid two">
          <Form.Item name={kind === 'target' ? 'targetServiceCode' : 'routeCode'} label={kind === 'target' ? '目标服务编码' : '路由编码'} rules={[...required, { pattern: /^[a-z][a-z0-9-]{2,127}$/, message: '3–128 位小写字母、数字或连字符，以字母开头' }]}><Input disabled={!creating || readOnly || editor.saving} maxLength={128} /></Form.Item>
          <Form.Item name="channel" label="Channel" rules={required}><Select disabled={!creating || readOnly || editor.saving} options={options(capabilities.channels.map(c => c.channel))} onChange={value => { if (kind === 'route') void switchProfile(value); }} /></Form.Item>
        </div>
        {kind === 'target' ? <Form.Item name="discoveryServiceName" label="服务发现名" rules={required}><Input maxLength={255} disabled={locked('discoveryServiceName')} /></Form.Item> : <>
          <div className="configuration-grid two"><Form.Item name="httpMethod" label="HTTP Method" rules={required}><Select options={options(capabilities.httpMethods)} disabled={locked('httpMethod')} /></Form.Item>
            <Form.Item name="targetServiceCode" label="目标服务" rules={required}><Select showSearch optionFilterProp="label" disabled={locked('targetServiceCode')} options={targets.map(t => ({ value: t.targetServiceCode, label: `${t.targetServiceCode} · ${t.status === 'ACTIVE' ? '启用' : '停用'}` }))} /></Form.Item></div>
          {targetsFailed && <Button onClick={() => setTargetsReload(v => v + 1)}>重新加载目标服务</Button>}
          <div className="configuration-grid two"><Form.Item name="externalPathTemplate" label="入口路径" rules={required}><Input maxLength={1000} disabled={locked('externalPathTemplate')} /></Form.Item>
            <Form.Item name="downstreamPathTemplate" label="下游路径" rules={required}><Input maxLength={1000} disabled={locked('downstreamPathTemplate')} /></Form.Item></div>
          <h3 className="configuration-section-heading">安全与授权</h3><div className="configuration-grid two">
            <Form.Item name="securityProfileCode" label="安全策略" rules={required}><Select options={options(capabilities.channels.find(c => c.channel === channel)?.profiles.map(p => p.securityProfileCode))} disabled={locked('securityProfileCode')} onChange={value => void switchProfile(channel!, value)} /></Form.Item>
            <Form.Item name="authorizationMode" label="授权方式" rules={required}><Select options={options(profile?.authorizationModes)} disabled={locked('authorizationMode')} onChange={() => form.setFieldsValue({ permissionId: null, requiredScopeCode: null })} /></Form.Item>
            {authorizationMode === 'HUMAN_PERMISSION' && <Form.Item name="permissionId" label="权限" rules={required}><PermissionSelect usage="GATEWAY_ROUTE" disabled={locked('permissionId')} currentLabel={route?.permissionCode || undefined} /></Form.Item>}
            {authorizationMode === 'API_SCOPE' && <Form.Item name="requiredScopeCode" label="API Scope" rules={required}><ScopeSelect disabled={locked('requiredScopeCode')} originalValue={route?.requiredScopeCode} /></Form.Item>}
          </div><div className="configuration-grid two"><Form.Item name="requestCookieMode" label="请求 Cookie" rules={required}><Select options={options(profile?.requestCookieModes)} disabled={locked('requestCookieMode')} /></Form.Item>
            <Form.Item name="responseCookieMode" label="响应 Cookie" rules={required}><Select options={options(profile?.responseCookieModes)} disabled={locked('responseCookieMode')} /></Form.Item></div>
          <h3 className="configuration-section-heading">请求限制与超时</h3><div className="configuration-grid three">
            <Form.Item name="bodyLimitBytes" label="Body 上限（字节）" rules={required}><InputNumber min={0} max={capabilities.limits.maxBodyLimitBytes} precision={0} disabled={locked('bodyLimitBytes')} /></Form.Item>
            <Form.Item name="bodyReceiveTimeoutMillis" label="接收超时（毫秒）" rules={required}><InputNumber min={1} max={capabilities.limits.maxBodyReceiveTimeoutMillis} precision={0} disabled={locked('bodyReceiveTimeoutMillis')} /></Form.Item>
            <Form.Item name="responseTimeoutMillis" label="响应超时（毫秒）" rules={required}><InputNumber min={1} max={capabilities.limits.maxResponseTimeoutMillis} precision={0} disabled={locked('responseTimeoutMillis')} /></Form.Item></div>
          <h3 className="configuration-section-heading">Header</h3><Form.List name="headers">{(fields, { add, remove }) => <div className="configuration-field-list">{fields.map(field => <div key={field.key} className="configuration-field-row">
            <Form.Item name={[field.name, 'headerName']} rules={[...required, { validator: (_, value: string) => capabilities.forbiddenHeaderNames.includes(value?.toLowerCase()) ? Promise.reject(new Error('该 Header 由网关管理')) : Promise.resolve() }]}><Input placeholder="Header 名称" disabled={locked('headers')} /></Form.Item>
            <Form.Item name={[field.name, 'headerUse']} rules={required}><Select placeholder="用途" options={options(capabilities.headerUses)} disabled={locked('headers')} /></Form.Item>
            {!locked('headers') && <Button danger onClick={() => { remove(field.name); editor.setDirty(true); }}>移除</Button>}</div>)}
            {!locked('headers') && <Button disabled={fields.length >= capabilities.limits.maxCorsHeaders + capabilities.limits.maxForwardedHeaders} onClick={() => { add({ headerName: '', headerUse: capabilities.headerUses[0] }); editor.setDirty(true); }}>＋ 添加 Header</Button>}</div>}</Form.List>
          <h3 className="configuration-section-heading">Query 参数</h3><Form.Item name="queryParameterNames" rules={[{ type: 'array', max: capabilities.limits.maxQueryParameters }]}><Select mode="tags" open={false} tokenSeparators={[',']} placeholder="输入参数名称后按回车" disabled={locked('queryParameterNames')} /></Form.Item>
          <h3 className="configuration-section-heading">Guard</h3><GuardFields profile={profile} capabilities={capabilities} disabled={locked('guardRules')} onChanged={() => editor.setDirty(true)} />
        </>}
        {!readOnly && <><h3 className="configuration-section-heading">变更说明</h3><Form.Item name="reason" label="操作原因" rules={[{ required: true, whitespace: true, message: '请填写操作原因' }]}><Input.TextArea rows={3} maxLength={500} showCount /></Form.Item>
          <footer className="configuration-save-bar"><span className="muted">{creating ? '新配置保存为停用状态' : `当前版本 ${detail?.version}`}</span><Button type="primary" htmlType="submit" loading={editor.saving} disabled={editor.conflict || !canManage}>保存配置</Button></footer></>}
      </Form>
      {readOnly && detail && <div className="configuration-readonly-field">版本 {detail.version}<span>最近更新 {new Date(detail.updateTime).toLocaleString()}</span></div>}
    </section></div>;
}
function GuardFields({ profile, capabilities, disabled, onChanged }: { profile?: GatewayProfileCapability; capabilities: GatewayCapabilities; disabled: boolean; onChanged: () => void }) {
  const form = Form.useFormInstance<Values>(); const limits = capabilities.limits;
  return <Form.List name="guardRules">{(fields, { add, remove }) => <div className="configuration-field-list">{fields.map(field => <div key={field.key} className="configuration-guard-row">
    <div className="configuration-field-row"><Form.Item name={[field.name, 'dimension']} label="维度" rules={required}><Select options={options(profile?.allowedGuardDimensions)} disabled={disabled} /></Form.Item>
      <Form.Item name={[field.name, 'ruleType']} label="类型" rules={required}><Select options={options(['RATE_LIMIT', 'CONCURRENCY'])} disabled={disabled} onChange={value => {
        form.setFieldValue(['guardRules', field.name], { ...form.getFieldValue(['guardRules', field.name]), capacity: value === 'RATE_LIMIT' ? 10 : null, refillTokens: value === 'RATE_LIMIT' ? 10 : null, refillPeriodMillis: value === 'RATE_LIMIT' ? 1000 : null, concurrencyLimit: value === 'CONCURRENCY' ? 5 : null });
      }} /></Form.Item>{!disabled && <Button danger style={{ marginTop: 30 }} onClick={() => { remove(field.name); onChanged(); }}>移除</Button>}</div>
    <Form.Item noStyle shouldUpdate={(before, after) => before.guardRules?.[field.name]?.ruleType !== after.guardRules?.[field.name]?.ruleType}>{() => form.getFieldValue(['guardRules', field.name, 'ruleType']) === 'RATE_LIMIT'
      ? <div className="configuration-grid three"><Form.Item name={[field.name, 'capacity']} label="容量" rules={required}><InputNumber min={1} max={limits.maxRateCapacity} precision={0} disabled={disabled} /></Form.Item>
        <Form.Item name={[field.name, 'refillTokens']} label="补充 Token" rules={required}><InputNumber min={1} max={limits.maxRateRefillTokens} precision={0} disabled={disabled} /></Form.Item>
        <Form.Item name={[field.name, 'refillPeriodMillis']} label="补充周期（毫秒）" rules={required}><InputNumber min={1} max={limits.maxRateRefillPeriodMillis} precision={0} disabled={disabled} /></Form.Item></div>
      : <Form.Item name={[field.name, 'concurrencyLimit']} label="并发上限" rules={required}><InputNumber min={1} max={limits.maxConcurrencyLimit} precision={0} disabled={disabled} /></Form.Item>}</Form.Item>
  </div>)}{!disabled && <Button disabled={fields.length >= limits.maxGuardRules} onClick={() => { add({ ruleType: 'RATE_LIMIT', dimension: profile?.allowedGuardDimensions[0], capacity: 10, refillTokens: 10, refillPeriodMillis: 1000, concurrencyLimit: null }); onChanged(); }}>＋ 添加 Guard</Button>}</div>}</Form.List>;
}
