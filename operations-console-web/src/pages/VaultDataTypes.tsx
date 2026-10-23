import { useMemo, useState } from 'react';
import { Alert, App, Button, Drawer, Form, Input, Select, Space, Switch, Table, Tag, Typography } from 'antd';
import type { FormInstance } from 'antd';
import type { ConsoleResource } from '../api/types';
import { enumLabel, formatDateTime } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import { ReasonField } from '../vault/ReasonField';
import { useVaultCommand } from '../vault/useVaultCommand';
import { useVaultDataTypes } from '../vault/useVaultDataTypes';
import {
  codePattern, dataClasses, decodeDataTypeMutation, sampleValuesRequired, tokenModes, valueFormatBody, valueFormatOptions, vaultAdminPath,
} from '../vault/vault';
import type { VaultDataType, VaultValueFormat } from '../vault/vault';
import '../configuration/configuration.css';
import '../vault/vault.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const yesNo = (t: Translate, value: boolean) => value ? <Tag color="blue">{t('dataTypes.allowed')}</Tag> : <Tag>{t('dataTypes.notAllowed')}</Tag>;
/** 值格式：REGEX 下一行等宽显示正则，超长省略、悬停看全文。 */
const valueFormat = (t: Translate, row: VaultDataType) => row.valueFormat !== 'REGEX' ? enumLabel(t, 'enum.valueFormat', row.valueFormat)
  : <div><Tag color="purple">{enumLabel(t, 'enum.valueFormat', 'REGEX')}</Tag><Typography.Text className="vault-pattern" ellipsis={{ tooltip: row.valuePattern }}>{row.valuePattern}</Typography.Text></div>;
const maxSampleValues = 5;

