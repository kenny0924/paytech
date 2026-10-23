import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Form, Input, Select, Spin, Table } from 'antd';
import { session } from '../api/session';
import { usePaging } from '../hooks/usePaging';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { enumLabel, formatDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import { decodePolicyChange, snapshotRows, targetTypes, vaultAdminPath } from '../vault/vault';
import type { SnapshotRow, VaultPolicyChange } from '../vault/vault';
import '../configuration/configuration.css';
import '../vault/vault.css';

/** 策略变更记录：版本倒序；详情抽屉并排显示变更前后字段并高亮差异。 */
export function VaultPolicyChanges() {
  const t = useT('vault'), tCommon = useT('common'), tNav = useT('nav');
  const targetTypeOptions = useMemo(() => targetTypes.map(value => ({ value, label: enumLabel(t, 'enum.targetType', value) })), [t]);
  const [form] = Form.useForm<{ targetType?: string; targetKey?: string }>();
  const page = usePaging<VaultPolicyChange>(`${vaultAdminPath}/policy-changes`);
  const [opened, setOpened] = useState<number | null>(null);
  function search(values: { targetType?: string; targetKey?: string }) {
    page.applyFilters(Object.fromEntries(Object.entries(values).flatMap(([k, v]) => typeof v === 'string' && v.trim() ? [[k, v.trim()]] : [])));
  }
  return <div className="configuration-page vault-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_VAULT_POLICY_CHANGES')}</h1></div>
    <Form form={form} layout="vertical" onFinish={search}><div className="vault-search">
      <Form.Item name="targetType" label={t('policyChanges.field.targetType')}><Select allowClear placeholder={t('filters.all')} options={targetTypeOptions} /></Form.Item>
      <Form.Item name="targetKey" label={t('policyChanges.field.targetKey')}><Input allowClear maxLength={256} placeholder={t('policyChanges.field.targetKeyPlaceholder')} /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('filters.search')}</Button><Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('filters.reset')}</Button></div>
    </div></Form>
    <section className="table-panel"><div className="table-toolbar"><span>{t('policyChanges.count', { count: page.data.total })}</span><Button onClick={page.refresh} loading={page.loading}>{page.error ? tCommon('action.retry') : tCommon('action.refresh')}</Button></div>
      <Table<VaultPolicyChange> rowKey="policyVersionNo" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 1130 }} pagination={page.pagination} columns={[
        { title: t('policyChanges.column.version'), dataIndex: 'policyVersionNo', width: 80 },
        { title: t('policyChanges.column.changeType'), width: 140, render: (_, row) => enumLabel(t, 'enum.changeType', row.changeType) },
        { title: t('policyChanges.column.target'), width: 300, ellipsis: true, render: (_, row) => <div><div>{row.targetKey}</div><div className="cell-meta">{enumLabel(t, 'enum.targetType', row.targetType)}</div></div> },
        { title: t('policyChanges.column.actor'), dataIndex: 'actorId', width: 190, ellipsis: true },
        { title: t('policyChanges.column.reason'), dataIndex: 'reason', width: 220, ellipsis: true },
        { title: t('policyChanges.column.time'), dataIndex: 'createTime', width: 190, render: (value: string) => formatDateTime(value) },
        { title: t('policyChanges.column.actions'), width: 90, fixed: 'right', render: (_, row) => <Button size="small" onClick={() => setOpened(row.policyVersionNo)}>{t('policyChanges.detail')}</Button> },
      ]} /></section>
    {opened !== null && <ChangeDetail versionNo={opened} onClose={() => setOpened(null)} />}
  </div>;
}

/** 变更详情抽屉。 */
function ChangeDetail({ versionNo, onClose }: { versionNo: number; onClose: () => void }) {
  const t = useT('vault');
  const notify = useErrorNotification(); const [detail, setDetail] = useState<VaultPolicyChange>(); const [failed, setFailed] = useState(false);
  useEffect(() => {
    const abort = new AbortController();
    session.request(`${vaultAdminPath}/policy-changes/${versionNo}`, { signal: abort.signal }, value => decodePolicyChange(value, versionNo))
      .then(value => { if (!abort.signal.aborted) setDetail(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, t('policyChanges.detailLoadFailed')); } });
    return () => abort.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [versionNo, notify]);
  return <Drawer title={t('policyChanges.detailTitle', { version: versionNo })} open size={640} onClose={onClose}>
    {failed ? <Alert type="warning" showIcon title={t('policyChanges.detailUnavailable')} /> : !detail ? <Spin /> : <>
      <Descriptions column={1} items={[
        { key: 'type', label: t('policyChanges.column.changeType'), children: enumLabel(t, 'enum.changeType', detail.changeType) },
        { key: 'target', label: t('policyChanges.column.target'), children: t('policyChanges.targetValue', { targetType: enumLabel(t, 'enum.targetType', detail.targetType), targetKey: detail.targetKey }) },
        { key: 'actor', label: t('policyChanges.column.actor'), children: detail.actorId }, { key: 'reason', label: t('policyChanges.column.reason'), children: detail.reason },
        { key: 'time', label: t('policyChanges.column.time'), children: formatDateTime(detail.createTime) },
      ]} />
      <Table<SnapshotRow> className="vault-diff-table" rowKey="field" size="small" pagination={false} dataSource={snapshotRows(detail.beforeSnapshot, detail.afterSnapshot)}
        rowClassName={row => row.changed ? 'vault-diff-changed' : ''} columns={[
          { title: t('policyChanges.diff.field'), dataIndex: 'label', width: 140 }, { title: t('policyChanges.diff.before'), dataIndex: 'before' }, { title: t('policyChanges.diff.after'), dataIndex: 'after' },
        ]} />
    </>}
  </Drawer>;
}
