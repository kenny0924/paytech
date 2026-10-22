import { useEffect, useRef, useState } from 'react';
import { Button, Descriptions, Form, Input, Select, Table } from 'antd';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { Loading, QueryFailure } from '../tenantAccess/TenantAccessView';
import {
  activeStatusLabels, activeStatuses, dataQueryButtons, dataQueryPaths, dataQueryRoutes, decodeMutation, hasButton, listFilters, localTime,
} from '../dataQuery/dataQueryCommon';
import {
  addressLabel, decodeConnectionTest, decodeDataSource, decodeDataSourcePage, deletionBlock, engineLabels, engineTypes, lastCheckLabel,
  maxPoolSizeLimit, statusAction,
} from '../dataQuery/dataSourceModel';
import type { ConnectionTest, DataSource } from '../dataQuery/dataSourceModel';
import { decodeTemplatePage } from '../dataQuery/templateModel';
import type { TemplateSummary } from '../dataQuery/templateModel';
import { ConnectionCheck, DataSourceEditor } from '../dataQuery/DataSourceEditor';
import { CommandModal, Note, StatusTag, useResource } from '../dataQuery/DataQueryParts';
import { useDataQueryCommand } from '../dataQuery/useDataQueryCommand';
import '../dataQuery/dataQuery.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface FilterValues { status?: string; engineType?: string; keyword?: string }
type Step = { kind: 'create' } | { kind: 'edit'; source: DataSource } | { kind: 'status'; source: DataSource } | { kind: 'delete'; source: DataSource } | null;

