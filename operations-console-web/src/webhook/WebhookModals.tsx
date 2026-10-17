import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Checkbox, Form, Input, Modal, Radio, Spin } from 'antd';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { ApiError } from '../api/session';
import {
  decodeEndpointCreated, decodeEndpointMutation, decodeReplay, decodeSecretRotation, endpointBody, eventTypes, maxReasonLength, maxUrlLength,
  replayBody, rotateBody, sendTestEvent, statusBody, testResultText, urlErrorMessage, webhookAdminPath,
} from './webhook';
import type { DeliverySummary, Endpoint, EndpointFormValues, TestResult, WebhookClient } from './webhook';
import { useWebhookCommand } from './useWebhookCommand';

interface CommandProps { onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void }
const reasonRules = [{ required: true, whitespace: true, message: '请填写原因' }];
const reasonInput = <Input maxLength={maxReasonLength} placeholder="必填，写入审计" />;
const urlField = (error: unknown) => { const message = urlErrorMessage(error); return message ? { field: 'url', message } : null; };

/** 版本冲突横幅：他人已修改，关闭弹窗并重新加载列表。 */
function ConflictBanner({ onReload }: { onReload: () => void }) {
  return <Alert type="error" showIcon className="webhook-conflict" title="该端点已被他人修改，当前内容不是最新。"
    action={<Button size="small" onClick={onReload}>重新加载</Button>} />;
}

/** 新建（endpoint 为空）或修改端点；新建成功后在本组件内打开密钥弹窗，明文只保存在本组件状态里。 */
export function EndpointFormModal({ client, endpoint, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  client: WebhookClient; endpoint: Endpoint | null;
}) {
  const [form] = Form.useForm<EndpointFormValues>();
  const command = useWebhookCommand(onAuthorizationChanged, form, urlField);
  const [created, setCreated] = useState<{ secret: string; url: string } | null>(null);
  async function submit() {
    const values = await form.validateFields();
    if (endpoint) {
      const result = await command.run(`${webhookAdminPath}/endpoints/${endpoint.endpointId}`, 'PUT', endpointBody(values, endpoint.version),
        value => decodeEndpointMutation(value, endpoint.endpointId), '保存端点失败');
      if (result) onDone();
      return;
    }
    const result = await command.run(`${webhookAdminPath}/clients/${client.clientId}/endpoints`, 'POST', endpointBody(values),
      decodeEndpointCreated, '新建端点失败');
    if (result) setCreated({ secret: result.signingSecret, url: values.url.trim() });
  }
  if (created) return <SecretOnceModal secret={created.secret} rotationNo={1} endpointUrl={created.url}
    onClose={() => { setCreated(null); onDone(); }} />;
  return <Modal open title={`${endpoint ? '修改端点' : '新建端点'} · ${client.clientName}`} okText="保存" cancelText="取消"
    confirmLoading={command.saving} okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden width={560}>
    {command.conflict && <ConflictBanner onReload={onDone} />}
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict}
      initialValues={{ url: endpoint?.url ?? '', eventTypes: endpoint?.eventTypes ?? [], reason: '' }}>
      <Form.Item name="url" label="接收地址" rules={[{ required: true, whitespace: true, message: '请填写接收地址' },
        { pattern: /^https?:\/\/\S+$/i, message: '请填写完整的 URL；允许的协议与公网地址由 Platform 校验' }]}>
        <Input maxLength={maxUrlLength} placeholder="https://" /></Form.Item>
      <Form.Item name="eventTypes" label="订阅事件（至少 1 项）" rules={[{ required: true, type: 'array', min: 1, message: '至少选择 1 个事件' }]}>
        <Checkbox.Group className="webhook-events" options={eventTypes.map(type => ({ value: type, label: type }))} /></Form.Item>
      <Form.Item name="reason" label="原因（必填）" rules={reasonRules}>{reasonInput}</Form.Item>
    </Form>
    {!endpoint && <div className="webhook-note">保存后生成签名密钥，只显示一次。</div>}
  </Modal>;
}

