import { useCallback, useState } from 'react';
import { Alert, Button, Form, Input } from 'antd';
import type { ConsoleResource } from '../api/types';
import { useT } from '../i18n/react';
import { SensitiveValue } from '../vault/SensitiveValue';
import { ReasonAndTicketFields } from '../vault/TicketField';
import { useVaultCommand } from '../vault/useVaultCommand';
import { decodeReveal, manualPath, tokenPattern } from '../vault/vault';
import '../configuration/configuration.css';
import '../vault/vault.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface Values { token: string; reasonCode: string; ticketRef: string }

/**
 * 手动解密：结果默认遮罩，点击显示后 30 秒自动隐藏并清除；切换页面时一并清除。
 * 有密钥类权限时走密钥路由，否则只能解密普通敏感数据。
 */
export function VaultManualReveal({ resources, onAuthorizationChanged }: Props) {
  const [form] = Form.useForm<Values>(); const command = useVaultCommand(onAuthorizationChanged, form);
  const t = useT('vault'), tNav = useT('nav');
  const canSecret = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === 'OPERATIONS_VAULT_SECRET_REVEAL');
  const [result, setResult] = useState<{ dataTypeCode: string; value: string } | null>(null);
  const clear = useCallback(() => setResult(null), []);
  async function submit(values: Values) {
    setResult(null);
    const revealed = await command.run(manualPath('reveal', canSecret), 'POST', { ...values, token: values.token.trim() }, decodeReveal, t('manualReveal.failed'));
    if (revealed) { form.setFieldValue('token', ''); setResult(revealed); }
  }
  return <div className="configuration-page vault-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_VAULT_MANUAL_REVEAL')}</h1></div>
    <Alert type="warning" showIcon title={canSecret ? t('manualReveal.notice') : t('manualReveal.noticeNoSecret')} />
    <section className="configuration-editor">
      <Form form={form} layout="vertical" onFinish={submit} disabled={command.saving}>
        <Form.Item name="token" label="Token" rules={[{ required: true, whitespace: true, message: t('manualReveal.tokenRequired') }, { pattern: tokenPattern, message: t('manualReveal.tokenPattern') }]}>
          <Input maxLength={43} autoComplete="off" spellCheck={false} />
        </Form.Item>
        <ReasonAndTicketFields />
        <div className="configuration-actions"><Button type="primary" htmlType="submit" loading={command.saving}>{t('manualReveal.submit')}</Button></div>
        {result && <Form.Item label={t('manualReveal.result')} extra={t('manualReveal.resultDataType', { code: result.dataTypeCode })} className="vault-result-item">
          <SensitiveValue value={result.value} onClear={clear} />
        </Form.Item>}
      </Form>
    </section>
  </div>;
}
