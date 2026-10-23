import { useState } from 'react';
import { Button, Card, Table, Tabs, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { PagingFilters } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  commercialButtons, commercialPaths, commercialRoutes, hasButton, localTime,
} from '../commercial/commercial';
import { consolePaths, decodeAgreementPage, decodeTermVersionPage } from '../commercial/commercialConsole';
import type { AgreementListItem, TermVersionListItem } from '../commercial/commercialConsole';
import { decodeDashboard } from '../commercial/commercialPreview';
import type { DashboardMetric } from '../commercial/commercialPreview';
import { BelowStandardTag, CommercialNote, StatusTag } from '../commercial/CommercialParts';
import { VersionTransitionModal } from '../commercial/TermModals';
import '../configuration/configuration.css';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
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
  const t = useT('commercial'), tCommon = useT('common'), tNav = useT('nav');
  const navigate = useNavigate();
  const dashboard = useDetail(commercialPaths.dashboard, decodeDashboard);
  const [decision, setDecision] = useState<Decision | null>(null);
  const canApprove = hasButton(resources, commercialButtons.termApprove);
  const count = (code: DashboardMetric['metricCode']) => dashboard.data?.metrics.find(metric => metric.metricCode === code)?.count ?? 0;
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>{t('workbench.title')}</h1>
      <div className="commercial-actions">
        <Button onClick={dashboard.refresh} loading={dashboard.loading}>{tCommon('action.refresh')}</Button>
        <Button onClick={() => void navigate(commercialRoutes.rateCards)}>{tNav('resource.OPERATIONS_COMMERCIAL_RATE_CARDS')}</Button>
        <Button onClick={() => void navigate(commercialRoutes.priceSimulator)}>{tNav('resource.OPERATIONS_COMMERCIAL_PRICE_SIMULATOR')}</Button>
      </div>
    </div>
    {dashboard.loading && !dashboard.data ? <Loading /> : dashboard.failure || !dashboard.data
      ? <QueryFailure failure={dashboard.failure} retry={dashboard.refresh} />
      : <>
        <div className="commercial-kpis">{dashboard.data.metrics.map(metric =>
          <Card key={metric.metricCode} size="small" hoverable className="commercial-kpi" onClick={() => void navigate(metricTargets[metric.metricCode])}>
            <div className="commercial-kpi-label">{enumLabel(t, 'enum.dashboardMetric', metric.metricCode)}</div>
            <div className="commercial-kpi-value">{metric.count}</div>
          </Card>)}
        </div>
        <div className="commercial-cell-meta">{t('workbench.asOf', { time: localTime(dashboard.data.asOf) })}</div>
        <Tabs destroyOnHidden items={[
          { key: 'approval', label: `${enumLabel(t, 'enum.dashboardMetric', 'PENDING_APPROVAL_VERSIONS')} ${count('PENDING_APPROVAL_VERSIONS')}`,
            children: <ApprovalQueue items={dashboard.data.approvalQueue} total={count('PENDING_APPROVAL_VERSIONS')} canApprove={canApprove} onDecide={setDecision} /> },
          { key: 'scheduled', label: `${enumLabel(t, 'enum.dashboardMetric', 'SCHEDULED_VERSIONS')} ${count('SCHEDULED_VERSIONS')}`, children: <ScheduledVersions /> },
          { key: 'signature', label: `${enumLabel(t, 'enum.dashboardMetric', 'PENDING_SIGNATURE_AGREEMENTS')} ${count('PENDING_SIGNATURE_AGREEMENTS')}`, children: <PendingSignatures /> },
        ]} />
      </>}
    <CommercialNote>{t('workbench.note')}</CommercialNote>
    {decision && <VersionTransitionModal version={decision.version} action={decision.action} onClose={() => setDecision(null)}
      onReload={() => { setDecision(null); dashboard.refresh(); }} onDone={() => { setDecision(null); dashboard.refresh(); }}
      onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 审批队列：提交时刻最早的前 10 条，行内批准 / 驳回。 */
function ApprovalQueue({ items, total, canApprove, onDecide }: {
  items: TermVersionListItem[]; total: number; canApprove: boolean; onDecide: (decision: Decision) => void;
}) {
  const t = useT('commercial');
  const navigate = useNavigate();
  return <>
    <Table<TermVersionListItem> rowKey="versionId" size="middle" pagination={false} dataSource={items} scroll={{ x: 1190 }}
      locale={{ emptyText: t('workbench.approvalEmpty') }} columns={[
        { title: t('workbench.column.planVersion'), width: 240, render: (_, row) => <div className="commercial-cell"><span className="mono">{row.planCode} · v{row.versionNo}</span>
          <span className="commercial-cell-meta">{enumLabel(t, 'enum.termType', row.termType)}</span></div> },
        { title: t('version.field.agreement'), width: 180, render: (_, row) => row.agreementId
          ? <Button type="link" size="small" onClick={() => void navigate(commercialRoutes.agreement(row.agreementId!))}>{row.agreementId}</Button> : '—' },
        { title: 'Maker', width: 160, render: (_, row) => <span className="mono">{row.makerAccountId ?? '—'}</span> },
        { title: t('terms.column.submittedAt'), width: 150, render: (_, row) => localTime(row.submittedTime) },
        { title: t('terms.column.priceCount'), width: 80, render: (_, row) => <Tag>{row.priceCount}</Tag> },
        { title: t('benchmark.below'), width: 90, render: (_, row) => <BelowStandardTag count={row.belowStandardCount} /> },
        { title: t('shared.column.status'), width: 100, render: (_, row) => <StatusTag status={row.status} kind="versionStatus" /> },
        { title: t('shared.column.actions'), width: 200, render: (_, row) => <span className="commercial-actions">
          <Button size="small" onClick={() => void navigate(commercialRoutes.version(row.versionId))}>{t('workbench.view')}</Button>
          {canApprove && <Button size="small" type="primary" onClick={() => onDecide({ version: row, action: 'approve' })}>{t('versionTransition.approve.ok')}</Button>}
          {canApprove && <Button size="small" danger onClick={() => onDecide({ version: row, action: 'reject' })}>{t('version.action.reject')}</Button>}
        </span> },
      ]} />
    {total > items.length && <CommercialNote>{t('workbench.approvalMore', { total, shown: items.length })}</CommercialNote>}
  </>;
}

/** 已排期待生效的版本，按生效起点升序。 */
function ScheduledVersions() {
  const t = useT('commercial');
  const navigate = useNavigate();
  const page = usePaging<TermVersionListItem, PagingFilters>(consolePaths.versions, decodeTermVersionPage, { status: ['SCHEDULED'] }, { notifyOnError: false });
  if (page.failure) return <QueryFailure failure={page.failure} retry={page.refresh} />;
  return <Table<TermVersionListItem> rowKey="versionId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
    scroll={{ x: 900 }} locale={{ emptyText: t('workbench.scheduledEmpty') }}
    onRow={row => ({ className: 'clickable-row', onClick: () => void navigate(commercialRoutes.version(row.versionId)) })} columns={[
      { title: t('workbench.column.planVersion'), width: 240, render: (_, row) => <span className="mono">{row.planCode} · v{row.versionNo}</span> },
      { title: t('shared.column.termType'), width: 160, render: (_, row) => enumLabel(t, 'enum.termType', row.termType) },
      { title: t('workbench.column.effectiveAt'), width: 160, render: (_, row) => localTime(row.effectiveFrom) },
      { title: 'Checker', width: 160, render: (_, row) => <span className="mono">{row.checkerAccountId ?? '—'}</span> },
      { title: t('terms.column.priceCount'), width: 80, render: (_, row) => <Tag>{row.priceCount}</Tag> },
    ]} />;
}

/** 待签署合同。 */
function PendingSignatures() {
  const t = useT('commercial');
  const navigate = useNavigate();
  const page = usePaging<AgreementListItem, PagingFilters>(consolePaths.agreements, decodeAgreementPage, { status: ['PENDING_SIGNATURE'] }, { notifyOnError: false });
  if (page.failure) return <QueryFailure failure={page.failure} retry={page.refresh} />;
  return <Table<AgreementListItem> rowKey="agreementId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
    scroll={{ x: 900 }} locale={{ emptyText: t('workbench.signatureEmpty') }}
    onRow={row => ({ className: 'clickable-row', onClick: () => void navigate(commercialRoutes.agreement(row.agreementId)) })} columns={[
      { title: t('agreements.column.agreementNo'), width: 180, render: (_, row) => <span className="mono">{row.agreementNo}</span> },
      { title: t('catalogDrawer.name'), dataIndex: 'agreementName', ellipsis: true },
      { title: 'Client / Partner', width: 190, render: (_, row) => <span className="mono">{row.clientId ?? row.localPartnerId ?? '—'}</span> },
      { title: t('shared.column.status'), width: 100, render: (_, row) => <StatusTag status={row.status} kind="agreementStatus" /> },
      { title: t('shared.column.updatedAt'), width: 150, render: (_, row) => localTime(row.updateTime) },
    ]} />;
}
