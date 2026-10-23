import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Checkbox, Form, Input, Modal, Radio, Spin } from 'antd';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { ApiError } from '../api/session';
import { useT } from '../i18n/react';
import {
  decodeEndpointCreated, decodeEndpointMutation, decodeReplay, decodeSecretRotation, endpointBody, eventTypes, maxReasonLength, maxUrlLength,
  replayBody, rotateBody, sendTestEvent, statusBody, testResultText, urlErrorMessage, webhookAdminPath,
} from './webhook';
import type { DeliverySummary, Endpoint, EndpointFormValues, TestResult, WebhookClient } from './webhook';
import { useWebhookCommand } from './useWebhookCommand';

interface CommandProps { onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void }
const urlField = (error: unknown) => { const message = urlErrorMessage(error); return message ? { field: 'url', message } : null; };

/** 原因：必填，写入审计。 */
function ReasonItem() {
  const t = useT('webhook');
  return <Form.Item name="reason" label={t('modal.reason')} rules={[{ required: true, whitespace: true, message: t('modal.reasonRequired') }]}>
    <Input maxLength={maxReasonLength} placeholder={t('modal.reasonPlaceholder')} /></Form.Item>;
}

/** 版本冲突横幅：他人已修改，关闭弹窗并重新加载列表。 */
function ConflictBanner({ onReload }: { onReload: () => void }) {
  const t = useT('webhook');
  return <Alert type="error" showIcon className="webhook-conflict" title={t('modal.conflict')}
    action={<Button size="small" onClick={onReload}>{t('modal.reload')}</Button>} />;
}

/** 新建（endpoint 为空）或修改端点；新建成功后在本组件内打开密钥弹窗，明文只保存在本组件状态里。 */
export function EndpointFormModal({ client, endpoint, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  client: WebhookClient; endpoint: Endpoint | null;
}) {
  const [form] = Form.useForm<EndpointFormValues>(); const t = useT('webhook'), tCommon = useT('common');
  const command = useWebhookCommand(onAuthorizationChanged, form, urlField);
  const [created, setCreated] = useState<{ secret: string; url: string } | null>(null);
  async function submit() {
    const values = await form.validateFields();
    if (endpoint) {
      const result = await command.run(`${webhookAdminPath}/endpoints/${endpoint.endpointId}`, 'PUT', endpointBody(values, endpoint.version),
        value => decodeEndpointMutation(value, endpoint.endpointId), t('endpointForm.saveFailed'));
      if (result) onDone();
      return;
    }
    const result = await command.run(`${webhookAdminPath}/clients/${client.clientId}/endpoints`, 'POST', endpointBody(values),
      decodeEndpointCreated, t('endpointForm.createFailed'));
    if (result) setCreated({ secret: result.signingSecret, url: values.url.trim() });
  }
  if (created) return <SecretOnceModal secret={created.secret} rotationNo={1} endpointUrl={created.url}
    onClose={() => { setCreated(null); onDone(); }} />;
  return <Modal open title={endpoint ? t('endpointForm.editTitle', { clientName: client.clientName }) : t('endpointForm.createTitle', { clientName: client.clientName })}
    okText={tCommon('action.save')} cancelText={tCommon('action.cancel')}
    confirmLoading={command.saving} okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden width={560}>
    {command.conflict && <ConflictBanner onReload={onDone} />}
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict}
      initialValues={{ url: endpoint?.url ?? '', eventTypes: endpoint?.eventTypes ?? [], reason: '' }}>
      <Form.Item name="url" label={t('endpointForm.url')} rules={[{ required: true, whitespace: true, message: t('endpointForm.urlRequired') },
        { pattern: /^https?:\/\/\S+$/i, message: t('endpointForm.urlPattern') }]}>
        <Input maxLength={maxUrlLength} placeholder="https://" /></Form.Item>
      <Form.Item name="eventTypes" label={t('endpointForm.eventTypes')} rules={[{ required: true, type: 'array', min: 1, message: t('endpointForm.eventTypesRequired') }]}>
        <Checkbox.Group className="webhook-events" options={eventTypes.map(type => ({ value: type, label: type }))} /></Form.Item>
      <ReasonItem />
    </Form>
    {!endpoint && <div className="webhook-note">{t('endpointForm.secretNote')}</div>}
  </Modal>;
}