/** 数据类型目录：查看全部类型与当前策略版本，有维护权限时新建或编辑。 */
export function VaultDataTypes({ resources, onAuthorizationChanged }: Props) {
  const t = useT('vault'), tCommon = useT('common'), tNav = useT('nav');
  const [reload, setReload] = useState(0); const { data, loading, failed } = useVaultDataTypes(reload);
  const canManage = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === 'OPERATIONS_VAULT_DATA_TYPE_MANAGE');
  const [editing, setEditing] = useState<VaultDataType | 'new' | null>(null);
  return <div className="configuration-page vault-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_VAULT_DATA_TYPES')}</h1>
      <Space><Tag>{t('dataTypes.policyVersion', { version: data.currentPolicyVersion })}</Tag>{canManage && <Button type="primary" onClick={() => setEditing('new')}>{t('dataTypes.create')}</Button>}</Space></div>
    <section className="table-panel"><div className="table-toolbar"><span>{t('dataTypes.count', { count: data.items.length })}</span>
      <Button onClick={() => setReload(v => v + 1)} loading={loading}>{failed ? tCommon('action.retry') : tCommon('action.refresh')}</Button></div>
      <Table<VaultDataType> rowKey="dataTypeCode" tableLayout="fixed" dataSource={data.items} loading={loading} scroll={{ x: 1180 }} pagination={false} columns={[
        { title: t('dataTypes.column.code'), width: 240, render: (_, row) => <div><div>{row.dataTypeCode}</div><div className="cell-meta">{row.description}</div></div> },
        { title: t('dataTypes.column.tokenMode'), width: 120, render: (_, row) => enumLabel(t, 'enum.tokenMode', row.tokenMode) },
        { title: t('dataTypes.column.dataClass'), width: 100, render: (_, row) => <Tag color={row.dataClass === 'SECRET' ? 'red' : 'default'}>{enumLabel(t, 'enum.dataClass', row.dataClass)}</Tag> },
        { title: t('dataTypes.column.valueFormat'), width: 200, render: (_, row) => valueFormat(t, row) },
        { title: t('dataTypes.column.manualProtect'), width: 90, render: (_, row) => yesNo(t, row.manualProtectEnabled) },
        { title: t('dataTypes.column.manualReveal'), width: 90, render: (_, row) => yesNo(t, row.manualRevealEnabled) },
        { title: t('dataTypes.column.status'), width: 80, render: (_, row) => <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{enumLabel(t, 'enum.status', row.status)}</Tag> },
        { title: t('dataTypes.column.updatedAt'), dataIndex: 'updateTime', width: 180, render: (value: string) => formatDateTime(value) },
        ...(canManage ? [{ title: t('dataTypes.column.actions'), width: 90, fixed: 'right' as const, render: (_: unknown, row: VaultDataType) => <Button size="small" onClick={() => setEditing(row)}>{t('dataTypes.edit')}</Button> }] : []),
      ]} /></section>
    {editing && <DataTypeEditor key={editing === 'new' ? 'new' : editing.dataTypeCode} dataType={editing === 'new' ? null : editing}
      onClose={() => setEditing(null)} onSaved={() => { setEditing(null); setReload(v => v + 1); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

interface EditorValues {
  dataTypeCode: string; tokenMode: string; dataClass: string; description: string;
  manualProtectEnabled: boolean; manualRevealEnabled: boolean; active: boolean; reason: string;
  valueFormat: VaultValueFormat; valuePattern?: string; sampleValues?: string[];
}

/** 新建或编辑抽屉：编辑时编码与 Token 模式只读；409 时提示刷新。 */
function DataTypeEditor({ dataType, onClose, onSaved, onAuthorizationChanged }: { dataType: VaultDataType | null; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm<EditorValues>(); const { message } = App.useApp();
  const t = useT('vault'), tCommon = useT('common');
  const tokenModeOptions = useMemo(() => tokenModes.map(value => ({ value, label: enumLabel(t, 'enum.tokenMode', value) })), [t]);
  const dataClassOptions = useMemo(() => dataClasses.map(value => ({ value, label: enumLabel(t, 'enum.dataClass', value) })), [t]);
  const formatOptions = useMemo(() => valueFormatOptions(), [t]);
  const command = useVaultCommand(onAuthorizationChanged, form);
  const format = Form.useWatch('valueFormat', form) ?? dataType?.valueFormat ?? 'TEXT';
  async function save(values: EditorValues) {
    const common = { dataClass: values.dataClass, description: values.description.trim(), manualProtectEnabled: values.manualProtectEnabled,
      manualRevealEnabled: values.manualRevealEnabled, reason: values.reason.trim(), ...valueFormatBody(values.valueFormat, values.valuePattern, values.sampleValues) };
    const result = dataType
      ? await command.run(`${vaultAdminPath}/data-types/${encodeURIComponent(dataType.dataTypeCode)}`, 'PUT',
        { ...common, expectedVersion: dataType.version, status: values.active ? 'ACTIVE' : 'DISABLED' },
        value => decodeDataTypeMutation(value, dataType.dataTypeCode), t('dataTypes.saveFailed'))
      : await command.run(`${vaultAdminPath}/data-types`, 'POST', { ...common, dataTypeCode: values.dataTypeCode, tokenMode: values.tokenMode },
        value => decodeDataTypeMutation(value, values.dataTypeCode), t('dataTypes.saveFailed'));
    if (result) { void message.success(t('policy.saved', { version: result.policyVersion })); onSaved(); }
  }
  return <Drawer title={dataType ? t('dataTypes.editTitle', { code: dataType.dataTypeCode }) : t('dataTypes.create')} open size={520} onClose={() => { if (!command.saving) onClose(); }} maskClosable={!command.saving}>
    <Form form={form} layout="vertical" onFinish={save} disabled={command.saving || command.conflict} initialValues={dataType
      ? { ...dataType, active: dataType.status === 'ACTIVE', reason: '', sampleValues: [''] }
      : { tokenMode: 'REFERENCE', dataClass: 'STANDARD', valueFormat: 'TEXT', sampleValues: [''], manualProtectEnabled: false, manualRevealEnabled: false, active: true }}>
      <Form.Item name="dataTypeCode" label={t('dataTypes.field.code')} rules={[{ required: true, message: t('dataTypes.field.codeRequired') }, { pattern: codePattern, message: t('dataTypes.field.codePattern') }]}>
        <Input disabled={!!dataType} maxLength={64} placeholder={t('dataTypes.field.codePlaceholder')} /></Form.Item>
      <Form.Item name="tokenMode" label={t('dataTypes.field.tokenMode')} extra={t('dataTypes.field.tokenModeHint')}>
        <Select disabled={!!dataType} options={tokenModeOptions} /></Form.Item>
      <Form.Item name="dataClass" label={t('dataTypes.field.dataClass')} extra={t('dataTypes.field.dataClassHint')}>
        <Select options={dataClassOptions} /></Form.Item>
      <Form.Item name="valueFormat" label={t('dataTypes.field.valueFormat')} extra={t('dataTypes.field.valueFormatHint')}>
        <Select options={formatOptions} optionRender={option => <div><div>{option.data.label}</div><div className="cell-meta">{option.data.description}</div></div>} /></Form.Item>
      {format === 'REGEX' && <ValueFormatPatternFields form={form} dataType={dataType} />}
      <Form.Item name="description" label={t('dataTypes.field.description')} rules={[{ required: true, whitespace: true, message: t('dataTypes.field.descriptionRequired') }]}>
        <Input.TextArea rows={2} maxLength={256} showCount /></Form.Item>
      <Form.Item name="manualProtectEnabled" label={t('dataTypes.field.manualProtectEnabled')} valuePropName="checked"><Switch /></Form.Item>
      <Form.Item name="manualRevealEnabled" label={t('dataTypes.field.manualRevealEnabled')} valuePropName="checked"><Switch /></Form.Item>
      {dataType && <Form.Item name="active" label={t('dataTypes.field.active')} valuePropName="checked" extra={t('dataTypes.field.activeHint')}><Switch /></Form.Item>}
      <ReasonField />
      {command.conflict && <Alert type="warning" showIcon title={t('dataTypes.conflict')} />}
      <div className="configuration-actions"><Button disabled={command.saving} onClick={onClose}>{tCommon('action.cancel')}</Button>
        <Button type="primary" htmlType="submit" loading={command.saving}>{tCommon('action.save')}</Button></div>
    </Form>
  </Drawer>;
}

/** REGEX 的正则与测试样例；样例是否必填按 sampleValuesRequired 判断，空输入框不计入。 */
function ValueFormatPatternFields({ form, dataType }: { form: FormInstance<EditorValues>; dataType: VaultDataType | null }) {
  const pattern = Form.useWatch('valuePattern', form); const t = useT('vault');
  const required = sampleValuesRequired(dataType, 'REGEX', pattern);
  return <>
    <Form.Item name="valuePattern" label={t('dataTypes.field.valuePattern')} extra={t('dataTypes.field.valuePatternHint')}
      rules={[{ required: true, message: t('dataTypes.field.valuePatternRequired') }]}>
      <Input maxLength={256} autoComplete="off" spellCheck={false} className="vault-pattern-input" /></Form.Item>
    <Form.List name="sampleValues" rules={[{ validator: async (_, samples: (string | undefined)[] | undefined) => {
      if (sampleValuesRequired(dataType, 'REGEX', form.getFieldValue('valuePattern')) && !(samples ?? []).some(sample => sample)) throw new Error(t('dataTypes.field.samplesRequired'));
    } }]}>
      {(fields, { add, remove }, { errors }) => <Form.Item label={t('dataTypes.field.samples')} required={required} extra={t('dataTypes.field.samplesHint')}>
        {fields.map(field => <Space.Compact key={field.key} block className="vault-sample-row">
          <Form.Item name={field.name} noStyle><Input maxLength={4096} autoComplete="off" spellCheck={false} placeholder={t('dataTypes.field.samplePlaceholder')} /></Form.Item>
          <Button disabled={fields.length <= 1} onClick={() => remove(field.name)}>{t('dataTypes.field.removeSample')}</Button>
        </Space.Compact>)}
        {fields.length < maxSampleValues && <Button type="dashed" block onClick={() => add('')}>{t('dataTypes.field.addSample')}</Button>}
        <Form.ErrorList errors={errors} />
      </Form.Item>}
    </Form.List>
  </>;
}