/** 签名密钥只显示一次：复制后反馈；勾选"已保存"后才能关闭；关闭即随组件卸载清空。 */
export function SecretOnceModal({ secret, rotationNo, endpointUrl, onClose }: {
  secret: string; rotationNo: number; endpointUrl: string; onClose: () => void;
}) {
  const [copy, setCopy] = useState<'idle' | 'copied' | 'failed'>('idle'); const [saved, setSaved] = useState(false);
  async function copySecret() {
    try { await navigator.clipboard.writeText(secret); setCopy('copied'); } catch { setCopy('failed'); }
  }
  return <Modal open title="签名密钥已生成" closable={false} keyboard={false} mask={{ closable: false }} destroyOnHidden width={560}
    footer={<Button type="primary" disabled={!saved} onClick={onClose}>关闭</Button>}>
    <Alert type="warning" className="webhook-secret-warning" title="这是该端点的签名密钥，只显示这一次。关闭后无法再查看；如遗失，只能轮换生成新密钥。" />
    <div className="webhook-field-label">签名密钥 · 第 {rotationNo} 号</div>
    <div className="webhook-secret-row">
      <Input readOnly value={secret} className="mono" onFocus={event => event.currentTarget.select()} aria-label="签名密钥" />
      <Button onClick={() => void copySecret()}>复制</Button>
    </div>
    {copy === 'copied' && <div className="webhook-ok">已复制</div>}
    {copy === 'failed' && <div className="webhook-error">无法访问剪贴板，请手动选中密钥后复制。</div>}
    <div className="webhook-meta">端点：{endpointUrl} · 签名头 Code-MoR-Signature: t=…,v1=…</div>
    <Checkbox checked={saved} onChange={event => setSaved(event.target.checked)}>我已保存密钥并交给 Client</Checkbox>
  </Modal>;
}

/** 轮换签名密钥：默认旧密钥再签名 1 小时；成功后在本组件内打开密钥弹窗。 */
export function RotateSecretModal({ endpoint, onClose, onDone, onAuthorizationChanged }: CommandProps & { endpoint: Endpoint }) {
  const [form] = Form.useForm<{ immediate: boolean; reason: string }>();
  const command = useWebhookCommand(onAuthorizationChanged, form);
  const [rotated, setRotated] = useState<{ secret: string; rotationNo: number } | null>(null);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(`${webhookAdminPath}/endpoints/${endpoint.endpointId}/signing-secret/rotations`, 'POST',
      rotateBody(values.immediate, values.reason), decodeSecretRotation, '轮换密钥失败');
    if (result) setRotated({ secret: result.signingSecret, rotationNo: result.rotationNo });
  }
  if (rotated) return <SecretOnceModal secret={rotated.secret} rotationNo={rotated.rotationNo} endpointUrl={endpoint.url}
    onClose={() => { setRotated(null); onDone(); }} />;
  return <Modal open title="轮换签名密钥" okText="轮换" cancelText="取消" confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden width={560}>
    <div className="webhook-meta">{endpoint.url} · 当前第 {endpoint.signingSecret.rotationNo} 号</div>
    {command.conflict && <ConflictBanner onReload={onDone} />}
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict} initialValues={{ immediate: false, reason: '' }}>
      <Form.Item name="immediate"><Radio.Group className="webhook-choices">
        <Radio value={false}><div className="webhook-choice-title">旧密钥再签名 1 小时（推荐）</div>
          <div className="webhook-meta">期间每次投递带两个 v1 签名，Client 换好新密钥后不受影响</div></Radio>
        <Radio value={true}><div className="webhook-choice-title">立即作废旧密钥</div>
          <div className="webhook-error">用于密钥泄露；Client 未更新前的投递会验签失败</div></Radio>
      </Radio.Group></Form.Item>
      <Form.Item name="reason" label="原因（必填）" rules={reasonRules}>{reasonInput}</Form.Item>
    </Form>
  </Modal>;
}

