import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Form, Input, Select, Spin, Table } from 'antd';
import { session } from '../api/session';
import { usePaging } from '../hooks/usePaging';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { changeTypeLabels, decodePolicyChange, snapshotRows, targetTypeLabels, vaultAdminPath } from '../vault/vault';
import type { SnapshotRow, VaultPolicyChange } from '../vault/vault';
import '../configuration/configuration.css';
import '../vault/vault.css';

const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });

/** 策略变更记录：版本倒序；详情抽屉并排显示变更前后字段并高亮差异。 */
export function VaultPolicyChanges() {
  const [form] = Form.useForm<{ targetType?: string; targetKey?: string }>();
  const page = usePaging<VaultPolicyChange>(`${vaultAdminPath}/policy-changes`);
  const [opened, setOpened] = useState<number | null>(null);
  function search(values: { targetType?: string; targetKey?: string }) {
    page.applyFilters(Object.fromEntries(Object.entries(values).flatMap(([k, v]) => typeof v === 'string' && v.trim() ? [[k, v.trim()]] : [])));
  }
  return <div className="configuration-page vault-page"><div className="page-heading"><h1>策略变更记录</h1></div>
    <Form form={form} layout="vertical" onFinish={search}><div className="vault-search">
      <Form.Item name="targetType" label="对象类型"><Select allowClear placeholder="全部" options={Object.entries(targetTypeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Form.Item name="targetKey" label="对象标识"><Input allowClear maxLength={256} placeholder="数据类型编码或规则标识" /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">查询</Button><Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button></div>
    </div></Form>
    <section className="table-panel"><div className="table-toolbar"><span>共 {page.data.total} 条变更</span><Button onClick={page.refresh} loading={page.loading}>{page.error ? '重试' : '刷新'}</Button></div>
      <Table<VaultPolicyChange> rowKey="policyVersionNo" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 1130 }} pagination={page.pagination} columns={[
        { title: '版本', dataIndex: 'policyVersionNo', width: 80 },
        { title: '变更类型', width: 140, render: (_, row) => changeTypeLabels[row.changeType] ?? row.changeType },
        { title: '对象', width: 300, ellipsis: true, render: (_, row) => <div><div>{row.targetKey}</div><div className="cell-meta">{targetTypeLabels[row.targetType] ?? row.targetType}</div></div> },
        { title: '操作人', dataIndex: 'actorId', width: 190, ellipsis: true },
        { title: '理由', dataIndex: 'reason', width: 220, ellipsis: true },
        { title: '时间', dataIndex: 'createTime', width: 190, render: date },
        { title: '操作', width: 90, fixed: 'right', render: (_, row) => <Button size="small" onClick={() => setOpened(row.policyVersionNo)}>详情</Button> },
      ]} /></section>
    {opened !== null && <ChangeDetail versionNo={opened} onClose={() => setOpened(null)} />}
  </div>;
}

/** 变更详情抽屉。 */
function ChangeDetail({ versionNo, onClose }: { versionNo: number; onClose: () => void }) {
  const notify = useErrorNotification(); const [detail, setDetail] = useState<VaultPolicyChange>(); const [failed, setFailed] = useState(false);
  useEffect(() => {
    const abort = new AbortController();
    session.request(`${vaultAdminPath}/policy-changes/${versionNo}`, { signal: abort.signal }, value => decodePolicyChange(value, versionNo))
      .then(value => { if (!abort.signal.aborted) setDetail(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, '变更详情加载失败'); } });
    return () => abort.abort();
  }, [versionNo, notify]);
  return <Drawer title={`策略版本 ${versionNo}`} open size={640} onClose={onClose}>
    {failed ? <Alert type="warning" showIcon title="变更详情暂时无法加载" /> : !detail ? <Spin /> : <>
      <Descriptions column={1} items={[
        { key: 'type', label: '变更类型', children: changeTypeLabels[detail.changeType] ?? detail.changeType },
        { key: 'target', label: '对象', children: `${targetTypeLabels[detail.targetType] ?? detail.targetType}：${detail.targetKey}` },
        { key: 'actor', label: '操作人', children: detail.actorId }, { key: 'reason', label: '理由', children: detail.reason },
        { key: 'time', label: '时间', children: date(detail.createTime) },
      ]} />
      <Table<SnapshotRow> className="vault-diff-table" rowKey="field" size="small" pagination={false} dataSource={snapshotRows(detail.beforeSnapshot, detail.afterSnapshot)}
        rowClassName={row => row.changed ? 'vault-diff-changed' : ''} columns={[
          { title: '字段', dataIndex: 'label', width: 140 }, { title: '变更前', dataIndex: 'before' }, { title: '变更后', dataIndex: 'after' },
        ]} />
    </>}
  </Drawer>;
}
