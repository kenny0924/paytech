import { useMemo, useState } from 'react';
import { Button, Descriptions, Drawer, Form, Input, Select, Table } from 'antd';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { useT } from '../i18n/react';
import { Loading, QueryFailure } from '../tenantAccess/TenantAccessView';
import {
  activeStatusLabel, activeStatuses, dataQueryButtons, dataQueryPaths, dataQueryRoutes, decodeMutation, hasButton, listFilters, localTime,
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

/** 数据源（原型 01、02）：全宽列表，点击行在右侧抽屉查看详情；有维护按钮时可新建、编辑、测试连接、启停与删除。 */
export function DataSources({ resources, onAuthorizationChanged }: Props) {
  const [form] = Form.useForm<FilterValues>(); const t = useT('data-query');
  const statusOptions = useMemo(() => activeStatuses.map(value => ({ value, label: activeStatusLabel(value) })), [t]);
  const page = usePaging<DataSource>(dataQueryPaths.dataSources, decodeDataSourcePage, {}, { notifyOnError: false });
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [step, setStep] = useState<Step>(null);
  const [revision, setRevision] = useState(0);
  const canManage = hasButton(resources, dataQueryButtons.dataSourceManage);
  const items = page.data.items;
  // 新建或修改后打开（或保持）该数据源的详情抽屉。
  const reload = (dataSourceId?: string) => {
    setStep(null);
    if (dataSourceId) setSelectedId(dataSourceId);
    page.refresh(); setRevision(v => v + 1);
  };
  return <div className="configuration-page dq-page">
    <Form form={form} layout="vertical" name="data-source-filter" onFinish={(values: FilterValues) => page.applyFilters(listFilters({ ...values }))}>
      <div className="dq-filters">
        <Form.Item name="status" label={t('filters.status')}><Select allowClear placeholder={t('filters.all')}
          options={statusOptions} /></Form.Item>
        <Form.Item name="engineType" label={t('editor.engine')}><Select allowClear placeholder={t('filters.all')} options={engineTypes.map(value => ({ value, label: engineLabels[value] }))} /></Form.Item>
        <Form.Item name="keyword" label={t('filters.codeOrName')}><Input allowClear maxLength={64} placeholder={t('filters.keyword')} /></Form.Item>
        <div className="dq-filter-actions"><Button type="primary" htmlType="submit">{t('filters.search')}</Button>
          <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('filters.reset')}</Button></div>
      </div>
    </Form>
    <section className="dq-panel">
      <div className="dq-list-toolbar">
        <div><strong>{t('dataSources.count', { count: page.data.total })}</strong>
          <span className="muted">{t('dataSources.intro')}</span></div>
        {canManage && <Button type="primary" onClick={() => setStep({ kind: 'create' })}>{t('dataSources.create')}</Button>}
      </div>
      {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
        : <Table<DataSource> rowKey="dataSourceId" size="middle" loading={page.loading} dataSource={items} pagination={page.pagination}
          tableLayout="fixed" scroll={{ x: 960 }} locale={{ emptyText: t('dataSources.empty') }}
          rowClassName={row => `dq-clickable${row.dataSourceId === selectedId ? ' dq-selected-row' : ''}`}
          onRow={row => ({ onClick: () => setSelectedId(row.dataSourceId) })} columns={[
            { title: t('filters.codeOrName'), width: 280, render: (_, row) => <div className="dq-cell"><span className="mono">{row.dataSourceCode}</span><span className="muted">{row.dataSourceName}</span></div> },
            { title: t('editor.engine'), width: 80, render: (_, row) => engineLabels[row.engineType] },
            { title: t('dataSources.column.address'), width: 260, render: (_, row) => <span className="mono">{addressLabel(row)}</span> },
            { title: t('filters.status'), width: 80, render: (_, row) => <StatusTag status={row.status} /> },
            { title: t('dataSources.column.lastCheck'), width: 180, render: (_, row) => <span className={row.lastCheckResult === 'FAILED' ? 'dq-text-error' : undefined}>{lastCheckLabel(row, localTime)}</span> },
            { title: t('dataSources.column.templateCount'), width: 90, align: 'right', render: (_, row) => row.templateCount },
          ]} />}
    </section>
    <Drawer open={selectedId !== null} rootClassName="dq-modal" size={520} title={t('dataSources.detailTitle')} onClose={() => setSelectedId(null)} destroyOnHidden>
      {selectedId && <DataSourceDetail key={`${selectedId}:${revision}`} dataSourceId={selectedId} canManage={canManage}
        onAuthorizationChanged={onAuthorizationChanged} onStep={setStep} />}
    </Drawer>
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
  const t = useT('data-query');
  const detail = useResource(dataQueryPaths.dataSource(dataSourceId), decodeDataSource);
  const references = useResource(referencePath(dataSourceId, 10), decodeTemplatePage, t('dataSources.referencesLoadFailed'));
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
    }, decodeConnectionTest, t('editor.testFailed'));
    if (result) { setTest(result); detail.refresh(); }
  }
  const params = Object.entries(source.connectionParams).map(([key, value]) => `${key}=${value}`).join('  ');
  return <>
    <div className="dq-detail-head">
      <div><h2 className="mono">{source.dataSourceCode}</h2><div className="muted">{source.dataSourceName} · {engineLabels[source.engineType]} · {t('dataSources.detail.version', { version: source.version })}</div></div>
      <StatusTag status={source.status} />
    </div>
    <h3 className="dq-section-title">{t('dataSources.detail.connection')}</h3>
    <Descriptions size="small" column={1} items={[
      { key: 'host', label: t('editor.host'), children: <span className="mono">{source.host}</span> },
      { key: 'port', label: t('editor.port'), children: source.port },
      { key: 'db', label: t('dataSources.detail.database'), children: source.databaseName ? <span className="mono">{source.databaseName}</span> : t('dataSources.detail.wholeInstance') },
      { key: 'user', label: t('editor.username'), children: <span className="mono">{source.username}</span> },
      { key: 'password', label: t('editor.password'), children: t('dataSources.detail.passwordSaved') },
      { key: 'params', label: t('dataSources.detail.connectionParams'), children: params ? <span className="mono">{params}</span> : '—' },
      { key: 'pool', label: t('editor.maxPoolSize'), children: t('dataSources.detail.maxPoolSize', { size: source.maxPoolSize, max: maxPoolSizeLimit }) },
      ...(source.description ? [{ key: 'description', label: t('editor.description'), children: source.description }] : []),
    ]} />
    <h3 className="dq-section-title">{t('dataSources.detail.lastCheck', { time: localTime(source.lastCheckTime) })}</h3>
    {test ? <ConnectionCheck result={test} title={t('check.justNow')} />
      : <Note tone={source.lastCheckResult === 'FAILED' ? 'error' : undefined}>
        {source.lastCheckResult === null ? t('dataSources.detail.notChecked') : source.lastCheckResult === 'PASSED' ? t('dataSources.detail.checkPassed')
          : t('dataSources.detail.checkFailed', { detail: source.lastCheckMessage ?? t('dataSources.detail.retestForDetail') })}
        {canManage && <>{' '}{t('dataSources.detail.testHint')}</>}</Note>}
    {tester.formError && <Note tone="error">{tester.formError}</Note>}
    <h3 className="dq-section-title">{t('dataSources.detail.references', { count: source.templateCount })}</h3>
    {references.failure ? <QueryFailure failure={references.failure} retry={references.refresh} />
      : <ul className="dq-reference-list">{(references.data?.items ?? []).map(item => <li key={item.templateId}>
        <Link to={dataQueryRoutes.template(item.templateId)} className="mono">{item.templateCode}</Link><span className="muted">{item.templateName}</span><StatusTag status={item.status} />
      </li>)}
        {references.data && references.data.items.length === 0 && <li className="muted">{t('dataSources.detail.noReferences')}</li>}</ul>}
    {source.templateCount > 0 && <Link to={`${dataQueryRoutes.templates}?dataSourceId=${source.dataSourceId}`}>{t('dataSources.detail.viewAll', { count: source.templateCount })}</Link>}
    {canManage && source.status !== 'DELETED' && <div className="dq-detail-actions">
      <Button type="primary" onClick={() => onStep({ kind: 'edit', source })}>{t('dataSources.detail.edit')}</Button>
      <Button onClick={() => void runTest()} loading={tester.saving}>{t('editor.test')}</Button>
      {action && <Button danger={action.target === 'INACTIVE'} onClick={() => onStep({ kind: 'status', source })}>{action.verb}</Button>}
      <Button danger onClick={() => onStep({ kind: 'delete', source })}>{t('dataSources.detail.delete')}</Button>
    </div>}
  </>;
}

