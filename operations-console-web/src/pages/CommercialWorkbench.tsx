import { useState } from 'react';
import { Button, Card, Table, Tabs, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { PagingFilters } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  agreementStatusLabels, commercialButtons, commercialPaths, commercialRoutes, hasButton, localTime, termTypeLabels, versionStatusLabels,
} from '../commercial/commercial';
import { consolePaths, decodeAgreementPage, decodeTermVersionPage } from '../commercial/commercialConsole';
import type { AgreementListItem, TermVersionListItem } from '../commercial/commercialConsole';
import { dashboardMetricLabels, decodeDashboard } from '../commercial/commercialPreview';
import type { DashboardMetric } from '../commercial/commercialPreview';
import { BelowStandardTag, CommercialNote, StatusTag } from '../commercial/CommercialParts';
import { VersionTransitionModal } from '../commercial/TermModals';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Decision = { version: TermVersionListItem; action: 'approve' | 'reject' };

/** 卡片对应的入口页：版本类去价目，合同类去合同。 */
const metricTargets: Record<DashboardMetric['metricCode'], string> = {
  PENDING_APPROVAL_VERSIONS: commercialRoutes.terms, SCHEDULED_VERSIONS: commercialRoutes.terms, DRAFT_AGREEMENTS: commercialRoutes.agreements,
  PENDING_SIGNATURE_AGREEMENTS: commercialRoutes.agreements, ACTIVE_AGREEMENTS: commercialRoutes.agreements,
};