/** 启停端点：停用需二次确认并说明影响，启用只需原因；均带版本号。 */
export function EndpointStatusModal({ endpoint, onClose, onDone, onAuthorizationChanged }: CommandProps & { endpoint: Endpoint }) {
  const [form] = Form.useForm<{ reason: string }>();
  const command = useWebhookCommand(onAuthorizationChanged, form);
  const disabling = endpoint.status === 'ACTIVE';
  async function submit() {
    const { reason } = await form.validateFields();
    const result = await command.run(`${webhookAdminPath}/endpoints/${endpoint.endpointId}/status`, 'PATCH',
      statusBody(disabling ? 'DISABLED' : 'ACTIVE', endpoint.version, reason),
      value => decodeEndpointMutation(value, endpoint.endpointId), disabling ? '停用失败' : '启用失败');
    if (result) onDone();
  }
  return <Modal open title={disabling ? '停用端点' : '启用端点'} okText={disabling ? '停用' : '启用'} cancelText="取消"
    okButtonProps={{ danger: disabling, disabled: command.conflict }} confirmLoading={command.saving} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden width={560}>
    <div className="webhook-target">{endpoint.url}</div>
    {disabling && <Alert type="error" className="webhook-impact" title="停用后不再投递新事件；尚未完成的投递在下次执行时取消。重新启用不会补发停用期间的事件。" />}
    {command.conflict && <ConflictBanner onReload={onDone} />}
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict}>
      <Form.Item name="reason" label="原因（必填）" rules={reasonRules}>{reasonInput}</Form.Item>
    </Form>
  </Modal>;
}

/** 发送测试事件：打开即发送一次 webhook.ping（无请求体），可再次发送；不进入投递记录。 */
export function TestEventModal({ endpoint, onClose, onAuthorizationChanged }: { endpoint: Endpoint; onClose: () => void; onAuthorizationChanged: () => void }) {
  const notify = useErrorNotification();
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
        notify(error, '测试事件发送失败');
      })
      .finally(() => { if (!abort.signal.aborted) setSending(false); });
  }
  // 打开弹窗即发送一次；卸载时取消未完成的请求。
  useEffect(() => { send(); return () => pending.current?.abort(); }, [endpoint.endpointId]);
  return <Modal open title="发送测试事件" onCancel={onClose} destroyOnHidden width={560} footer={<>
    <Button disabled={sending} onClick={send}>再次发送</Button><Button type="primary" onClick={onClose}>关闭</Button></>}>
    <div className="webhook-meta">已向 {endpoint.url} 发送 webhook.ping（不进入投递记录）</div>
    {sending ? <div className="webhook-test-result"><Spin size="small" /> 正在等待对方响应…</div>
      : result && <div className={`webhook-test-result ${result.result === 'SUCCEEDED' ? 'is-ok' : 'is-failed'}`}>{testResultText(result)}</div>}
  </Modal>;
}

/** Replay：对已结束的投递生成新投递，Event ID 与内容不变，使用当前地址与密钥。 */
export function ReplayModal({ delivery, onClose, onReplayed, onAuthorizationChanged }: {
  delivery: DeliverySummary; onClose: () => void; onReplayed: (deliveryId: string) => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<{ reason: string }>();
  const command = useWebhookCommand(onAuthorizationChanged, form);
  async function submit() {
    const { reason } = await form.validateFields();
    const result = await command.run(`${webhookAdminPath}/deliveries/${delivery.deliveryId}/replays`, 'POST', replayBody(reason),
      decodeReplay, 'Replay 失败');
    if (result) onReplayed(result.deliveryId);
  }
  return <Modal open title="Replay 投递" okText="Replay" cancelText="取消" confirmLoading={command.saving}
    onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden width={560}>
    <div className="webhook-meta">{delivery.eventId} · {delivery.eventType}</div>
    <div className="webhook-note">将生成一条新的投递，立即发送到端点当前地址，并使用当前签名密钥。Event ID 和内容不变，Client 应按 eventId 去重。</div>
    <Form form={form} layout="vertical" disabled={command.saving}>
      <Form.Item name="reason" label="原因（必填）" rules={reasonRules}>{reasonInput}</Form.Item>
    </Form>
  </Modal>;
}
