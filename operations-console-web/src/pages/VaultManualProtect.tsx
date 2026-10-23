import { useState } from 'react';
import { Alert, Button, Form, Input, Select } from 'antd';
import type { ConsoleResource } from '../api/types';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { TokenValue } from '../vault/SensitiveValue';
import { ReasonAndTicketFields } from '../vault/TicketField';
import { useVaultCommand } from '../vault/useVaultCommand';
import { useVaultDataTypes } from '../vault/useVaultDataTypes';
import { decodeProtect, isJsonObjectText, manualDataTypes, manualPath, newSubmissionId, valueFormatOptions } from '../vault/vault';
import type { VaultDataType } from '../vault/vault';
import '../configuration/configuration.css';
import '../vault/vault.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface Values { dataTypeCode: string; value: string; reasonCode: string; ticketRef: string }

/**
 * 手动加密：只列出开启人工加密、且当前权限允许级别的数据类型。
 * 提交后清空原值，Token 显示在表单下方，只在本页内存中，离开页面即丢弃。
 * 网络重试沿用同一 submissionId 返回同一 Token；成功后换新 submissionId，下一次加密是新的提交。
 * 选中类型后在原值下方提示值格式；JSON 类型提交前预检根为对象，REGEX 不在前端预检（浏览器正则不是 RE2）。失败时保留表单内容。
 */
export function VaultManualProtect({ resources, onAuthorizationChanged }: Props) {
  const [form] = Form.useForm<Values>(); const command = useVaultCommand(onAuthorizationChanged, form);
  const t = useT('vault'), tNav = useT('nav');
  const canSecret = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === 'OPERATIONS_VAULT_SECRET_PROTECT');
  const types = useVaultDataTypes(); const options = manualDataTypes(types.data.items, 'protect', canSecret);
  const selectedCode = Form.useWatch('dataTypeCode', form); const selected = options.find(type => type.dataTypeCode === selectedCode);
  const [submissionId, setSubmissionId] = useState(newSubmissionId); const [token, setToken] = useState<string | null>(null);
  async function submit(values: Values) {
    const result = await command.run(manualPath('protect', canSecret), 'POST', { submissionId, ...values }, decodeProtect, t('manualProtect.failed'));
    if (!result) return;
    form.setFieldValue('value', ''); setToken(result.token); setSubmissionId(newSubmissionId());
  }
  return <div className="configuration-page vault-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_VAULT_MANUAL_PROTECT')}</h1></div>
    <Alert type="info" showIcon title={t('manualProtect.notice')} />
    <section className="configuration-editor">
      <Form form={form} layout="vertical" onFinish={submit} disabled={command.saving}>
        <Form.Item name="dataTypeCode" label={t('manualProtect.dataType')} rules={[{ required: true, message: t('manualProtect.dataTypeRequired') }]}
          extra={options.length === 0 && !types.loading ? t('manualProtect.noDataType') : undefined}>
          <Select showSearch loading={types.loading} options={options.map(type => ({ value: type.dataTypeCode,
            label: t('manualProtect.dataTypeOption', { code: type.dataTypeCode, dataClass: enumLabel(t, 'enum.dataClass', type.dataClass) }) }))} />
        </Form.Item>
        <Form.Item name="value" label={t('manualProtect.value')} dependencies={['dataTypeCode']} extra={selected && <ValueFormatHint dataType={selected} />} rules={[{ required: true, message: t('manualProtect.valueRequired') },
          { validator: async (_, value: string | undefined) => { if (selected?.valueFormat === 'JSON' && value && !isJsonObjectText(value)) throw new Error(t('valueFormatError.VALUE_NOT_JSON_OBJECT')); } }]}>
          <Input.TextArea rows={4} autoComplete="off" spellCheck={false} className="vault-sensitive-input" />
        </Form.Item>
        <ReasonAndTicketFields />
        <div className="configuration-actions"><Button type="primary" htmlType="submit" loading={command.saving}>{t('manualProtect.submit')}</Button></div>
        {token && <Form.Item label={t('manualProtect.result')} extra={t('manualProtect.resultHint')} className="vault-result-item"><TokenValue token={token} /></Form.Item>}
      </Form>
    </section>
  </div>;
}

/** 原值的格式要求；正则等宽显示。 */
function ValueFormatHint({ dataType }: { dataType: VaultDataType }) {
  const t = useT('vault');
  if (dataType.valueFormat === 'REGEX') return <>{t('manualProtect.regexHint')}<span className="vault-format-pattern">{dataType.valuePattern}</span></>;
  return <>{valueFormatOptions().find(option => option.value === dataType.valueFormat)?.description}</>;
}
