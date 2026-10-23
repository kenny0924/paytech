import { useMemo, useState } from 'react';
import { Alert, Button, Form, Input, Modal, Select } from 'antd';
import {
  accessKeyPattern, codePattern, configurationSetPattern, decodeMutation, decodeTestSend, eventQueuePattern, notificationAdminPath,
  providerTypes, regionPattern, secretKeyPattern, testSendHint,
} from './notification';
import type { Mutation, ProviderSummary, TestSendResult } from './notification';
import { useNotificationCommand } from './useNotificationCommand';
import { enumLabel, formatDateTime } from '../i18n/index';
import { useT } from '../i18n/react';

interface FormValues {
  providerCode?: string; providerType?: string; region: string; fromAddress: string; configurationSet?: string; eventQueueUrl?: string; reason: string;
}

/** 新建（provider 为空）或编辑发送账号的区域、发件地址、Configuration Set 与事件队列；编辑被启用路由引用的账号时提示影响。 */
export function ProviderFormModal({ provider, onClose, onDone, onAuthorizationChanged }: {
  provider: ProviderSummary | null; onClose: () => void; onDone: (result: Mutation) => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<FormValues>(); const command = useNotificationCommand(onAuthorizationChanged, form);
  const t = useT('notification'), tCommon = useT('common');
  const typeOptions = useMemo(() => providerTypes.map(value => ({ value, label: enumLabel(t, 'enum.providerType', value) })), [t]);
  const activeReferences = provider?.references.filter(r => r.routeStatus === 'ACTIVE') ?? [];
  async function submit() {
    const values = await form.validateFields();
    const common = { region: values.region.trim(), fromAddress: values.fromAddress.trim(),
      configurationSet: values.configurationSet?.trim() || null, eventQueueUrl: values.eventQueueUrl?.trim() || null,
      reason: values.reason.trim() };
    const result = provider
      ? await command.run(`${notificationAdminPath}/providers/${provider.providerCode}`, 'PUT',
        { ...common, expectedVersion: provider.version }, value => decodeMutation(value, provider.providerCode), t('providerForm.saveFailed'))
      : await command.run(`${notificationAdminPath}/providers`, 'POST',
        { providerCode: values.providerCode!.trim(), providerType: values.providerType, ...common },
        value => decodeMutation(value, values.providerCode!.trim()), t('providerForm.createFailed'));
    if (result) onDone(result);
  }
  return <Modal open title={provider ? t('providerForm.editTitle', { code: provider.providerCode }) : t('providerForm.createTitle')}
    okText={provider ? tCommon('action.save') : t('providerForm.createOk')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving} okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    {provider && activeReferences.length > 0 && <Alert type="warning" showIcon style={{ marginBottom: 16 }} title={<div>
      <div>{t('providerForm.impact')}</div>
      <ul className="notification-impact">{activeReferences.map(r => <li key={`${r.templateCode}:${r.role}`}>
        {t('providers.reference', { templateCode: r.templateCode, role: enumLabel(t, 'enum.routeRole', r.role) })}</li>)}</ul>
    </div>} />}
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict} initialValues={{
      providerCode: provider?.providerCode, providerType: provider?.providerType ?? 'AMAZON_SES', region: provider?.region ?? '',
      fromAddress: provider?.fromAddress ?? '', configurationSet: provider?.configurationSet ?? '',
      eventQueueUrl: provider?.eventQueueUrl ?? '', reason: '' }}>
      <Form.Item name="providerCode" label={t('providerForm.code')} extra={provider ? undefined : t('providerForm.codeHint')}
        rules={[{ required: true, message: t('providerForm.codeRequired') }, { pattern: codePattern, message: t('providerForm.codePattern') }]}>
        <Input maxLength={64} disabled={!!provider} className="mono" /></Form.Item>
      <Form.Item name="providerType" label={t('providerForm.providerType')}>
        <Select disabled={!!provider} options={typeOptions} /></Form.Item>
      <Form.Item name="region" label={t('providerForm.region')} extra={t('providerForm.regionHint')}
        rules={[{ required: true, whitespace: true, message: t('providerForm.regionRequired') }, { pattern: regionPattern, message: t('providerForm.regionPattern') }]}>
        <Input maxLength={32} /></Form.Item>
      <Form.Item name="fromAddress" label={t('providerForm.fromAddress')} extra={t('providerForm.fromAddressHint')}
        rules={[{ required: true, whitespace: true, message: t('providerForm.fromAddressRequired') }, { type: 'email', message: t('providerForm.emailInvalid') }]}>
        <Input maxLength={254} /></Form.Item>
      <Form.Item name="configurationSet" label={t('providerForm.configurationSet')}
        rules={[{ pattern: configurationSetPattern, message: t('providerForm.configurationSetPattern') }]}>
        <Input maxLength={64} placeholder={t('providerForm.optional')} /></Form.Item>
      <Form.Item name="eventQueueUrl" label={t('providerForm.eventQueueUrl')}
        extra={t('providerForm.eventQueueUrlHint')}
        rules={[{ pattern: eventQueuePattern, message: t('providerForm.eventQueueUrlPattern') }]}>
        <Input maxLength={512} placeholder={t('providerForm.optional')} className="mono" /></Form.Item>
      <Form.Item name="reason" label={t('providerForm.reason')} rules={[{ required: true, whitespace: true, message: t('providerForm.reasonRequired') }]}>
        <Input.TextArea rows={2} maxLength={500} showCount /></Form.Item>
    </Form>
    {!provider && <div className="muted">{t('providerForm.createNote')}</div>}
    {command.conflict && <Alert type="error" showIcon title={t('providerForm.conflict')} />}
  </Modal>;
}