interface ModalProps { source: DataSource; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void }

/** 启用（服务端先做连接检查，不通过拒绝）或停用：二次确认。 */
function StatusModal({ source, onClose, onDone, onAuthorizationChanged }: ModalProps) {
  const command = useDataQueryCommand(onAuthorizationChanged); const t = useT('data-query');
  const action = statusAction(source)!; const disabling = action.target === 'INACTIVE';
  const submit = async () => {
    const result = await command.run(dataQueryPaths.dataSourceStatus(source.dataSourceId), 'POST', { status: action.target, expectedVersion: source.version },
      value => decodeMutation(value, 'DATA_SOURCE', source.dataSourceId), disabling ? t('dataSources.status.disableFailed') : t('dataSources.status.enableFailed'));
    if (result) onDone();
  };
  return <CommandModal title={disabling ? t('dataSources.status.disableTitle', { code: source.dataSourceCode }) : t('dataSources.status.enableTitle', { code: source.dataSourceCode })}
    command={command} okText={disabling ? t('dataSources.status.confirmDisable') : t('dataSources.status.confirmEnable')}
    danger={action.target === 'INACTIVE'} onOk={() => void submit()} onClose={onClose} onReload={onDone}>
    <Note tone={action.target === 'INACTIVE' ? 'warning' : undefined}>{action.notice}</Note>
    {action.target === 'INACTIVE' && source.activeTemplateCount > 0 && <p>{t('dataSources.status.activeReferences', { count: source.activeTemplateCount })}</p>}
  </CommandModal>;
}

