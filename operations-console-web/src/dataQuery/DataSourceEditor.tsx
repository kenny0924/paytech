import { useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Form, Input, InputNumber, Modal, Segmented, Select } from 'antd';
import { useT } from '../i18n/react';
import { codePattern, dataQueryPaths, decodeMutation, joinList, localTime } from './dataQueryCommon';
import {
  connectionFingerprint, connectionParamKeys, connectionTestBody, createDataSourceBody, databaseNamePattern, dataSourceFormValues, decodeConnectionTest,
  engineLabels, engineTypes, grantSegments, hostPattern, maxPoolSizeLimit, saveGate, updateDataSourceBody,
} from './dataSourceModel';
import type { ConnectionTest, DataSource, DataSourceFormValues } from './dataSourceModel';
import { ConflictBanner, Note } from './DataQueryParts';
import { useDataQueryCommand } from './useDataQueryCommand';

type StepState = 'passed' | 'failed' | 'skipped';
const stepMark: Record<StepState, string> = { passed: '✓', failed: '✕', skipped: '–' };

/**
 * 连接检查三步：连通、账号只读（SHOW GRANTS 原文，违规权限标红）、会话只读。
 * 接口没有单独的会话只读结论：前两步通过且总结果通过视为通过，前两步通过但总结果失败视为未通过，否则未执行。
 */
export function ConnectionCheck({ result, title }: { result: ConnectionTest; title: string }) {
  const t = useT('data-query');
  const sessionState: StepState = !result.connected || !result.privilegesPassed ? 'skipped' : result.result === 'PASSED' ? 'passed' : 'failed';
  const steps: { name: string; state: StepState; detail: ReactNode }[] = [
    { name: t('check.connect'), state: result.connected ? 'passed' : 'failed', detail: result.connected ? t('check.connected') : result.violations[0] ?? t('check.connectFailed') },
    { name: t('check.privileges'), state: !result.connected ? 'skipped' : result.privilegesPassed ? 'passed' : 'failed', detail: <>
      {result.grants.map((line, index) => <div key={index} className="dq-grant">{grantSegments(line).map((segment, i) =>
        <span key={i} className={segment.violation ? 'is-violation' : undefined}>{segment.text}</span>)}</div>)}
      {!result.privilegesPassed && result.connected && <div className="dq-step-hint">{t('check.privilegesHint')}</div>}
    </> },
    { name: t('check.session'), state: sessionState, detail: sessionState === 'skipped' ? t('check.skipped') : t('check.sessionReadOnly') },
  ];
  const others = result.violations.filter((_, index) => result.connected || index > 0);
  return <section className="dq-check" aria-live="polite">
    <div className="dq-check-title">{title} · {localTime(result.checkedAt)}</div>
    {steps.map(step => <div key={step.name} className={`dq-step is-${step.state}`}>
      <span className="dq-step-mark">{stepMark[step.state]}</span>
      <div><div className="dq-step-name">{step.name}</div><div className="dq-step-detail">{step.detail}</div></div>
    </div>)}
    {others.length > 0 && <ul className="dq-violations">{others.map((item, index) => <li key={index}>{item}</li>)}</ul>}
  </section>;
}

/**
 * 新建 / 编辑数据源（原型 02）：左侧表单，右侧连接检查结果。编码新建后锁定；密码留空保留 Vault 中的原密码；
 * 启用中的数据源修改后必须用当前表单配置检查通过才能保存（服务端保存时仍会重新检查）。
 */