/** 签名密钥只显示一次：复制后反馈；勾选"已保存"后才能关闭；关闭即随组件卸载清空。 */
export function SecretOnceModal({ secret, rotationNo, endpointUrl, onClose }: {
  secret: string; rotationNo: number; endpointUrl: string; onClose: () => void;
}) {
  const [copy, setCopy] = useState<'idle' | 'copied' | 'failed'>('idle'); const [saved, setSaved] = useState(false); const t = useT('webhook');
  async function copySecret() {
    try { await navigator.clipboard.writeText(secret); setCopy('copied'); } catch { setCopy('failed'); }
  }
  return <Modal open title={t('secretOnce.title')} closable={false} keyboard={false} mask={{ closable: false }} destroyOnHidden width={560}
    footer={<Button type="primary" disabled={!saved} onClick={onClose}>{t('detail.close')}</Button>}>
    <Alert type="warning" className="webhook-secret-warning" title={t('secretOnce.warning')} />
    <div className="webhook-field-label">{t('secretOnce.label', { rotationNo })}</div>
    <div className="webhook-secret-row">
      <Input readOnly value={secret} className="mono" onFocus={event => event.currentTarget.select()} aria-label={t('secretOnce.ariaLabel')} />
      <Button onClick={() => void copySecret()}>{t('secretOnce.copy')}</Button>
    </div>
    {copy === 'copied' && <div className="webhook-ok">{t('secretOnce.copied')}</div>}
    {copy === 'failed' && <div className="webhook-error">{t('secretOnce.copyFailed')}</div>}
    <div className="webhook-meta">{t('secretOnce.meta', { endpointUrl })}</div>
    <Checkbox checked={saved} onChange={event => setSaved(event.target.checked)}>{t('secretOnce.saved')}</Checkbox>
  </Modal>;
}

/** 轮换签名密钥：默认旧密钥再签名 1 小时；成功后在本组件内打开密钥弹窗。 */
export function RotateSecretModal({ endpoint, onClose, onDone, onAuthorizationChanged }: CommandProps & { endpoint: Endpoint }) {
  const [form] = Form.useForm<{ immediate: boolean; reason: string }>(); const t = useT('webhook'), tCommon = useT('common');
  const command = useWebhookCommand(onAuthorizationChanged, form);
  const [rotated, setRotated] = useState<{ secret: string; rotationNo: number } | null>(null);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(`${webhookAdminPath}/endpoints/${endpoint.endpointId}/signing-secret/rotations`, 'POST',
      rotateBody(values.immediate, values.reason), decodeSecretRotation, t('rotate.failed'));
    if (result) setRotated({ secret: result.signingSecret, rotationNo: result.rotationNo });
  }
  if (rotated) return <SecretOnceModal secret={rotated.secret} rotationNo={rotated.rotationNo} endpointUrl={endpoint.url}
    onClose={() => { setRotated(null); onDone(); }} />;
  return <Modal open title={t('rotate.title')} okText={t('rotate.ok')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden width={560}>
    <div className="webhook-meta">{t('rotate.current', { url: endpoint.url, rotationNo: endpoint.signingSecret.rotationNo })}</div>
    {command.conflict && <ConflictBanner onReload={onDone} />}
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict} initialValues={{ immediate: false, reason: '' }}>
      <Form.Item name="immediate"><Radio.Group className="webhook-choices">
        <Radio value={false}><div className="webhook-choice-title">{t('rotate.grace')}</div>
          <div className="webhook-meta">{t('rotate.graceHint')}</div></Radio>
        <Radio value={true}><div className="webhook-choice-title">{t('rotate.immediate')}</div>
          <div className="webhook-error">{t('rotate.immediateHint')}</div></Radio>
      </Radio.Group></Form.Item>
      <ReasonItem />
    </Form>
  </Modal>;
}