/** 删除：必须停用且没有启用中的模板引用；确认前列出引用模板。 */
function DeleteModal({ source, onClose, onDone, onAuthorizationChanged }: ModalProps) {
  const command = useDataQueryCommand(onAuthorizationChanged); const t = useT('data-query');
  const references = useResource(referencePath(source.dataSourceId, 50), decodeTemplatePage, t('dataSources.referencesLoadFailed'));
  const block = deletionBlock(source);
  const submit = async () => {
    const result = await command.run(dataQueryPaths.dataSourceDeletion(source.dataSourceId, source.version), 'DELETE', undefined,
      value => decodeMutation(value, 'DATA_SOURCE', source.dataSourceId), t('dataSources.delete.failed'));
    if (result) onDone();
  };
  const items: TemplateSummary[] = references.data?.items ?? [];
  return <CommandModal title={t('dataSources.delete.title', { code: source.dataSourceCode })} command={command} okText={t('dataSources.delete.confirm')} danger okDisabled={!!block || references.loading}
    onOk={() => void submit()} onClose={onClose} onReload={onDone}>
    {block ? <Note tone="error">{block}</Note> : <Note tone="warning">{t('dataSources.delete.notice')}</Note>}
    {references.loading ? <Loading /> : <>
      <p>{t('dataSources.delete.references', { count: references.data?.total ?? 0 })}</p>
      {items.length ? <ul className="dq-reference-list">{items.map(item => <li key={item.templateId}>
        <span className="mono">{item.templateCode}</span><span className="muted">{item.templateName}</span><StatusTag status={item.status} /></li>)}</ul>
        : <p className="muted">{t('dataSources.delete.noReferences')}</p>}
      {(references.data?.total ?? 0) > items.length && <p className="muted">{t('dataSources.delete.partial', { count: items.length })}</p>}
    </>}
  </CommandModal>;
}