/** 商务工作台：五张状态计数卡片与三个待办清单（待审批可直接批准 / 驳回、已排期待生效、待签署合同）。 */
export function CommercialWorkbench({ resources, onAuthorizationChanged }: Props) {
  const navigate = useNavigate();
  const dashboard = useDetail(commercialPaths.dashboard, decodeDashboard);
  const [decision, setDecision] = useState<Decision | null>(null);
  const canApprove = hasButton(resources, commercialButtons.termApprove);
  const count = (code: DashboardMetric['metricCode']) => dashboard.data?.metrics.find(metric => metric.metricCode === code)?.count ?? 0;
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>商务工作台</h1>
      <div className="commercial-actions">
        <Button onClick={dashboard.refresh} loading={dashboard.loading}>刷新</Button>
        <Button onClick={() => void navigate(commercialRoutes.rateCards)}>费率预览</Button>
        <Button onClick={() => void navigate(commercialRoutes.priceSimulator)}>价格试算</Button>
      </div>
    </div>
    {dashboard.loading && !dashboard.data ? <Loading /> : dashboard.failure || !dashboard.data
      ? <QueryFailure failure={dashboard.failure} retry={dashboard.refresh} />
      : <>
        <div className="commercial-kpis">{dashboard.data.metrics.map(metric =>
          <Card key={metric.metricCode} size="small" hoverable className="commercial-kpi" onClick={() => void navigate(metricTargets[metric.metricCode])}>
            <div className="commercial-kpi-label">{dashboardMetricLabels[metric.metricCode]}</div>
            <div className="commercial-kpi-value">{metric.count}</div>
          </Card>)}
        </div>
        <div className="commercial-cell-meta">统计时点 {localTime(dashboard.data.asOf)}</div>
        <Tabs destroyOnHidden items={[
          { key: 'approval', label: `待审批版本 ${count('PENDING_APPROVAL_VERSIONS')}`, children: <ApprovalQueue items={dashboard.data.approvalQueue}
            total={count('PENDING_APPROVAL_VERSIONS')} canApprove={canApprove} onDecide={setDecision} /> },
          { key: 'scheduled', label: `已排期待生效 ${count('SCHEDULED_VERSIONS')}`, children: <ScheduledVersions /> },
          { key: 'signature', label: `待签署合同 ${count('PENDING_SIGNATURE_AGREEMENTS')}`, children: <PendingSignatures /> },
        ]} />
      </>}
    <CommercialNote>批准 / 驳回需要价目审批权限，且审批人不能是提交人（commercial 校验）。驳回后版本回到草稿；commercial 驳回接口目前不接收原因。</CommercialNote>
    {decision && <VersionTransitionModal version={decision.version} action={decision.action} onClose={() => setDecision(null)}
      onReload={() => { setDecision(null); dashboard.refresh(); }} onDone={() => { setDecision(null); dashboard.refresh(); }}
      onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 审批队列：提交时刻最早的前 10 条，行内批准 / 驳回。 */
function ApprovalQueue({ items, total, canApprove, onDecide }: {
  items: TermVersionListItem[]; total: number; canApprove: boolean; onDecide: (decision: Decision) => void;
}) {
  const navigate = useNavigate();
  return <>
    <Table<TermVersionListItem> rowKey="versionId" size="middle" pagination={false} dataSource={items} scroll={{ x: 1190 }}
      locale={{ emptyText: '没有待审批的版本' }} columns={[
        { title: '方案 / 版本', width: 240, render: (_, row) => <div className="commercial-cell"><span className="mono">{row.planCode} · v{row.versionNo}</span>
          <span className="commercial-cell-meta">{termTypeLabels[row.termType] ?? row.termType}</span></div> },
        { title: '合同', width: 180, render: (_, row) => row.agreementId
          ? <Button type="link" size="small" onClick={() => void navigate(commercialRoutes.agreement(row.agreementId!))}>{row.agreementId}</Button> : '—' },
        { title: 'Maker', width: 160, render: (_, row) => <span className="mono">{row.makerAccountId ?? '—'}</span> },
        { title: '提交时刻', width: 150, render: (_, row) => localTime(row.submittedTime) },
        { title: '价格数', width: 80, render: (_, row) => <Tag>{row.priceCount}</Tag> },
        { title: '破标', width: 90, render: (_, row) => <BelowStandardTag count={row.belowStandardCount} /> },
        { title: '状态', width: 100, render: (_, row) => <StatusTag status={row.status} labels={versionStatusLabels} /> },
        { title: '操作', width: 200, render: (_, row) => <span className="commercial-actions">
          <Button size="small" onClick={() => void navigate(commercialRoutes.version(row.versionId))}>查看</Button>
          {canApprove && <Button size="small" type="primary" onClick={() => onDecide({ version: row, action: 'approve' })}>批准</Button>}
          {canApprove && <Button size="small" danger onClick={() => onDecide({ version: row, action: 'reject' })}>驳回</Button>}
        </span> },
      ]} />
    {total > items.length && <CommercialNote>共 {total} 条待审批，这里显示提交最早的 {items.length} 条；其余请到价目的版本页签按「待审批」筛选。</CommercialNote>}
  </>;
}

/** 已排期待生效的版本，按生效起点升序。 */
function ScheduledVersions() {
  const navigate = useNavigate();
  const page = usePaging<TermVersionListItem, PagingFilters>(consolePaths.versions, decodeTermVersionPage, { status: ['SCHEDULED'] }, { notifyOnError: false });
  if (page.failure) return <QueryFailure failure={page.failure} retry={page.refresh} />;
  return <Table<TermVersionListItem> rowKey="versionId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
    scroll={{ x: 900 }} locale={{ emptyText: '没有已排期的版本' }}
    onRow={row => ({ className: 'clickable-row', onClick: () => void navigate(commercialRoutes.version(row.versionId)) })} columns={[
      { title: '方案 / 版本', width: 240, render: (_, row) => <span className="mono">{row.planCode} · v{row.versionNo}</span> },
      { title: '条款类型', width: 160, render: (_, row) => termTypeLabels[row.termType] ?? row.termType },
      { title: '生效时间', width: 160, render: (_, row) => localTime(row.effectiveFrom) },
      { title: 'Checker', width: 160, render: (_, row) => <span className="mono">{row.checkerAccountId ?? '—'}</span> },
      { title: '价格数', width: 80, render: (_, row) => <Tag>{row.priceCount}</Tag> },
    ]} />;
}

/** 待签署合同。 */
function PendingSignatures() {
  const navigate = useNavigate();
  const page = usePaging<AgreementListItem, PagingFilters>(consolePaths.agreements, decodeAgreementPage, { status: ['PENDING_SIGNATURE'] }, { notifyOnError: false });
  if (page.failure) return <QueryFailure failure={page.failure} retry={page.refresh} />;
  return <Table<AgreementListItem> rowKey="agreementId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
    scroll={{ x: 900 }} locale={{ emptyText: '没有待签署的合同' }}
    onRow={row => ({ className: 'clickable-row', onClick: () => void navigate(commercialRoutes.agreement(row.agreementId)) })} columns={[
      { title: '合同号', width: 180, render: (_, row) => <span className="mono">{row.agreementNo}</span> },
      { title: '名称', dataIndex: 'agreementName', ellipsis: true },
      { title: 'Client / Partner', width: 190, render: (_, row) => <span className="mono">{row.clientId ?? row.localPartnerId ?? '—'}</span> },
      { title: '状态', width: 100, render: (_, row) => <StatusTag status={row.status} labels={agreementStatusLabels} /> },
      { title: '更新时间', width: 150, render: (_, row) => localTime(row.updateTime) },
    ]} />;
}
