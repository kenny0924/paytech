import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Table, Tag } from 'antd';
import type { ConsoleResource } from '../api/types';
import { session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { enumLabel, formatDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import { ConfigurationStatusModal } from '../configuration/ConfigurationStatusModal';
import { useConfigurationForm } from '../configuration/useConfigurationForm';
import {
  createCurrencySpecBody, currencyCodePattern, currencySpecPath, currencyStatusChange, currencyStatusColors,
  decodeCurrencySpecList, decodeCurrencySpecMutation, editableCurrencySpec, manageCurrencySpecs, maxMinorUnit,
  roundingModes, updateCurrencySpecBody,
} from '../configuration/currencySpecModel';
import type { CurrencySpec, CurrencySpecFormValues } from '../configuration/currencySpecModel';
import '../configuration/configuration.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/** 币种：Platform 维护的全平台币种规则；有管理按钮时可新建、编辑草稿与启停，否则只读。 */
export function CurrencySpecs({ resources, onAuthorizationChanged }: Props) {
  const t = useT('currency'), tCommon = useT('common'), tNav = useT('nav');
  const notify = useErrorNotification();
  const canManage = resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === manageCurrencySpecs);
  const [items, setItems] = useState<CurrencySpec[]>([]); const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0);
  const [editing, setEditing] = useState<CurrencySpec | 'new' | null>(null); const [changing, setChanging] = useState<CurrencySpec | null>(null);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setFailed(false);
    session.request(currencySpecPath, { signal: controller.signal }, decodeCurrencySpecList)
      .then(value => { if (!controller.signal.aborted) setItems(value); })
      .catch(error => { if (!controller.signal.aborted) { setItems([]); setFailed(true); notify(error, t('specs.loadFailed')); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [reload, notify]);
  const refresh = () => setReload(value => value + 1);
  return <div className="configuration-page">
    <div className="page-header"><h1>{tNav('resource.OPERATIONS_CURRENCY_SPECS')}</h1>{canManage && <Button type="primary" onClick={() => setEditing('new')}>{t('specs.create')}</Button>}</div>
    {failed && <Alert type="warning" showIcon title={t('specs.unavailable')} action={<Button onClick={refresh}>{tCommon('action.retry')}</Button>} />}
    <section className="table-panel" style={{ marginTop: 16 }}><div className="table-toolbar"><span>{t('specs.count', { count: items.length })}</span>
      <Button loading={loading} onClick={refresh}>{tCommon('action.refresh')}</Button></div>
      <Table<CurrencySpec> rowKey="currency" tableLayout="fixed" dataSource={items} loading={loading} pagination={false} scroll={{ x: 820 }} columns={[
        { title: t('specs.column.currency'), dataIndex: 'currency', width: 100 },
        { title: t('specs.column.minorUnit'), dataIndex: 'minorUnit', width: 110 },
        { title: t('specs.column.roundingMode'), width: 190, render: (_, row) => enumLabel(t, 'enum.roundingMode', row.roundingMode) },
        { title: t('specs.column.status'), width: 100, render: (_, row) => <Tag color={currencyStatusColors[row.status]}>{enumLabel(t, 'enum.status', row.status)}</Tag> },
        { title: t('specs.column.updatedAt'), dataIndex: 'updateTime', width: 180, render: (value: string) => formatDateTime(value) },
        ...(canManage ? [{ title: t('specs.column.actions'), width: 140, fixed: 'right' as const, render: (_: unknown, row: CurrencySpec) => <Space>
          {editableCurrencySpec(row) && <Button size="small" onClick={() => setEditing(row)}>{t('specs.edit')}</Button>}
          <Button size="small" danger={row.status === 'ACTIVE'} onClick={() => setChanging(row)}>{currencyStatusChange(row).verb}</Button>
        </Space> }] : []),
      ]} /></section>
    {editing && <CurrencySpecEditor key={editing === 'new' ? 'new' : editing.currency} spec={editing === 'new' ? null : editing}
      onClose={() => setEditing(null)} onSaved={() => { setEditing(null); refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
    {changing && <ConfigurationStatusModal kind="currency" code={changing.currency} version={changing.version} enabled={changing.status === 'ACTIVE'}
      notice={<Alert type={changing.status === 'ACTIVE' ? 'info' : 'warning'} showIcon title={currencyStatusChange(changing).notice} style={{ marginBottom: 16 }} />}
      onClose={() => setChanging(null)} onSaved={() => { setChanging(null); refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 新建或编辑草稿弹窗：编辑时币种代码只读；409 时保留草稿并提示刷新。 */
function CurrencySpecEditor({ spec, onClose, onSaved, onAuthorizationChanged }: {
  spec: CurrencySpec | null; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('currency'), tCommon = useT('common');
  const [form] = Form.useForm<CurrencySpecFormValues>(); const editor = useConfigurationForm(onAuthorizationChanged, form);
  const roundingOptions = useMemo(() => roundingModes.map(value => ({ value, label: enumLabel(t, 'enum.roundingMode', value) })), [t]);
  async function save(values: CurrencySpecFormValues) {
    const result = spec
      ? await editor.save(`${currencySpecPath}/${encodeURIComponent(spec.currency)}`, 'PUT', updateCurrencySpecBody(values, spec),
        value => decodeCurrencySpecMutation(value, spec.currency, 'DRAFT'))
      : await editor.save(currencySpecPath, 'POST', createCurrencySpecBody(values),
        value => decodeCurrencySpecMutation(value, createCurrencySpecBody(values).currency, 'DRAFT'));
    if (result) onSaved();
  }
  return <Modal open title={spec ? t('editor.editTitle', { currency: spec.currency }) : t('editor.createTitle')}
    okText={tCommon('action.save')} cancelText={tCommon('action.cancel')} confirmLoading={editor.saving}
    okButtonProps={{ disabled: editor.conflict }} cancelButtonProps={{ disabled: editor.saving }} closable={!editor.saving} maskClosable={!editor.saving}
    onCancel={onClose} onOk={() => form.submit()}>
    <Form form={form} layout="vertical" requiredMark={false} disabled={editor.saving || editor.conflict} onFinish={save}
      onValuesChange={() => editor.setDirty(true)}
      initialValues={spec ? { currency: spec.currency, minorUnit: spec.minorUnit, roundingMode: spec.roundingMode, reason: '' } : { roundingMode: 'HALF_UP' }}>
      <Form.Item name="currency" label={t('editor.code')} extra={spec ? undefined : t('editor.codeHint')}
        normalize={(value?: string) => value?.toUpperCase()}
        rules={[{ required: true, message: t('editor.codeRequired') }, { pattern: currencyCodePattern, message: t('editor.codePattern') }]}>
        <Input disabled={!!spec} maxLength={3} placeholder={t('editor.codePlaceholder')} /></Form.Item>
      <Form.Item name="minorUnit" label={t('editor.minorUnit')} extra={t('editor.minorUnitHint')}
        rules={[{ required: true, message: t('editor.minorUnitRequired') }, { type: 'integer', min: 0, max: maxMinorUnit, message: t('editor.minorUnitRange', { max: maxMinorUnit }) }]}>
        <InputNumber min={0} max={maxMinorUnit} precision={0} style={{ width: '100%' }} /></Form.Item>
      <Form.Item name="roundingMode" label={t('editor.roundingMode')} extra={t('editor.frozenHint')} rules={[{ required: true, message: t('editor.roundingModeRequired') }]}>
        <Select options={roundingOptions} /></Form.Item>
      <Form.Item name="reason" label={t('editor.reason')} rules={[{ required: true, whitespace: true, message: t('editor.reasonRequired') }]}>
        <Input.TextArea rows={3} maxLength={500} showCount /></Form.Item>
      {!spec && <p className="muted">{t('editor.draftHint')}</p>}
      {editor.conflict && <Alert type="warning" showIcon title={t('editor.conflict')} />}
    </Form>
  </Modal>;
}
