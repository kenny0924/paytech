import { useState } from 'react';
import { Alert, Button, Form, Input, Select, Table } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { session } from '../api/session';
import type { PagingFilters } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { idPattern, localTime } from '../commercial/commercial';
import {
  aggregateRootTypes, changeSummary, consolePaths, decodeAgreementPage, decodeBusinessChangePage, exactAgreement, snapshotValue,
} from '../commercial/commercialConsole';
import type { AggregateRootType, BusinessChangeEntry } from '../commercial/commercialConsole';
import { CommercialNote } from '../commercial/CommercialParts';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface QueryValues { aggregateRootType: AggregateRootType; reference: string }


/**
 * 变更历史：按聚合根（合同 / 条款方案 / 定价画像）展示 commercial BCR 快照时间线
 * <li>合同可输入合同号，经合同列表（合同号前缀）换成精确匹配的合同 ID；查询条件写入 URL，供合同详情、价目与画像页直达。</li>
 */
export function CommercialHistory(_props: Props) {
  const t = useT('commercial'), tNav = useT('nav');
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
        setResolveError(t('history.resolveFailed'));
        return;
      }
    }
    if (!resolved) {
      setResolveError(values.aggregateRootType === 'AGREEMENT' ? t('history.agreementNotFound', { agreementNo: reference }) : t('history.idRequired'));
      return;
    }
    setParams({ aggregateRootType: values.aggregateRootType, aggregateRootId: resolved });
  };
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_COMMERCIAL_HISTORY')}</h1></div>
    <Form form={form} name="commercial-history-query" layout="vertical" onFinish={values => void search(values)}
      initialValues={{ aggregateRootType: type ?? 'AGREEMENT', reference: rootId ?? '' }}>
      <div className="commercial-filters">
        <Form.Item name="aggregateRootType" label={t('history.rootType')}><Select options={aggregateRootTypes.map(value => ({ value,
          label: `${enumLabel(t, 'enum.aggregateRootType', value)} ${value}` }))} /></Form.Item>
        <Form.Item name="reference" label={t('history.reference')} rules={[{ required: true, whitespace: true, message: t('history.referenceRequired') }]}>
          <Input allowClear maxLength={64} placeholder={t('history.referencePlaceholder')} /></Form.Item>
        <div className="commercial-filter-actions"><Button type="primary" htmlType="submit">{t('list.search')}</Button></div>
      </div>
    </Form>
    {resolveError && <Alert type="warning" showIcon className="commercial-banner" title={resolveError} />}
    {valid ? <Timeline key={`${type}-${rootId}`} type={type!} rootId={rootId!} />
      : <CommercialNote>{t('history.hint')}</CommercialNote>}
    <CommercialNote>{t('history.note')}</CommercialNote>
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
  const t = useT('commercial');
  const page = usePaging<BusinessChangeEntry, PagingFilters>(consolePaths.businessChanges, decodeBusinessChangePage,
    { aggregateRootType: type, aggregateRootId: rootId }, { notifyOnError: false });
  if (page.failure) return <QueryFailure failure={page.failure} retry={page.refresh} />;
  return <section className="table-panel">
    <Table<BusinessChangeEntry> rowKey="historyId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
      scroll={{ x: 1100 }} locale={{ emptyText: t('history.empty') }}
      expandable={{ expandedRowRender: entry => <SnapshotDetail entry={entry} /> }} columns={[
        { title: t('history.column.time'), width: 150, render: (_, row) => localTime(row.createTime) },
        { title: t('history.column.reason'), width: 260, render: (_, row) => <div className="commercial-cell"><span className="mono">{row.meta?.reasonCode ?? '—'}</span>
          <span className="commercial-cell-meta">{row.meta?.triggerType ?? ''}</span></div> },
        { title: t('history.column.rootVersion'), width: 90, render: (_, row) => `v${row.versionNo}` },
        { title: t('history.column.record'), width: 230, render: (_, row) => <div className="commercial-cell"><span>{row.recordType} · {row.operationType}</span>
          <span className="commercial-cell-meta mono">{row.recordId}</span></div> },
        { title: t('history.column.operator'), width: 160, render: (_, row) => row.createBy === '0' ? t('history.system')
          : <div className="commercial-cell">{row.createByLabel && <span>{row.createByLabel}</span>}<span className="mono commercial-cell-meta">{row.createBy}</span></div> },
        { title: t('history.column.summary'), render: (_, row) => changeSummary(row) },
      ]} />
  </section>;
}

/** 字段级变化（上一快照 → 本快照）与完整快照。 */
function SnapshotDetail({ entry }: { entry: BusinessChangeEntry }) {
  const t = useT('commercial');
  const changed = new Set(entry.changedFields ?? []);
  const fields = [...new Set([...Object.keys(entry.data), ...Object.keys(entry.previousData ?? {})])].sort();
  return <Table<string> rowKey={field => field} size="small" pagination={false} dataSource={fields}
    rowClassName={field => changed.has(field) && entry.comparison === 'COMPARED' ? 'commercial-diff-changed' : ''} columns={[
      { title: t('history.column.field'), width: 240, render: (_, field) => <span className="mono">{field}</span> },
      ...(entry.comparison === 'COMPARED' ? [{ title: `v${entry.previousVersionNo}`, key: 'previous',
        render: (_: unknown, field: string) => <span className="mono">{snapshotValue(entry.previousData?.[field])}</span> }] : []),
      { title: `v${entry.versionNo}`, key: 'current', render: (_, field) => <span className="mono">{snapshotValue(entry.data[field])}</span> },
    ]} />;
}
