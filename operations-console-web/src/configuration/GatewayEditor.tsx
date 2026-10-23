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
import { defaultIdempotencyMode, idempotencyOptions, initialGuards, routeWrite } from './gatewayEditorModel';
import { editable } from './configurationModel';
import { enumLabel, formatDateTime } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
const requiredRule = (t: Translate) => [{ required: true, message: t('editor.required') }];
const options = (values: readonly string[] = []) => values.map(value => ({ value, label: value }));
interface Values extends Partial<ManagedRoute> { discoveryServiceName?: string; reason: string }

export function GatewayEditor({ resources, onAuthorizationChanged, capabilities, kind }: GatewayPageProps & { capabilities: GatewayCapabilities; kind: 'target' | 'route' }) {
  const t = useT('gateway'), tCommon = useT('common'); const required = requiredRule(t);
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
    }).catch(error => { if (!controller.signal.aborted) { setFailed(true); notify(error, t('editor.detailLoadFailed')); if (error instanceof ApiError && error.status === 403) onAuthorizationChanged(); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
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
    })().catch(error => { if (!abort.signal.aborted) { setTargetsFailed(true); notify(error, t('editor.targetsLoadFailed')); } });
    return () => abort.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [kind, channel, notify, targetsReload]);
  function locked(field: string) { return readOnly || editor.saving || !creating && !editable(detail?.managementProtection, field); }
  async function switchProfile(nextChannel: string, nextProfile?: string) {
    const selectedChannel = capabilities.channels.find(c => c.channel === nextChannel);
    const selected = selectedChannel?.profiles.find(p => p.securityProfileCode === nextProfile) || selectedChannel?.profiles[0];
    if (!selected) return;
    // 选择器受控回显原值，确认后才清理旧 Channel/Profile 下的草稿字段。
    form.setFieldsValue({ channel, securityProfileCode: profileCode });
    if (editor.dirty) {
      const confirmed = await modal.confirm({ title: t('editor.switchProfile.title'), content: t('editor.switchProfile.content'), okText: t('editor.switchProfile.confirm'), cancelText: t('editor.switchProfile.keep') });
      if (!confirmed) return;
    }
    form.setFieldsValue({ channel: nextChannel, securityProfileCode: selected.securityProfileCode,
      authorizationMode: selected.authorizationModes[0], permissionId: null, requiredScopeCode: null,
      requestCookieMode: selected.requestCookieModes[0], responseCookieMode: selected.responseCookieModes[0], idempotencyMode: defaultIdempotencyMode(selected), guardRules: initialGuards(selected),
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
    if (editor.dirty && !await modal.confirm({ title: t('editor.reloadConfirm.title'), content: t('editor.reloadConfirm.content'), okText: t('editor.reload'), cancelText: t('config.leave.stay') })) return;
    editor.reset(); setReload(v => v + 1);
  }
  if (loading) return <Spin />;
  if (failed || (!creating && !detail)) return <Button onClick={() => setReload(v => v + 1)}>{t('editor.reloadDetail')}</Button>;
  return <div className="configuration-page configuration-wide-editor"><div className="page-heading"><h1>{creating ? t('editor.title.create', { kind }) : readOnly ? t('editor.title.view', { kind }) : t('editor.title.edit', { kind })}</h1></div>
    <section className="configuration-editor"><header className="configuration-editor-heading"><div><h2>{creating ? kind === 'target' ? t('editor.newTarget') : t('editor.newRoute') : code}<Tag color={detail?.status === 'ACTIVE' ? 'green' : 'default'}>{enumLabel(t, 'enum.resourceStatus', detail?.status === 'ACTIVE' ? 'ACTIVE' : 'DISABLED')}</Tag></h2>
      {detail && <Typography.Text className="configuration-code" copyable>{code}</Typography.Text>}</div>
      {readOnly && canManage && !!detail?.managementProtection.editableFields.length && <Button onClick={() => { const next = new URLSearchParams(location.search); next.set('mode', 'edit'); void navigate(`?${next}`); }}>{t('resources.action.edit')}</Button>}</header>
      {detail?.managementProtection.reason && <p className="configuration-protection">{detail.managementProtection.reason}</p>}
      {editor.conflict && <Alert type="warning" showIcon title={t('editor.conflict')} action={<Button onClick={() => void reloadLatest()}>{t('editor.reload')}</Button>} />}
      <Form form={form} layout="vertical" disabled={readOnly || editor.saving} onValuesChange={() => editor.setDirty(true)} onFinish={save}
        initialValues={{ reason: '', idempotencyMode: 'NONE', headers: [], queryParameterNames: [], guardRules: [], bodyLimitBytes: Math.min(65536, capabilities.limits.maxBodyLimitBytes), bodyReceiveTimeoutMillis: capabilities.limits.maxBodyReceiveTimeoutMillis, responseTimeoutMillis: capabilities.limits.maxResponseTimeoutMillis }}>
        <h3 className="configuration-section-heading">{t('resources.editor.basics')}</h3><div className="configuration-grid two">
          <Form.Item name={kind === 'target' ? 'targetServiceCode' : 'routeCode'} label={kind === 'target' ? t('routes.field.targetServiceCode') : t('routes.field.routeCode')} rules={[...required, { pattern: /^[a-z][a-z0-9-]{2,127}$/, message: t('editor.codePattern') }]}><Input disabled={!creating || readOnly || editor.saving} maxLength={128} /></Form.Item>
          <Form.Item name="channel" label={t('routes.field.channel')} rules={required}><Select disabled={!creating || readOnly || editor.saving} options={options(capabilities.channels.map(c => c.channel))} onChange={value => { if (kind === 'route') void switchProfile(value); }} /></Form.Item>
        </div>
        {kind === 'target' ? <Form.Item name="discoveryServiceName" label={t('routes.field.discoveryServiceName')} rules={required}><Input maxLength={255} disabled={locked('discoveryServiceName')} /></Form.Item> : <>
          <div className="configuration-grid two"><Form.Item name="httpMethod" label={t('editor.httpMethod')} rules={required}><Select options={options(capabilities.httpMethods)} disabled={locked('httpMethod')} /></Form.Item>
            <Form.Item name="targetServiceCode" label={t('routes.field.targetService')} rules={required}><Select showSearch optionFilterProp="label" disabled={locked('targetServiceCode')} options={targets.map(target => ({ value: target.targetServiceCode, label: t('editor.targetOption', { code: target.targetServiceCode, status: enumLabel(t, 'enum.resourceStatus', target.status === 'ACTIVE' ? 'ACTIVE' : 'DISABLED') }) }))} /></Form.Item></div>
          {targetsFailed && <Button onClick={() => setTargetsReload(v => v + 1)}>{t('editor.reloadTargets')}</Button>}
          <div className="configuration-grid two"><Form.Item name="externalPathTemplate" label={t('routes.field.entryPath')} rules={required}><Input maxLength={1000} disabled={locked('externalPathTemplate')} /></Form.Item>
            <Form.Item name="downstreamPathTemplate" label={t('editor.downstreamPath')} rules={required}><Input maxLength={1000} disabled={locked('downstreamPathTemplate')} /></Form.Item></div>
          <h3 className="configuration-section-heading">{t('editor.securitySection')}</h3><div className="configuration-grid two">
            <Form.Item name="securityProfileCode" label={t('editor.securityProfile')} rules={required}><Select options={options(capabilities.channels.find(c => c.channel === channel)?.profiles.map(p => p.securityProfileCode))} disabled={locked('securityProfileCode')} onChange={value => void switchProfile(channel!, value)} /></Form.Item>
            <Form.Item name="authorizationMode" label={t('editor.authorizationMode')} rules={required}><Select options={options(profile?.authorizationModes)} disabled={locked('authorizationMode')} onChange={() => form.setFieldsValue({ permissionId: null, requiredScopeCode: null })} /></Form.Item>
            {authorizationMode === 'HUMAN_PERMISSION' && <Form.Item name="permissionId" label={t('editor.permission')} rules={required}><PermissionSelect usage="GATEWAY_ROUTE" disabled={locked('permissionId')} currentLabel={route?.permissionCode || undefined} /></Form.Item>}
            {authorizationMode === 'API_SCOPE' && <Form.Item name="requiredScopeCode" label={t('routes.field.apiScope')} rules={required}><ScopeSelect disabled={locked('requiredScopeCode')} originalValue={route?.requiredScopeCode} /></Form.Item>}
          </div><div className="configuration-grid two"><Form.Item name="requestCookieMode" label={t('editor.requestCookie')} rules={required}><Select options={options(profile?.requestCookieModes)} disabled={locked('requestCookieMode')} /></Form.Item>
            <Form.Item name="responseCookieMode" label={t('editor.responseCookie')} rules={required}><Select options={options(profile?.responseCookieModes)} disabled={locked('responseCookieMode')} /></Form.Item></div>
          <div className="configuration-grid two"><Form.Item name="idempotencyMode" label={t('editor.idempotency')} rules={required}><Select options={idempotencyOptions(profile?.idempotencyModes, route?.idempotencyMode)} disabled={locked('idempotencyMode')} /></Form.Item></div>
          <h3 className="configuration-section-heading">{t('editor.limitsSection')}</h3><div className="configuration-grid three">
            <Form.Item name="bodyLimitBytes" label={t('editor.bodyLimit')} rules={required}><InputNumber min={0} max={capabilities.limits.maxBodyLimitBytes} precision={0} disabled={locked('bodyLimitBytes')} /></Form.Item>
            <Form.Item name="bodyReceiveTimeoutMillis" label={t('editor.receiveTimeout')} rules={required}><InputNumber min={1} max={capabilities.limits.maxBodyReceiveTimeoutMillis} precision={0} disabled={locked('bodyReceiveTimeoutMillis')} /></Form.Item>
            <Form.Item name="responseTimeoutMillis" label={t('editor.responseTimeout')} rules={required}><InputNumber min={1} max={capabilities.limits.maxResponseTimeoutMillis} precision={0} disabled={locked('responseTimeoutMillis')} /></Form.Item></div>
          <h3 className="configuration-section-heading">{t('editor.headerSection')}</h3><Form.List name="headers">{(fields, { add, remove }) => <div className="configuration-field-list">{fields.map(field => <div key={field.key} className="configuration-field-row">
            <Form.Item name={[field.name, 'headerName']} rules={[...required, { validator: (_, value: string) => capabilities.forbiddenHeaderNames.includes(value?.toLowerCase()) ? Promise.reject(new Error(t('editor.headerManaged'))) : Promise.resolve() }]}><Input placeholder={t('editor.headerName')} disabled={locked('headers')} /></Form.Item>
            <Form.Item name={[field.name, 'headerUse']} rules={required}><Select placeholder={t('editor.headerUse')} options={options(capabilities.headerUses)} disabled={locked('headers')} /></Form.Item>
            {!locked('headers') && <Button danger onClick={() => { remove(field.name); editor.setDirty(true); }}>{t('editor.remove')}</Button>}</div>)}
            {!locked('headers') && <Button disabled={fields.length >= capabilities.limits.maxCorsHeaders + capabilities.limits.maxForwardedHeaders} onClick={() => { add({ headerName: '', headerUse: capabilities.headerUses[0] }); editor.setDirty(true); }}>{t('editor.addHeader')}</Button>}</div>}</Form.List>
          <h3 className="configuration-section-heading">{t('editor.querySection')}</h3><Form.Item name="queryParameterNames" rules={[{ type: 'array', max: capabilities.limits.maxQueryParameters }]}><Select mode="tags" open={false} tokenSeparators={[',']} placeholder={t('editor.queryPlaceholder')} disabled={locked('queryParameterNames')} /></Form.Item>
          <h3 className="configuration-section-heading">{t('editor.guardSection')}</h3><GuardFields profile={profile} capabilities={capabilities} disabled={locked('guardRules')} onChanged={() => editor.setDirty(true)} />
        </>}
        {!readOnly && <><h3 className="configuration-section-heading">{t('editor.changeSection')}</h3><Form.Item name="reason" label={tCommon('reason.label')} rules={[{ required: true, whitespace: true, message: tCommon('reason.required') }]}><Input.TextArea rows={3} maxLength={500} showCount /></Form.Item>
          <footer className="configuration-save-bar"><span className="muted">{creating ? t('editor.createdDisabled') : t('config.currentVersion', { version: detail?.version ?? '' })}</span><Button type="primary" htmlType="submit" loading={editor.saving} disabled={editor.conflict || !canManage}>{t('config.save')}</Button></footer></>}
      </Form>
      {readOnly && detail && <div className="configuration-readonly-field">{t('editor.version', { version: detail.version })}<span>{t('editor.lastUpdated', { time: formatDateTime(detail.updateTime) })}</span></div>}
    </section></div>;
}
function GuardFields({ profile, capabilities, disabled, onChanged }: { profile?: GatewayProfileCapability; capabilities: GatewayCapabilities; disabled: boolean; onChanged: () => void }) {
  const t = useT('gateway'); const required = requiredRule(t);
  const form = Form.useFormInstance<Values>(); const limits = capabilities.limits;
  return <Form.List name="guardRules">{(fields, { add, remove }) => <div className="configuration-field-list">{fields.map(field => <div key={field.key} className="configuration-guard-row">
    <div className="configuration-field-row"><Form.Item name={[field.name, 'dimension']} label={t('editor.guard.dimension')} rules={required}><Select options={options(profile?.allowedGuardDimensions)} disabled={disabled} /></Form.Item>
      <Form.Item name={[field.name, 'ruleType']} label={t('editor.guard.ruleType')} rules={required}><Select options={options(['RATE_LIMIT', 'CONCURRENCY'])} disabled={disabled} onChange={value => {
        form.setFieldValue(['guardRules', field.name], { ...form.getFieldValue(['guardRules', field.name]), capacity: value === 'RATE_LIMIT' ? 10 : null, refillTokens: value === 'RATE_LIMIT' ? 10 : null, refillPeriodMillis: value === 'RATE_LIMIT' ? 1000 : null, concurrencyLimit: value === 'CONCURRENCY' ? 5 : null });
      }} /></Form.Item>{!disabled && <Button danger style={{ marginTop: 30 }} onClick={() => { remove(field.name); onChanged(); }}>{t('editor.remove')}</Button>}</div>
    <Form.Item noStyle shouldUpdate={(before, after) => before.guardRules?.[field.name]?.ruleType !== after.guardRules?.[field.name]?.ruleType}>{() => form.getFieldValue(['guardRules', field.name, 'ruleType']) === 'RATE_LIMIT'
      ? <div className="configuration-grid three"><Form.Item name={[field.name, 'capacity']} label={t('editor.guard.capacity')} rules={required}><InputNumber min={1} max={limits.maxRateCapacity} precision={0} disabled={disabled} /></Form.Item>
        <Form.Item name={[field.name, 'refillTokens']} label={t('editor.guard.refillTokens')} rules={required}><InputNumber min={1} max={limits.maxRateRefillTokens} precision={0} disabled={disabled} /></Form.Item>
        <Form.Item name={[field.name, 'refillPeriodMillis']} label={t('editor.guard.refillPeriod')} rules={required}><InputNumber min={1} max={limits.maxRateRefillPeriodMillis} precision={0} disabled={disabled} /></Form.Item></div>
      : <Form.Item name={[field.name, 'concurrencyLimit']} label={t('editor.guard.concurrencyLimit')} rules={required}><InputNumber min={1} max={limits.maxConcurrencyLimit} precision={0} disabled={disabled} /></Form.Item>}</Form.Item>
  </div>)}{!disabled && <Button disabled={fields.length >= limits.maxGuardRules} onClick={() => { add({ ruleType: 'RATE_LIMIT', dimension: profile?.allowedGuardDimensions[0], capacity: 10, refillTokens: 10, refillPeriodMillis: 1000, concurrencyLimit: null }); onChanged(); }}>{t('editor.addGuard')}</Button>}</div>}</Form.List>;
}