/** 启停端点：停用需二次确认并说明影响，启用只需原因；均带版本号。 */
export function EndpointStatusModal({ endpoint, onClose, onDone, onAuthorizationChanged }: CommandProps & { endpoint: Endpoint }) {
  const [form] = Form.useForm<{ reason: string }>(); const t = useT('webhook'), tCommon = useT('common');
  const command = useWebhookCommand(onAuthorizationChanged, form);
  const disabling = endpoint.status === 'ACTIVE';
  async function submit() {
    const { reason } = await form.validateFields();
    const result = await command.run(`${webhookAdminPath}/endpoints/${endpoint.endpointId}/status`, 'PATCH',
      statusBody(disabling ? 'DISABLED' : 'ACTIVE', endpoint.version, reason),
      value => decodeEndpointMutation(value, endpoint.endpointId), disabling ? t('status.disableFailed') : t('status.enableFailed'));
    if (result) onDone();
  }
  return <Modal open title={disabling ? t('status.disableTitle') : t('status.enableTitle')} okText={disabling ? t('endpoints.action.disable') : t('endpoints.action.enable')} cancelText={tCommon('action.cancel')}
    okButtonProps={{ danger: disabling, disabled: command.conflict }} confirmLoading={command.saving} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden width={560}>
    <div className="webhook-target">{endpoint.url}</div>
    {disabling && <Alert type="error" className="webhook-impact" title={t('status.disableImpact')} />}
    {command.conflict && <ConflictBanner onReload={onDone} />}
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict}>
      <ReasonItem />
    </Form>
  </Modal>;
}

/** 发送测试事件：打开即发送一次 webhook.ping（无请求体），可再次发送；不进入投递记录。 */
export function TestEventModal({ endpoint, onClose, onAuthorizationChanged }: { endpoint: Endpoint; onClose: () => void; onAuthorizationChanged: () => void }) {
  const notify = useErrorNotification(); const t = useT('webhook');
  const [result, setResult] = useState<TestResult | null>(null); const [sending, setSending] = useState(false);
  const pending = useRef<AbortController | null>(null);
  function send() {
    pending.current?.abort();
    const abort = new AbortController(); pending.current = abort;
    setSending(true); setResult(null);
    sendTestEvent(endpoint.endpointId, abort.signal)
      .then(value => { if (!abort.signal.aborted) setResult(value); })
      .catch(error => {
        if (abort.signal.aborted) return;
        if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
        notify(error, t('test.failed'));
      })
      .finally(() => { if (!abort.signal.aborted) setSending(false); });
  }
  // 打开弹窗即发送一次；卸载时取消未完成的请求。
  useEffect(() => { send(); return () => pending.current?.abort(); }, [endpoint.endpointId]);
  return <Modal open title={t('test.title')} onCancel={onClose} destroyOnHidden width={560} footer={<>
    <Button disabled={sending} onClick={send}>{t('test.sendAgain')}</Button><Button type="primary" onClick={onClose}>{t('detail.close')}</Button></>}>
    <div className="webhook-meta">{t('test.sent', { url: endpoint.url })}</div>
    {sending ? <div className="webhook-test-result"><Spin size="small" /> {t('test.waiting')}</div>
      : result && <div className={`webhook-test-result ${result.result === 'SUCCEEDED' ? 'is-ok' : 'is-failed'}`}>{testResultText(result)}</div>}
  </Modal>;
}

/** Replay：对已结束的投递生成新投递，Event ID 与内容不变，使用当前地址与密钥。 */
export function ReplayModal({ delivery, onClose, onReplayed, onAuthorizationChanged }: {
  delivery: DeliverySummary; onClose: () => void; onReplayed: (deliveryId: string) => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<{ reason: string }>(); const t = useT('webhook'), tCommon = useT('common');
  const command = useWebhookCommand(onAuthorizationChanged, form);
  async function submit() {
    const { reason } = await form.validateFields();
    const result = await command.run(`${webhookAdminPath}/deliveries/${delivery.deliveryId}/replays`, 'POST', replayBody(reason),
      decodeReplay, t('replay.failed'));
    if (result) onReplayed(result.deliveryId);
  }
  return <Modal open title={t('replay.title')} okText={t('deliveries.replay')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving}
    onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden width={560}>
    <div className="webhook-meta">{delivery.eventId} · {delivery.eventType}</div>
    <div className="webhook-note">{t('replay.note')}</div>
    <Form form={form} layout="vertical" disabled={command.saving}>
      <ReasonItem />
    </Form>
  </Modal>;
}
