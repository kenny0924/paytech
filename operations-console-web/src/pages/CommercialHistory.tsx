import { useState } from 'react';
import { Alert, Button, Form, Input, Select, Table, Tag } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { session } from '../api/session';
import type { PagingFilters } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { idPattern, localTime } from '../commercial/commercial';
import {
  aggregateRootTypeLabels, aggregateRootTypes, changeSummary, consolePaths, decodeAgreementPage, decodeBusinessChangePage, exactAgreement, snapshotValue,
} from '../commercial/commercialConsole';
import type { AggregateRootType, BusinessChangeEntry } from '../commercial/commercialConsole';
import { CommercialNote } from '../commercial/CommercialParts';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface QueryValues { aggregateRootType: AggregateRootType; reference: string }

const comparisonLabels: Record<BusinessChangeEntry['comparison'], string> = { FIRST_VERSION: '首个快照', COMPARED: '已比较', NOT_COMPARED: '未比较' };

/**
 * 变更历史：按聚合根（合同 / 条款方案 / 定价画像）展示 commercial BCR 快照时间线
 * <li>合同可输入合同号，经合同列表（合同号前缀）换成精确匹配的合同 ID；查询条件写入 URL，供合同详情、价目与画像页直达。</li>
 */
export function CommercialHistory(_props: Props) {
  const [params, setParams] = useSearchParams();
  const [form] = Form.useForm<QueryValues>();
  const [resolveError, setResolveError] = useState<string | null>(null);
  const type = params.get('aggregateRootType') as AggregateRootType | null;
  const rootId = params.get('aggregateRootId');
  const valid = !!type && (aggregateRootTypes as readonly string[]).includes(type) && !!rootId && idPattern.test(rootId);
  const search = async (values: QueryValues) => {
    setResolveError(null);
    const reference = values.reference.trim();
    let resolved: string | null = reference;
    if (!idPattern.test(reference)) {
      try {
        resolved = values.aggregateRootType === 'AGREEMENT' ? await agreementIdByNo(reference) : null;
      } catch {
        setResolveError('合同号查询失败，请稍后重试或直接输入合同 ID');
        return;
      }
    }
    if (!resolved) {
      setResolveError(values.aggregateRootType === 'AGREEMENT' ? `没有合同号为 ${reference} 的合同` : '请输入聚合根 ID（正十进制整数）');
      return;
    }
    setParams({ aggregateRootType: values.aggregateRootType, aggregateRootId: resolved });
  };
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>变更历史</h1></div>
    <Form form={form} name="commercial-history-query" layout="vertical" onFinish={values => void search(values)}
      initialValues={{ aggregateRootType: type ?? 'AGREEMENT', reference: rootId ?? '' }}>
      <div className="commercial-filters">
        <Form.Item name="aggregateRootType" label="聚合根类型"><Select options={aggregateRootTypes.map(value => ({ value, label: `${aggregateRootTypeLabels[value]} ${value}` }))} /></Form.Item>
        <Form.Item name="reference" label="聚合根 ID / 合同号" rules={[{ required: true, whitespace: true, message: '请输入 ID 或合同号' }]}>
          <Input allowClear maxLength={64} placeholder="7319000000000000101 或 AGR-AE001-0001" /></Form.Item>
        <div className="commercial-filter-actions"><Button type="primary" htmlType="submit">查询</Button></div>
      </div>
    </Form>
    {resolveError && <Alert type="warning" showIcon className="commercial-banner" title={resolveError} />}
    {valid ? <Timeline key={`${type}-${rootId}`} type={type!} rootId={rootId!} />
      : <CommercialNote>选择聚合根类型并输入 ID（合同也可输入合同号）后查询；从合同详情、版本编辑器与定价画像页可直接跳转。</CommercialNote>}
    <CommercialNote>快照来自 commercial 业务变更记录（BCR），按聚合版本倒序；同一版本内合同 / 方案本身在前，开通项、版本等子记录随后。字段级变化由 BFF 与同一记录的上一快照比较得出。</CommercialNote>
  </div>;
}

/** 合同号 → 合同 ID：前缀查询后取编号完全一致的一条。 */
async function agreementIdByNo(agreementNo: string): Promise<string | null> {
  if (!/^[A-Za-z0-9._-]{1,64}$/.test(agreementNo)) return null;
  const page = await session.request(`${consolePaths.agreements}?agreementNo=${encodeURIComponent(agreementNo)}&page=1&size=50`, {}, decodeAgreementPage);
  return exactAgreement(page.items, agreementNo)?.agreementId ?? null;
}

/** 一个聚合根的快照时间线；展开查看字段前后值与完整快照。 */
function Timeline({ type, rootId }: { type: AggregateRootType; rootId: string }) {
  const page = usePaging<BusinessChangeEntry, PagingFilters>(consolePaths.businessChanges, decodeBusinessChangePage,
    { aggregateRootType: type, aggregateRootId: rootId }, { notifyOnError: false });
  if (page.failure) return <QueryFailure failure={page.failure} retry={page.refresh} />;
  return <section className="table-panel">
    <Table<BusinessChangeEntry> rowKey="historyId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
      scroll={{ x: 1100 }} locale={{ emptyText: '该对象还没有变更记录' }}
      expandable={{ expandedRowRender: entry => <SnapshotDetail entry={entry} /> }} columns={[
        { title: '时间', width: 150, render: (_, row) => localTime(row.createTime) },
        { title: '原因', width: 260, render: (_, row) => <div className="commercial-cell"><span className="mono">{row.meta?.reasonCode ?? '—'}</span>
          <span className="commercial-cell-meta">{row.meta?.triggerType ?? ''}</span></div> },
        { title: '聚合版本', width: 90, render: (_, row) => `v${row.versionNo}` },
        { title: '记录', width: 230, render: (_, row) => <div className="commercial-cell"><span>{row.recordType} · {row.operationType}</span>
          <span className="commercial-cell-meta mono">{row.recordId}</span></div> },
        { title: '操作者', width: 160, render: (_, row) => <span className="mono">{row.createBy === '0' ? '系统' : row.createBy}</span> },
        { title: '摘要', render: (_, row) => <span>{row.comparison !== 'COMPARED' && <Tag>{comparisonLabels[row.comparison]}</Tag>}{changeSummary(row)}</span> },
      ]} />
  </section>;
}

/** 字段级变化（上一快照 → 本快照）与完整快照。 */
function SnapshotDetail({ entry }: { entry: BusinessChangeEntry }) {
  const changed = new Set(entry.changedFields ?? []);
  const fields = [...new Set([...Object.keys(entry.data), ...Object.keys(entry.previousData ?? {})])].sort();
  return <Table<string> rowKey={field => field} size="small" pagination={false} dataSource={fields}
    rowClassName={field => changed.has(field) && entry.comparison === 'COMPARED' ? 'commercial-diff-changed' : ''} columns={[
      { title: '字段', width: 240, render: (_, field) => <span className="mono">{field}</span> },
      ...(entry.comparison === 'COMPARED' ? [{ title: `v${entry.previousVersionNo}`, key: 'previous',
        render: (_: unknown, field: string) => <span className="mono">{snapshotValue(entry.previousData?.[field])}</span> }] : []),
      { title: `v${entry.versionNo}`, key: 'current', render: (_, field) => <span className="mono">{snapshotValue(entry.data[field])}</span> },
    ]} />;
}