export function DataSourceEditor({ source, onClose, onSaved, onAuthorizationChanged }: {
  source: DataSource | null; onClose: () => void; onSaved: (dataSourceId: string) => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<DataSourceFormValues>(); const t = useT('data-query'), tCommon = useT('common');
  const command = useDataQueryCommand(onAuthorizationChanged, form);
  const tester = useDataQueryCommand(onAuthorizationChanged);
  const [test, setTest] = useState<{ fingerprint: string; result: ConnectionTest } | null>(null);
  const values = Form.useWatch([], form) as DataSourceFormValues | undefined;
  const fingerprint = connectionFingerprint(values ?? dataSourceFormValues(source));
  const gate = saveGate(source, test, fingerprint);
  const stale = test !== null && test.fingerprint !== fingerprint;
  const params = values?.connectionParams ?? [];

  async function runTest() {
    const current = await form.validateFields(['engineType', 'host', 'port', 'databaseName', 'username', 'password', 'connectionParams']).catch(() => null);
    if (!current) return;
    const all = form.getFieldsValue(true) as DataSourceFormValues;
    const result = await tester.run(dataQueryPaths.connectionTests, 'POST', connectionTestBody(all, source), decodeConnectionTest, t('editor.testFailed'));
    if (result) setTest({ fingerprint: connectionFingerprint(all), result });
  }
  async function save() {
    const current = await form.validateFields().catch(() => null);
    if (!current || !gate.allowed) return;
    const all = form.getFieldsValue(true) as DataSourceFormValues;
    const result = source
      ? await command.run(dataQueryPaths.dataSource(source.dataSourceId), 'PUT', updateDataSourceBody(all, source),
        value => decodeMutation(value, 'DATA_SOURCE', source.dataSourceId), t('editor.saveFailed'))
      : await command.run(dataQueryPaths.dataSources, 'POST', createDataSourceBody(all), value => decodeMutation(value, 'DATA_SOURCE'), t('editor.createFailed'));
    if (result) onSaved(result.objectId);
  }

  return <Modal open width={1080} destroyOnHidden wrapClassName="dq-modal" title={source ? t('editor.editTitle', { code: source.dataSourceCode }) : t('editor.createTitle')}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }}
    footer={<div className="dq-modal-footer">
      <span className="muted">{source ? t('editor.versionNote', { from: source.version, to: source.version + 1 }) : t('editor.createNote')}</span>
      <Button onClick={onClose} disabled={command.saving}>{tCommon('action.cancel')}</Button>
      <Button onClick={() => void runTest()} loading={tester.saving}>{test ? t('editor.retest') : t('editor.test')}</Button>
      <Button type="primary" onClick={() => void save()} loading={command.saving} disabled={!gate.allowed || command.conflict}>{tCommon('action.save')}</Button>
    </div>}>
    {source?.status === 'ACTIVE' && <Alert type="warning" showIcon className="dq-banner" title={t('editor.activeWarning')} />}
    {command.conflict && <ConflictBanner subject="dataSource" onReload={onClose} />}
    {command.formError && <Alert type="error" showIcon className="dq-banner" title={command.formError} />}
    <div className="dq-editor-grid">
      <Form form={form} layout="vertical" requiredMark name="data-source-editor" initialValues={dataSourceFormValues(source)} disabled={command.saving}>
        <div className="dq-form-row">
          <Form.Item name="dataSourceCode" label={t('editor.code')} extra={source ? t('editor.codeLocked') : t('editor.codeHint')}
            normalize={(v?: string) => v?.trim().toUpperCase()} rules={source ? [] : [{ required: true, message: t('editor.codeRequired') }, { pattern: codePattern, message: t('editor.codePattern') }]}>
            <Input disabled={!!source} maxLength={64} placeholder="PAYMENT_REPLICA" /></Form.Item>
          <Form.Item name="dataSourceName" label={t('editor.name')} rules={[{ required: true, whitespace: true, message: t('editor.nameRequired') }]}>
            <Input maxLength={128} /></Form.Item>
        </div>
        <Form.Item name="engineType" label={t('editor.engine')} rules={[{ required: true }]}>
          <Segmented options={engineTypes.map(value => ({ value, label: engineLabels[value] }))} /></Form.Item>
        <div className="dq-form-row is-host">
          <Form.Item name="host" label={t('editor.host')} rules={[{ required: true, whitespace: true, message: t('editor.hostRequired') }, { pattern: hostPattern, message: t('editor.hostPattern') }]}>
            <Input maxLength={255} placeholder="10.0.12.21" /></Form.Item>
          <Form.Item name="port" label={t('editor.port')} rules={[{ required: true, message: t('editor.portRequired') }]}>
            <InputNumber min={1} max={65535} precision={0} style={{ width: '100%' }} /></Form.Item>
        </div>
        <Form.Item name="databaseName" label={t('editor.databaseName')} extra={t('editor.databaseNameHint')}
          rules={[{ pattern: databaseNamePattern, message: t('editor.databaseNamePattern') }]}><Input maxLength={128} /></Form.Item>
        <div className="dq-form-row">
          <Form.Item name="username" label={t('editor.username')} rules={[{ required: true, whitespace: true, message: t('editor.usernameRequired') }]}><Input maxLength={128} autoComplete="off" /></Form.Item>
          <Form.Item name="password" label={t('editor.password')} extra={source ? t('editor.passwordHintEdit') : t('editor.passwordHint')}
            rules={source ? [] : [{ required: true, message: t('editor.passwordRequired') }]}>
            <Input.Password maxLength={256} autoComplete="new-password" placeholder={source ? t('editor.passwordPlaceholder') : undefined} /></Form.Item>
        </div>
        <Form.Item label={t('editor.connectionParams', { keys: joinList(connectionParamKeys) })} extra={t('editor.connectionParamsHint')}>
          <Form.List name="connectionParams">{(fields, { add, remove }) => <div className="dq-param-list">
            {fields.map(field => <div key={field.key} className="dq-param-row">
              <Form.Item name={[field.name, 'key']} rules={[{ required: true, message: t('editor.paramKeyRequired') }, {
                validator: (_, key: string) => params.filter(row => row?.key === key).length > 1 ? Promise.reject(new Error(t('editor.paramDuplicate'))) : Promise.resolve(),
              }]}><Select placeholder={t('editor.paramKeyPlaceholder')} options={connectionParamKeys.map(value => ({ value, label: value }))} /></Form.Item>
              <span>=</span>
              <Form.Item name={[field.name, 'value']} rules={[{ required: true, whitespace: true, message: t('editor.paramValueRequired') }, { max: 64, message: t('editor.paramValueMax') }]}>
                <Input maxLength={64} /></Form.Item>
              <Button type="text" onClick={() => remove(field.name)} aria-label={t('editor.removeParam')}>✕</Button>
            </div>)}
            <Button type="dashed" size="small" disabled={fields.length >= connectionParamKeys.length} onClick={() => add({})}>{t('editor.addParam')}</Button>
          </div>}</Form.List>
        </Form.Item>
        <Form.Item name="maxPoolSize" label={t('editor.maxPoolSize')} extra={t('editor.maxPoolSizeHint', { max: maxPoolSizeLimit })} rules={[{ required: true, message: t('editor.maxPoolSizeRequired') }]}>
          <InputNumber min={1} max={maxPoolSizeLimit} precision={0} style={{ width: 160 }} /></Form.Item>
        <Form.Item name="description" label={t('editor.description')}><Input.TextArea rows={2} maxLength={512} showCount /></Form.Item>
      </Form>
      <aside className="dq-check-panel">
        <h3>{t('check.title')}</h3>
        {test ? <ConnectionCheck result={test.result} title={stale ? t('check.staleTitle') : t('check.formTitle')} />
          : <Note>{source ? t('check.introEdit') : t('check.intro')}</Note>}
        {tester.formError && <Alert type="error" showIcon className="dq-banner" title={tester.formError} />}
        {gate.reason && <Note tone={test?.result.result === 'FAILED' && !stale ? 'error' : 'warning'}>{gate.reason}</Note>}
      </aside>
    </div>
  </Modal>;
}
