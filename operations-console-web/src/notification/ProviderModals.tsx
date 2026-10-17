import { useState } from 'react';
import { Alert, Button, Form, Input, Modal, Select } from 'antd';
import {
  accessKeyPattern, codePattern, configurationSetPattern, decodeMutation, decodeTestSend, eventQueuePattern, notificationAdminPath,
  providerTypeLabels,
  regionPattern, secretKeyPattern, testSendHint,
} from './notification';
import type { Mutation, ProviderSummary, TestSendResult } from './notification';
import { useNotificationCommand } from './useNotificationCommand';

const roleLabels: Record<string, string> = { PRIMARY: '主', FALLBACK: '备' };
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });

interface FormValues {
  providerCode?: string; providerType?: string; region: string; fromAddress: string; configurationSet?: string; eventQueueUrl?: string; reason: string;
}

/** 新建（provider 为空）或编辑发送账号的区域、发件地址、Configuration Set 与事件队列；编辑被启用路由引用的账号时提示影响。 */
export function ProviderFormModal({ provider, onClose, onDone, onAuthorizationChanged }: {
  provider: ProviderSummary | null; onClose: () => void; onDone: (result: Mutation) => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<FormValues>(); const command = useNotificationCommand(onAuthorizationChanged, form);
  const activeReferences = provider?.references.filter(r => r.routeStatus === 'ACTIVE') ?? [];
  async function submit() {
    const values = await form.validateFields();
    const common = { region: values.region.trim(), fromAddress: values.fromAddress.trim(),
      configurationSet: values.configurationSet?.trim() || null, eventQueueUrl: values.eventQueueUrl?.trim() || null,
      reason: values.reason.trim() };
    const result = provider
      ? await command.run(`${notificationAdminPath}/providers/${provider.providerCode}`, 'PUT',
        { ...common, expectedVersion: provider.version }, value => decodeMutation(value, provider.providerCode), '保存账号失败')
      : await command.run(`${notificationAdminPath}/providers`, 'POST',
        { providerCode: values.providerCode!.trim(), providerType: values.providerType, ...common },
        value => decodeMutation(value, values.providerCode!.trim()), '新建账号失败');
    if (result) onDone(result);
  }
  return <Modal open title={provider ? `编辑发送账号 ${provider.providerCode}` : '新建发送账号'} okText={provider ? '保存' : '创建账号'}
    cancelText="取消" confirmLoading={command.saving} okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    {provider && activeReferences.length > 0 && <Alert type="warning" showIcon style={{ marginBottom: 16 }} title={<div>
      <div>该账号被以下启用中的路由引用，保存后立即影响这些模板的发送：</div>
      <ul className="notification-impact">{activeReferences.map(r => <li key={`${r.templateCode}:${r.role}`}>{r.templateCode}（{roleLabels[r.role]}）</li>)}</ul>
    </div>} />}
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict} initialValues={{
      providerCode: provider?.providerCode, providerType: provider?.providerType ?? 'AMAZON_SES', region: provider?.region ?? '',
      fromAddress: provider?.fromAddress ?? '', configurationSet: provider?.configurationSet ?? '',
      eventQueueUrl: provider?.eventQueueUrl ?? '', reason: '' }}>
      <Form.Item name="providerCode" label="账号编码" extra={provider ? undefined : '大写字母开头，可含数字与下划线；创建后不可修改'}
        rules={[{ required: true, message: '请填写账号编码' }, { pattern: codePattern, message: '编码只能由大写字母开头，包含大写字母、数字或下划线' }]}>
        <Input maxLength={64} disabled={!!provider} className="mono" /></Form.Item>
      <Form.Item name="providerType" label="服务商类型">
        <Select disabled={!!provider} options={Object.entries(providerTypeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Form.Item name="region" label="区域" extra="例如 me-central-1"
        rules={[{ required: true, whitespace: true, message: '请填写区域' }, { pattern: regionPattern, message: '区域格式如 me-central-1' }]}>
        <Input maxLength={32} /></Form.Item>
      <Form.Item name="fromAddress" label="发件地址" extra="必须是该区域 SES 已验证的发件身份；可用测试发送验证"
        rules={[{ required: true, whitespace: true, message: '请填写发件地址' }, { type: 'email', message: '请填写纯邮箱地址' }]}>
        <Input maxLength={254} /></Form.Item>
      <Form.Item name="configurationSet" label="Configuration Set（可选）"
        rules={[{ pattern: configurationSetPattern, message: '只能包含字母、数字、下划线或连字符' }]}>
        <Input maxLength={64} placeholder="选填" /></Form.Item>
      <Form.Item name="eventQueueUrl" label="事件队列 URL（可选）"
        extra="SES 送达、退信、投诉事件经 SNS 发到该 SQS 队列，平台用本账号密钥定时拉取（需本环境开启事件拉取，IAM 需该队列的 ReceiveMessage、DeleteMessage）；留空则不接收事件"
        rules={[{ pattern: eventQueuePattern, message: '格式如 https://sqs.ap-southeast-1.amazonaws.com/123456789012/队列名' }]}>
        <Input maxLength={512} placeholder="选填" className="mono" /></Form.Item>
      <Form.Item name="reason" label="原因" rules={[{ required: true, whitespace: true, message: '请填写原因' }]}>
        <Input.TextArea rows={2} maxLength={500} showCount /></Form.Item>
    </Form>
    {!provider && <div className="muted">新建后为「停用」，录入密钥并确认配置完整后才能启用。</div>}
    {command.conflict && <Alert type="error" showIcon title="该账号已被他人修改，请关闭后刷新再操作。" />}
  </Modal>;
}

interface CredentialValues { accessKeyId: string; secretAccessKey: string; reason: string }

/** 录入或更换密钥：密码框输入，提交后清空；保存后无法查看，只能再次更换。 */
export function CredentialModal({ provider, onClose, onDone, onAuthorizationChanged }: {
  provider: ProviderSummary; onClose: () => void; onDone: (result: Mutation) => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<CredentialValues>(); const command = useNotificationCommand(onAuthorizationChanged, form);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(`${notificationAdminPath}/providers/${provider.providerCode}/credential`, 'PUT',
      { accessKeyId: values.accessKeyId.trim(), secretAccessKey: values.secretAccessKey, expectedVersion: provider.version,
        reason: values.reason.trim() }, value => decodeMutation(value, provider.providerCode), '保存密钥失败');
    form.setFieldsValue({ accessKeyId: '', secretAccessKey: '' });
    if (result) onDone(result);
  }
  return <Modal open title={`${provider.credentialConfigured ? '更换' : '录入'}密钥 · ${provider.providerCode}`} okText="保存密钥"
    cancelText="取消" confirmLoading={command.saving} okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    <div className="notification-note">密钥保存在 Vault，保存后任何人都无法查看，只能再次更换。建议保存后先测试发送，确认成功再到 AWS 停用旧密钥。</div>
    <div className="muted" style={{ marginBottom: 12 }}>当前：{provider.credentialConfigured
      ? `已录入${provider.credentialUpdateTime ? ` · 更换于 ${date(provider.credentialUpdateTime)}` : ''}` : '未录入'}</div>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict} autoComplete="off">
      <Form.Item name="accessKeyId" label="Access Key ID"
        rules={[{ required: true, message: '请填写 Access Key ID' }, { pattern: accessKeyPattern, message: 'Access Key ID 格式不正确' }]}>
        <Input.Password maxLength={128} autoComplete="off" className="mono" /></Form.Item>
      <Form.Item name="secretAccessKey" label="Secret Access Key"
        rules={[{ required: true, message: '请填写 Secret Access Key' }, { pattern: secretKeyPattern, message: 'Secret Access Key 格式不正确' }]}>
        <Input.Password maxLength={128} autoComplete="new-password" className="mono" /></Form.Item>
      <Form.Item name="reason" label="原因" rules={[{ required: true, whitespace: true, message: '请填写原因' }]}>
        <Input.TextArea rows={2} maxLength={500} showCount /></Form.Item>
    </Form>
    {command.conflict && <Alert type="error" showIcon title="该账号已被他人修改，请关闭后刷新再操作。" />}
  </Modal>;
}

const outcomeLabels: Record<TestSendResult['outcome'], { type: 'success' | 'error' | 'warning'; text: string }> = {
  ACCEPTED: { type: 'success', text: '已受理' },
  REJECTED: { type: 'error', text: '被拒绝' },
  UNKNOWN: { type: 'warning', text: '结果未知' },
};

/** 测试发送：用该账号的区域、发件地址与密钥发一封固定内容的邮件，结果显示在弹窗内，不生成发送记录。 */
export function TestSendModal({ provider, onClose, onAuthorizationChanged }: {
  provider: ProviderSummary; onClose: () => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<{ email: string; reason: string }>(); const command = useNotificationCommand(onAuthorizationChanged, form);
  const [result, setResult] = useState<TestSendResult | null>(null);
  async function submit() {
    const values = await form.validateFields();
    setResult(null);
    const sent = await command.run(`${notificationAdminPath}/providers/${provider.providerCode}/test-send`, 'POST',
      { email: values.email.trim(), reason: values.reason.trim() }, decodeTestSend, '测试发送失败');
    if (sent) setResult(sent);
  }
  const label = result ? outcomeLabels[result.outcome] : null;
  return <Modal open title={`测试发送 · ${provider.providerCode}`} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden footer={[
      <Button key="close" disabled={command.saving} onClick={onClose}>关闭</Button>,
      <Button key="send" type="primary" loading={command.saving} onClick={() => void submit()}>{result ? '再次发送' : '发送'}</Button>]}>
    <div className="muted" style={{ marginBottom: 12 }}>使用该账号的区域、发件地址与密钥，向下方邮箱发送一封固定内容的测试邮件。不生成发送记录，只记审计。</div>
    <Form form={form} layout="vertical" disabled={command.saving}>
      <Form.Item name="email" label="收件邮箱" rules={[{ required: true, whitespace: true, message: '请填写收件邮箱' }, { type: 'email', message: '请填写纯邮箱地址' }]}>
        <Input maxLength={254} /></Form.Item>
      <Form.Item name="reason" label="原因" rules={[{ required: true, whitespace: true, message: '请填写原因' }]}>
        <Input maxLength={500} /></Form.Item>
    </Form>
    {result && label && <Alert type={label.type} showIcon title={`${label.text} · ${result.resultCode}`} description={testSendHint(result)} />}
  </Modal>;
}