/** 数据源（原型 01、02）：左侧列表，右侧选中项详情；有维护按钮时可新建、编辑、测试连接、启停与删除。 */
export function DataSources({ resources, onAuthorizationChanged }: Props) {
  const [form] = Form.useForm<FilterValues>();
  const page = usePaging<DataSource>(dataQueryPaths.dataSources, decodeDataSourcePage, {}, { notifyOnError: false });
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [step, setStep] = useState<Step>(null);
  const [revision, setRevision] = useState(0);
  const canManage = hasButton(resources, dataQueryButtons.dataSourceManage);
  const items = page.data.items;
  // 新建或修改后要选中的数据源：刷新完成前它可能还不在旧列表中，不能因此回退到第一项。
  const pendingSelection = useRef<string | null>(null);
  useEffect(() => {
    if (!items.length) return;
    if (selectedId && items.some(item => item.dataSourceId === selectedId)) {
      if (pendingSelection.current === selectedId) pendingSelection.current = null;
      return;
    }
    if (selectedId && pendingSelection.current === selectedId) return;
    setSelectedId(items[0].dataSourceId);
  }, [items, selectedId]);
  const reload = (dataSourceId?: string) => {
    setStep(null);
    if (dataSourceId) { pendingSelection.current = dataSourceId; setSelectedId(dataSourceId); }
    page.refresh(); setRevision(v => v + 1);
  };
  return <div className="configuration-page dq-page">
    <div className="page-heading"><h1>数据源</h1>
      {canManage && <Button type="primary" onClick={() => setStep({ kind: 'create' })}>＋ 新建数据源</Button>}</div>
    <p className="muted dq-intro">登记可供查询模板使用的只读数据库连接。密码只存 Vault Token，不回显；启用与修改启用中的数据源前必须通过只读检查。</p>
    <Form form={form} layout="vertical" name="data-source-filter" onFinish={(values: FilterValues) => page.applyFilters(listFilters({ ...values }))}>
      <div className="dq-filters">
        <Form.Item name="status" label="状态"><Select allowClear placeholder="全部"
          options={activeStatuses.map(value => ({ value, label: activeStatusLabels[value] }))} /></Form.Item>
        <Form.Item name="engineType" label="引擎"><Select allowClear placeholder="全部" options={engineTypes.map(value => ({ value, label: engineLabels[value] }))} /></Form.Item>
        <Form.Item name="keyword" label="编码 / 名称"><Input allowClear maxLength={64} placeholder="关键字" /></Form.Item>
        <div className="dq-filter-actions"><Button type="primary" htmlType="submit">查询</Button>
          <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button></div>
      </div>
    </Form>
    <div className="dq-master-detail">
      <section className="dq-panel">
        {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
          : <Table<DataSource> rowKey="dataSourceId" size="middle" loading={page.loading} dataSource={items} pagination={page.pagination}
            tableLayout="fixed" scroll={{ x: 860 }} locale={{ emptyText: '没有符合条件的数据源' }}
            rowClassName={row => `dq-clickable${row.dataSourceId === selectedId ? ' dq-selected-row' : ''}`}
            onRow={row => ({ onClick: () => setSelectedId(row.dataSourceId) })} columns={[
              { title: '编码 / 名称', width: 200, render: (_, row) => <div className="dq-cell"><span className="mono">{row.dataSourceCode}</span><span className="muted">{row.dataSourceName}</span></div> },
              { title: '引擎', width: 80, render: (_, row) => engineLabels[row.engineType] },
              { title: '地址 / 默认库', width: 240, render: (_, row) => <span className="mono">{addressLabel(row)}</span> },
              { title: '状态', width: 80, render: (_, row) => <StatusTag status={row.status} /> },
              { title: '最后检查', width: 170, render: (_, row) => <span className={row.lastCheckResult === 'FAILED' ? 'dq-text-error' : undefined}>{lastCheckLabel(row, localTime)}</span> },
              { title: '引用模板', width: 90, align: 'right', render: (_, row) => row.templateCount },
            ]} />}
      </section>
      <section className="dq-panel dq-detail">
        {selectedId ? <DataSourceDetail key={`${selectedId}:${revision}`} dataSourceId={selectedId} canManage={canManage}
          onAuthorizationChanged={onAuthorizationChanged} onStep={setStep} />
          : <Note>{page.loading ? '加载中…' : '选择左侧数据源查看详情。'}</Note>}
      </section>
    </div>
    {(step?.kind === 'create' || step?.kind === 'edit') && <DataSourceEditor source={step.kind === 'edit' ? step.source : null}
      onClose={() => setStep(null)} onSaved={reload} onAuthorizationChanged={onAuthorizationChanged} />}
    {step?.kind === 'status' && <StatusModal source={step.source} onClose={() => setStep(null)} onDone={() => reload(step.source.dataSourceId)}
      onAuthorizationChanged={onAuthorizationChanged} />}
    {step?.kind === 'delete' && <DeleteModal source={step.source} onClose={() => setStep(null)} onDone={() => { setSelectedId(null); reload(); }}
      onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

const referencePath = (dataSourceId: string, size: number) => `${dataQueryPaths.templates}?dataSourceId=${dataSourceId}&page=1&size=${size}`;

/** 右侧详情：连接、密码只显示“已保存（Vault）”、最后一次检查、引用的模板与动作。 */
function DataSourceDetail({ dataSourceId, canManage, onStep, onAuthorizationChanged }: {
  dataSourceId: string; canManage: boolean; onStep: (step: Step) => void; onAuthorizationChanged: () => void;
}) {
  const detail = useResource(dataQueryPaths.dataSource(dataSourceId), decodeDataSource);
  const references = useResource(referencePath(dataSourceId, 10), decodeTemplatePage, '引用模板加载失败');
  const tester = useDataQueryCommand(onAuthorizationChanged);
  const [test, setTest] = useState<ConnectionTest | null>(null);
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const source = detail.data;
  const action = statusAction(source);
  async function runTest() {
    const result = await tester.run(dataQueryPaths.connectionTests, 'POST', {
      dataSourceId: source.dataSourceId, engineType: source.engineType, host: source.host, port: source.port, databaseName: source.databaseName,
      username: source.username, password: null, connectionParams: source.connectionParams,
    }, decodeConnectionTest, '连接测试失败');
    if (result) { setTest(result); detail.refresh(); }
  }
  const params = Object.entries(source.connectionParams).map(([key, value]) => `${key}=${value}`).join('  ');
  return <>
    <div className="dq-detail-head">
      <div><h2 className="mono">{source.dataSourceCode}</h2><div className="muted">{source.dataSourceName} · {engineLabels[source.engineType]} · 版本 {source.version}</div></div>
      <StatusTag status={source.status} />
    </div>
    <h3 className="dq-section-title">连接</h3>
    <Descriptions size="small" column={1} items={[
      { key: 'host', label: '主机', children: <span className="mono">{source.host}</span> },
      { key: 'port', label: '端口', children: source.port },
      { key: 'db', label: '默认库', children: source.databaseName ? <span className="mono">{source.databaseName}</span> : '整个实例（模板写 库.表）' },
      { key: 'user', label: '账号', children: <span className="mono">{source.username}</span> },
      { key: 'password', label: '密码', children: '已保存（Vault）· 不回显' },
      { key: 'params', label: '连接参数', children: params ? <span className="mono">{params}</span> : '—' },
      { key: 'pool', label: '连接池上限', children: `${source.maxPoolSize}（系统上限 ${maxPoolSizeLimit}）` },
      ...(source.description ? [{ key: 'description', label: '说明', children: source.description }] : []),
    ]} />
    <h3 className="dq-section-title">最后一次检查 · {localTime(source.lastCheckTime)}</h3>
    {test ? <ConnectionCheck result={test} title="刚刚测试" />
      : <Note tone={source.lastCheckResult === 'FAILED' ? 'error' : undefined}>
        {source.lastCheckResult === null ? '尚未检查。' : source.lastCheckResult === 'PASSED' ? '通过：连通、账号只读、会话只读。' : `未通过：${source.lastCheckMessage ?? '请重新测试查看详情'}`}
        {canManage && ' 点击“测试连接”查看逐步结果。'}</Note>}
    {tester.formError && <Note tone="error">{tester.formError}</Note>}
    <h3 className="dq-section-title">引用的模板（{source.templateCount}）</h3>
    {references.failure ? <QueryFailure failure={references.failure} retry={references.refresh} />
      : <ul className="dq-reference-list">{(references.data?.items ?? []).map(item => <li key={item.templateId}>
        <Link to={dataQueryRoutes.template(item.templateId)} className="mono">{item.templateCode}</Link><span className="muted">{item.templateName}</span><StatusTag status={item.status} />
      </li>)}
        {references.data && references.data.items.length === 0 && <li className="muted">暂无模板引用</li>}</ul>}
    {source.templateCount > 0 && <Link to={`${dataQueryRoutes.templates}?dataSourceId=${source.dataSourceId}`}>查看全部 {source.templateCount} 个 →</Link>}
    {canManage && source.status !== 'DELETED' && <div className="dq-detail-actions">
      <Button type="primary" onClick={() => onStep({ kind: 'edit', source })}>编辑</Button>
      <Button onClick={() => void runTest()} loading={tester.saving}>测试连接</Button>
      {action && <Button danger={action.target === 'INACTIVE'} onClick={() => onStep({ kind: 'status', source })}>{action.verb}</Button>}
      <Button danger onClick={() => onStep({ kind: 'delete', source })}>删除</Button>
    </div>}
  </>;
}

interface ModalProps { source: DataSource; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void }

/** 启用（服务端先做连接检查，不通过拒绝）或停用：二次确认。 */
function StatusModal({ source, onClose, onDone, onAuthorizationChanged }: ModalProps) {
  const command = useDataQueryCommand(onAuthorizationChanged);
  const action = statusAction(source)!;
  const submit = async () => {
    const result = await command.run(dataQueryPaths.dataSourceStatus(source.dataSourceId), 'POST', { status: action.target, expectedVersion: source.version },
      value => decodeMutation(value, 'DATA_SOURCE', source.dataSourceId), `${action.verb}数据源失败`);
    if (result) onDone();
  };
  return <CommandModal title={`${action.verb}数据源 · ${source.dataSourceCode}`} command={command} okText={`确认${action.verb}`}
    danger={action.target === 'INACTIVE'} onOk={() => void submit()} onClose={onClose} onReload={onDone}>
    <Note tone={action.target === 'INACTIVE' ? 'warning' : undefined}>{action.notice}</Note>
    {action.target === 'INACTIVE' && source.activeTemplateCount > 0 && <p>当前有 {source.activeTemplateCount} 个启用中的模板引用此数据源。</p>}
  </CommandModal>;
}

/** 删除：必须停用且没有启用中的模板引用；确认前列出引用模板。 */
function DeleteModal({ source, onClose, onDone, onAuthorizationChanged }: ModalProps) {
  const command = useDataQueryCommand(onAuthorizationChanged);
  const references = useResource(referencePath(source.dataSourceId, 50), decodeTemplatePage, '引用模板加载失败');
  const block = deletionBlock(source);
  const submit = async () => {
    const result = await command.run(dataQueryPaths.dataSourceDeletion(source.dataSourceId, source.version), 'DELETE', undefined,
      value => decodeMutation(value, 'DATA_SOURCE', source.dataSourceId), '删除数据源失败');
    if (result) onDone();
  };
  const items: TemplateSummary[] = references.data?.items ?? [];
  return <CommandModal title={`删除数据源 · ${source.dataSourceCode}`} command={command} okText="确认删除" danger okDisabled={!!block || references.loading}
    onOk={() => void submit()} onClose={onClose} onReload={onDone}>
    {block ? <Note tone="error">{block}</Note> : <Note tone="warning">删除后不可恢复，编码也不能再次使用；已停用模板仍保留对它的历史引用。</Note>}
    {references.loading ? <Loading /> : <>
      <p>引用此数据源的模板（{references.data?.total ?? 0}）：</p>
      {items.length ? <ul className="dq-reference-list">{items.map(item => <li key={item.templateId}>
        <span className="mono">{item.templateCode}</span><span className="muted">{item.templateName}</span><StatusTag status={item.status} /></li>)}</ul>
        : <p className="muted">没有模板引用。</p>}
      {(references.data?.total ?? 0) > items.length && <p className="muted">仅列出前 {items.length} 个。</p>}
    </>}
  </CommandModal>;
}
