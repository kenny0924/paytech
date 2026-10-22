import { useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Form, Input, InputNumber, Modal, Segmented, Select } from 'antd';
import { codePattern, dataQueryPaths, decodeMutation, localTime } from './dataQueryCommon';
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
  const sessionState: StepState = !result.connected || !result.privilegesPassed ? 'skipped' : result.result === 'PASSED' ? 'passed' : 'failed';
  const steps: { name: string; state: StepState; detail: ReactNode }[] = [
    { name: '连通', state: result.connected ? 'passed' : 'failed', detail: result.connected ? '连接成功' : result.violations[0] ?? '无法连接，请检查主机、端口、账号与密码' },
    { name: '账号只读', state: !result.connected ? 'skipped' : result.privilegesPassed ? 'passed' : 'failed', detail: <>
      {result.grants.map((line, index) => <div key={index} className="dq-grant">{grantSegments(line).map((segment, i) =>
        <span key={i} className={segment.violation ? 'is-violation' : undefined}>{segment.text}</span>)}</div>)}
      {!result.privilegesPassed && result.connected && <div className="dq-step-hint">只允许 USAGE、SELECT、SHOW VIEW；角色授权也不允许。请换用只读账号，或请 DBA 收回上述权限后重新测试。</div>}
    </> },
    { name: '会话只读', state: sessionState, detail: sessionState === 'skipped' ? '前一步未通过，未执行' : '连接设为 READ ONLY，多语句与本地文件读取已关闭' },
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
  const [form] = Form.useForm<DataSourceFormValues>();
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
    const result = await tester.run(dataQueryPaths.connectionTests, 'POST', connectionTestBody(all, source), decodeConnectionTest, '连接测试失败');
    if (result) setTest({ fingerprint: connectionFingerprint(all), result });
  }
  async function save() {
    const current = await form.validateFields().catch(() => null);
    if (!current || !gate.allowed) return;
    const all = form.getFieldsValue(true) as DataSourceFormValues;
    const result = source
      ? await command.run(dataQueryPaths.dataSource(source.dataSourceId), 'PUT', updateDataSourceBody(all, source),
        value => decodeMutation(value, 'DATA_SOURCE', source.dataSourceId), '数据源保存失败')
      : await command.run(dataQueryPaths.dataSources, 'POST', createDataSourceBody(all), value => decodeMutation(value, 'DATA_SOURCE'), '数据源创建失败');
    if (result) onSaved(result.objectId);
  }

  return <Modal open width={1080} destroyOnHidden wrapClassName="dq-modal" title={source ? `编辑数据源 · ${source.dataSourceCode}` : '新建数据源'}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }}
    footer={<div className="dq-modal-footer">
      <span className="muted">{source ? `保存后版本 ${source.version} → ${source.version + 1}，各实例约 5 秒内切换` : '新建后为停用状态，启用前会再次检查'}</span>
      <Button onClick={onClose} disabled={command.saving}>取消</Button>
      <Button onClick={() => void runTest()} loading={tester.saving}>{test ? '重新测试' : '测试连接'}</Button>
      <Button type="primary" onClick={() => void save()} loading={command.saving} disabled={!gate.allowed || command.conflict}>保存</Button>
    </div>}>
    {source?.status === 'ACTIVE' && <Alert type="warning" showIcon className="dq-banner" title="当前为启用状态：修改连接或账号后必须检查通过才能保存" />}
    {command.conflict && <ConflictBanner subject="数据源" onReload={onClose} />}
    {command.formError && <Alert type="error" showIcon className="dq-banner" title={command.formError} />}
    <div className="dq-editor-grid">
      <Form form={form} layout="vertical" requiredMark name="data-source-editor" initialValues={dataSourceFormValues(source)} disabled={command.saving}>
        <div className="dq-form-row">
          <Form.Item name="dataSourceCode" label="编码" extra={source ? '创建后不可修改' : '大写字母开头，大写字母、数字或下划线，2–64 位；创建后不可修改'}
            normalize={(v?: string) => v?.trim().toUpperCase()} rules={source ? [] : [{ required: true, message: '请填写编码' }, { pattern: codePattern, message: '格式不正确，例如 PAYMENT_REPLICA' }]}>
            <Input disabled={!!source} maxLength={64} placeholder="PAYMENT_REPLICA" /></Form.Item>
          <Form.Item name="dataSourceName" label="名称" rules={[{ required: true, whitespace: true, message: '请填写名称' }]}>
            <Input maxLength={128} /></Form.Item>
        </div>
        <Form.Item name="engineType" label="引擎" rules={[{ required: true }]}>
          <Segmented options={engineTypes.map(value => ({ value, label: engineLabels[value] }))} /></Form.Item>
        <div className="dq-form-row is-host">
          <Form.Item name="host" label="主机" rules={[{ required: true, whitespace: true, message: '请填写主机' }, { pattern: hostPattern, message: '只填主机名或 IP，不接受 JDBC URL' }]}>
            <Input maxLength={255} placeholder="10.0.12.21" /></Form.Item>
          <Form.Item name="port" label="端口" rules={[{ required: true, message: '请填写端口' }]}>
            <InputNumber min={1} max={65535} precision={0} style={{ width: '100%' }} /></Form.Item>
        </div>
        <Form.Item name="databaseName" label="默认库（可选）" extra="不填则连接整个实例，模板中写 库.表"
          rules={[{ pattern: databaseNamePattern, message: '只允许字母、数字、_ 与 $' }]}><Input maxLength={128} /></Form.Item>
        <div className="dq-form-row">
          <Form.Item name="username" label="账号" rules={[{ required: true, whitespace: true, message: '请填写账号' }]}><Input maxLength={128} autoComplete="off" /></Form.Item>
          <Form.Item name="password" label="密码" extra={source ? '不修改请留空；保存时写入 Vault，不回显' : '保存时写入 Vault，不回显'}
            rules={source ? [] : [{ required: true, message: '请填写密码' }]}>
            <Input.Password maxLength={256} autoComplete="new-password" placeholder={source ? '不修改请留空' : undefined} /></Form.Item>
        </div>
        <Form.Item label={`连接参数（只允许 ${connectionParamKeys.join('、')}）`} extra="allowMultiQueries、allowLoadLocalInfile 等由系统固定为关闭，不可配置">
          <Form.List name="connectionParams">{(fields, { add, remove }) => <div className="dq-param-list">
            {fields.map(field => <div key={field.key} className="dq-param-row">
              <Form.Item name={[field.name, 'key']} rules={[{ required: true, message: '选择参数' }, {
                validator: (_, key: string) => params.filter(row => row?.key === key).length > 1 ? Promise.reject(new Error('参数重复')) : Promise.resolve(),
              }]}><Select placeholder="参数" options={connectionParamKeys.map(value => ({ value, label: value }))} /></Form.Item>
              <span>=</span>
              <Form.Item name={[field.name, 'value']} rules={[{ required: true, whitespace: true, message: '填写取值' }, { max: 64, message: '最长 64' }]}>
                <Input maxLength={64} /></Form.Item>
              <Button type="text" onClick={() => remove(field.name)} aria-label="删除参数">✕</Button>
            </div>)}
            <Button type="dashed" size="small" disabled={fields.length >= connectionParamKeys.length} onClick={() => add({})}>＋ 添加</Button>
          </div>}</Form.List>
        </Form.Item>
        <Form.Item name="maxPoolSize" label="连接池上限" extra={`1–${maxPoolSizeLimit}，按需建立连接，空闲 10 分钟释放`} rules={[{ required: true, message: '请填写连接池上限' }]}>
          <InputNumber min={1} max={maxPoolSizeLimit} precision={0} style={{ width: 160 }} /></Form.Item>
        <Form.Item name="description" label="说明"><Input.TextArea rows={2} maxLength={512} showCount /></Form.Item>
      </Form>
      <aside className="dq-check-panel">
        <h3>连接检查</h3>
        {test ? <ConnectionCheck result={test.result} title={stale ? '上次检查（表单已修改，请重新测试）' : '用表单中的配置'} />
          : <Note>点击“测试连接”用表单中的配置检查：连通、账号只读（SHOW GRANTS）、会话只读。{source ? '密码留空时使用已保存的密码。' : ''}</Note>}
        {tester.formError && <Alert type="error" showIcon className="dq-banner" title={tester.formError} />}
        {gate.reason && <Note tone={test?.result.result === 'FAILED' && !stale ? 'error' : 'warning'}>{gate.reason}</Note>}
      </aside>
    </div>
  </Modal>;
}
