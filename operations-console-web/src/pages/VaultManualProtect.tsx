import { useState } from 'react';
import { Alert, Button, Form, Input, Select } from 'antd';
import type { ConsoleResource } from '../api/types';
import { TokenValue } from '../vault/SensitiveValue';
import { ReasonAndTicketFields } from '../vault/TicketField';
import { useVaultCommand } from '../vault/useVaultCommand';
import { useVaultDataTypes } from '../vault/useVaultDataTypes';
import { dataClassLabels, decodeProtect, manualDataTypes, manualPath, newSubmissionId } from '../vault/vault';
import '../configuration/configuration.css';
import '../vault/vault.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface Values { dataTypeCode: string; value: string; reasonCode: string; ticketRef: string }

/**
 * 手动加密：只列出开启人工加密、且当前权限允许级别的数据类型。
 * 提交后清空原值，Token 显示在表单下方，只在本页内存中，离开页面即丢弃。
 * 网络重试沿用同一 submissionId 返回同一 Token；成功后换新 submissionId，下一次加密是新的提交。
 */
export function VaultManualProtect({ resources, onAuthorizationChanged }: Props) {
  const [form] = Form.useForm<Values>(); const command = useVaultCommand(onAuthorizationChanged, form);
  const canSecret = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === 'OPERATIONS_VAULT_SECRET_PROTECT');
  const types = useVaultDataTypes(); const options = manualDataTypes(types.data.items, 'protect', canSecret);
  const [submissionId, setSubmissionId] = useState(newSubmissionId); const [token, setToken] = useState<string | null>(null);
  async function submit(values: Values) {
    const result = await command.run(manualPath('protect', canSecret), 'POST', { submissionId, ...values }, decodeProtect, '加密失败');
    if (!result) return;
    form.setFieldValue('value', ''); setToken(result.token); setSubmissionId(newSubmissionId());
  }
  return <div className="configuration-page vault-page"><div className="page-heading"><h1>手动加密</h1></div>
    <Alert type="info" showIcon title="加密结果的 Token 只显示一次，请复制后写入对应业务配置；本操作会记录操作人、理由与工单号。" />
    <section className="configuration-editor">
      <Form form={form} layout="vertical" onFinish={submit} disabled={command.saving}>
        <Form.Item name="dataTypeCode" label="数据类型" rules={[{ required: true, message: '请选择数据类型' }]}
          extra={options.length === 0 && !types.loading ? '没有可人工加密的数据类型' : undefined}>
          <Select showSearch loading={types.loading} options={options.map(t => ({ value: t.dataTypeCode, label: `${t.dataTypeCode}（${dataClassLabels[t.dataClass]}）` }))} />
        </Form.Item>
        <Form.Item name="value" label="原值" rules={[{ required: true, message: '请填写待加密的值' }]}>
          <Input.TextArea rows={4} autoComplete="off" spellCheck={false} className="vault-sensitive-input" />
        </Form.Item>
        <ReasonAndTicketFields />
        <div className="configuration-actions"><Button type="primary" htmlType="submit" loading={command.saving}>加密</Button></div>
        {token && <Form.Item label="加密结果（Token）" extra="只显示这一次，请复制后写入对应业务配置" className="vault-result-item"><TokenValue token={token} /></Form.Item>}
      </Form>
    </section>
  </div>;
}