interface CredentialValues { accessKeyId: string; secretAccessKey: string; reason: string }

/** 录入或更换密钥：密码框输入，提交后清空；保存后无法查看，只能再次更换。 */
export function CredentialModal({ provider, onClose, onDone, onAuthorizationChanged }: {
  provider: ProviderSummary; onClose: () => void; onDone: (result: Mutation) => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<CredentialValues>(); const command = useNotificationCommand(onAuthorizationChanged, form);
  const t = useT('notification'), tCommon = useT('common');
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(`${notificationAdminPath}/providers/${provider.providerCode}/credential`, 'PUT',
      { accessKeyId: values.accessKeyId.trim(), secretAccessKey: values.secretAccessKey, expectedVersion: provider.version,
        reason: values.reason.trim() }, value => decodeMutation(value, provider.providerCode), t('credential.saveFailed'));
    form.setFieldsValue({ accessKeyId: '', secretAccessKey: '' });
    if (result) onDone(result);
  }
  return <Modal open title={provider.credentialConfigured ? t('credential.replaceTitle', { code: provider.providerCode }) : t('credential.enterTitle', { code: provider.providerCode })}
    okText={t('credential.ok')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving} okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    <div className="notification-note">{t('credential.note')}</div>
    <div className="muted" style={{ marginBottom: 12 }}>{!provider.credentialConfigured ? t('credential.currentNone')
      : provider.credentialUpdateTime ? t('credential.currentReplaced', { time: formatDateTime(provider.credentialUpdateTime) }) : t('credential.currentConfigured')}</div>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict} autoComplete="off">
      <Form.Item name="accessKeyId" label="Access Key ID"
        rules={[{ required: true, message: t('credential.accessKeyRequired') }, { pattern: accessKeyPattern, message: t('credential.accessKeyPattern') }]}>
        <Input.Password maxLength={128} autoComplete="off" className="mono" /></Form.Item>
      <Form.Item name="secretAccessKey" label="Secret Access Key"
        rules={[{ required: true, message: t('credential.secretKeyRequired') }, { pattern: secretKeyPattern, message: t('credential.secretKeyPattern') }]}>
        <Input.Password maxLength={128} autoComplete="new-password" className="mono" /></Form.Item>
      <Form.Item name="reason" label={t('providerForm.reason')} rules={[{ required: true, whitespace: true, message: t('providerForm.reasonRequired') }]}>
        <Input.TextArea rows={2} maxLength={500} showCount /></Form.Item>
    </Form>
    {command.conflict && <Alert type="error" showIcon title={t('providerForm.conflict')} />}
  </Modal>;
}

/** 测试发送结果的提示类型；文字为 `notification.enum.testOutcome.<结果>`。 */
const outcomeTypes: Record<TestSendResult['outcome'], 'success' | 'error' | 'warning'> = { ACCEPTED: 'success', REJECTED: 'error', UNKNOWN: 'warning' };

/** 测试发送：用该账号的区域、发件地址与密钥发一封固定内容的邮件，结果显示在弹窗内，不生成发送记录。 */
export function TestSendModal({ provider, onClose, onAuthorizationChanged }: {
  provider: ProviderSummary; onClose: () => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<{ email: string; reason: string }>(); const command = useNotificationCommand(onAuthorizationChanged, form);
  const [result, setResult] = useState<TestSendResult | null>(null); const t = useT('notification');
  async function submit() {
    const values = await form.validateFields();
    setResult(null);
    const sent = await command.run(`${notificationAdminPath}/providers/${provider.providerCode}/test-send`, 'POST',
      { email: values.email.trim(), reason: values.reason.trim() }, decodeTestSend, t('testSend.failed'));
    if (sent) setResult(sent);
  }
  return <Modal open title={t('testSend.title', { code: provider.providerCode })} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden footer={[
      <Button key="close" disabled={command.saving} onClick={onClose}>{t('testSend.close')}</Button>,
      <Button key="send" type="primary" loading={command.saving} onClick={() => void submit()}>{result ? t('testSend.sendAgain') : t('testSend.send')}</Button>]}>
    <div className="muted" style={{ marginBottom: 12 }}>{t('testSend.note')}</div>
    <Form form={form} layout="vertical" disabled={command.saving}>
      <Form.Item name="email" label={t('testSend.email')} rules={[{ required: true, whitespace: true, message: t('testSend.emailRequired') }, { type: 'email', message: t('providerForm.emailInvalid') }]}>
        <Input maxLength={254} /></Form.Item>
      <Form.Item name="reason" label={t('providerForm.reason')} rules={[{ required: true, whitespace: true, message: t('providerForm.reasonRequired') }]}>
        <Input maxLength={500} /></Form.Item>
    </Form>
    {result && <Alert type={outcomeTypes[result.outcome]} showIcon title={t('testSend.result', { outcome: enumLabel(t, 'enum.testOutcome', result.outcome), resultCode: result.resultCode })}
      description={testSendHint(result)} />}
  </Modal>;
}
