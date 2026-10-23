import { useState } from 'react';
import { Button, Form, Input, Select, Table, Tabs, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { CreatePlanModal } from '../commercial/AgreementModals';
import {
  codePattern, commercialButtons, commercialRoutes, hasButton, idPattern, isId, localTime, termTypes, versionStatuses,
} from '../commercial/commercial';
import type { TermPlan } from '../commercial/commercial';
import { consolePaths, consoleRoutes, decodeTermPlanPage, decodeTermVersionPage, listFilters } from '../commercial/commercialConsole';
import type { PagingFilters } from '../api/paging';
import type { TermPlanListItem, TermVersionListItem } from '../commercial/commercialConsole';
import { BelowStandardTag, CommercialNote, SignatureTag, StatusTag } from '../commercial/CommercialParts';
import { CreateVersionModal } from '../commercial/TermModals';
import { TermVersionEditor } from '../commercial/TermVersionEditor';
import { VersionCompare } from '../commercial/VersionCompare';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/** 价目：/commercial/terms 方案与版本总览，/commercial/terms/versions/:versionId 版本编辑器，/commercial/terms/compare/:base/:target 版本对比。 */
export function CommercialTerms(props: Props) {
  const location = useLocation();
  const parts = location.pathname.replace(/\/$/, '').split('/').slice(3);
  if (parts.length === 0) return <TermsHome {...props} />;
  if (parts.length === 2 && parts[0] === 'versions' && isId(parts[1])) {
    return <TermVersionEditor key={parts[1]} versionId={parts[1]} {...props} />;
  }
  if (parts.length === 3 && parts[0] === 'compare' && isId(parts[1]) && isId(parts[2]) && parts[1] !== parts[2]) {
    return <VersionCompare key={`${parts[1]}-${parts[2]}`} baseVersionId={parts[1]} targetVersionId={parts[2]} />;
  }
  return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
}

/** 价目总览：方案页签（当前 / 已排期版本）与版本页签（多选状态、选两版对比）。 */
function TermsHome({ resources, onAuthorizationChanged }: Props) {
  const t = useT('commercial'), tNav = useT('nav');
  const navigate = useNavigate();
  const [tab, setTab] = useState<'plans' | 'versions'>('plans');
  const [versionFilters, setVersionFilters] = useState<{ planId?: string }>({});
  const [step, setStep] = useState<{ kind: 'plan' } | { kind: 'version'; plan: TermPlan } | null>(null);
  const canManage = hasButton(resources, commercialButtons.termManage);
  const showVersions = (planId: string) => { setVersionFilters({ planId }); setTab('versions'); };
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_COMMERCIAL_TERMS')}</h1></div>
    <Tabs activeKey={tab} onChange={key => setTab(key as 'plans' | 'versions')} tabBarExtraContent={canManage && <Button type="primary" onClick={() => setStep({ kind: 'plan' })}>{t('agreement.detail.createPlan')}</Button>} items={[
      { key: 'plans', label: t('terms.tab.plans'), children: <PlanList onShowVersions={showVersions} /> },
      { key: 'versions', label: t('terms.tab.versions'), children: <VersionList key={versionFilters.planId ?? 'all'} planId={versionFilters.planId} /> },
    ]} />
    <CommercialNote>{t('terms.note')}</CommercialNote>
    {step?.kind === 'plan' && <CreatePlanModal onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={plan => setStep({ kind: 'version', plan })} />}
    {step?.kind === 'version' && <CreateVersionModal planId={step.plan.planId} termType={step.plan.termType} expectedChangeVersion={step.plan.changeVersion}
      agreementId={step.plan.agreementId} entityNos={step.plan.entityNo ? [step.plan.entityNo] : []} onClose={() => setStep(null)}
      onAuthorizationChanged={onAuthorizationChanged}
      onCreated={version => { setStep(null); void navigate(commercialRoutes.version(version.versionId)); }} />}
  </div>;
}

interface PlanFilterValues { agreementId?: string; clientId?: string; termType?: string; status?: string }

/** 方案列表：合同 / Client / 条款类型 / 方案状态筛选；每行显示当前与已排期版本。 */
function PlanList({ onShowVersions }: { onShowVersions: (planId: string) => void }) {
  const t = useT('commercial');
  const navigate = useNavigate();
  const [form] = Form.useForm<PlanFilterValues>();
  const page = usePaging<TermPlanListItem, PagingFilters>(consolePaths.plans, decodeTermPlanPage, {}, { notifyOnError: false });
  const versionLink = (versionId: string | null, versionNo: string | null, from: string | null) => versionId
    ? <Button type="link" size="small" onClick={() => void navigate(commercialRoutes.version(versionId))}>v{versionNo} · {localTime(from)}</Button> : '—';
  return <>
    <Form form={form} name="commercial-plan-filter" layout="vertical" onFinish={(values: PlanFilterValues) => page.applyFilters(listFilters({ ...values }))}>
      <div className="commercial-filters">
        <Form.Item name="agreementId" label={t('plan.field.agreementId')} rules={[{ pattern: idPattern, message: t('form.idPattern') }]}><Input allowClear maxLength={19} /></Form.Item>
        <Form.Item name="clientId" label="Client ID" rules={[{ pattern: idPattern, message: t('form.idPattern') }]}><Input allowClear maxLength={19} /></Form.Item>
        <Form.Item name="termType" label={t('shared.column.termType')} rules={[{ pattern: codePattern, message: t('catalogDrawer.upperCode') }]}>
          <Select allowClear options={termTypes.map(value => ({ value, label: enumLabel(t, 'enum.termType', value) }))} /></Form.Item>
        <Form.Item name="status" label={t('templates.planStatus')}><Select allowClear options={['DRAFT', 'ACTIVE', 'RETIRED'].map(value => ({ value, label: value }))} /></Form.Item>
        <div className="commercial-filter-actions"><Button type="primary" htmlType="submit">{t('list.search')}</Button>
          <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('list.reset')}</Button></div>
      </div>
    </Form>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
      : <Table<TermPlanListItem> rowKey="planId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
        scroll={{ x: 1200 }} locale={{ emptyText: t('terms.plansEmpty') }} columns={[
          { title: t('terms.column.plan'), width: 200, render: (_, row) => <div className="commercial-cell"><span className="mono">{row.planCode}</span>
            <span className="commercial-cell-meta">{enumLabel(t, 'enum.termType', row.termType)}</span></div> },
          { title: t('version.field.agreement'), width: 180, render: (_, row) => row.agreementId
            ? <Button type="link" size="small" onClick={() => void navigate(commercialRoutes.agreement(row.agreementId!))}>{row.agreementId}</Button> : '—' },
          { title: 'Client / Partner', width: 180, render: (_, row) => <span className="mono">{row.clientId ?? row.localPartnerId ?? '—'}</span> },
          { title: t('shared.column.status'), width: 90, render: (_, row) => <StatusTag status={row.status} /> },
          { title: t('terms.column.currentVersion'), width: 220, render: (_, row) => versionLink(row.activeVersionId, row.activeVersionNo, row.activeEffectiveFrom) },
          { title: enumLabel(t, 'enum.versionStatus', 'SCHEDULED'), width: 220, render: (_, row) => versionLink(row.scheduledVersionId, row.scheduledVersionNo, row.scheduledEffectiveFrom) },
          { title: t('shared.column.actions'), width: 160, render: (_, row) => <span className="commercial-actions">
            <Button size="small" onClick={() => onShowVersions(row.planId)}>{t('terms.tab.versions')}</Button>
            <Button size="small" onClick={() => void navigate(consoleRoutes.history('COMMERCIAL_TERM_PLAN', row.planId))}>{t('profiles.history')}</Button></span> },
        ]} />}
  </>;
}

interface VersionFilterValues { planId?: string; agreementId?: string; status?: string[] }

/** 版本列表：方案 / 合同 / 状态（多选）筛选；勾选同一方案的两个版本进入对比（较小版本号为基准）。 */
function VersionList({ planId }: { planId?: string }) {
  const t = useT('commercial');
  const navigate = useNavigate();
  const [form] = Form.useForm<VersionFilterValues>();
  const initial: PagingFilters = planId ? { planId } : {};
  const page = usePaging<TermVersionListItem, PagingFilters>(consolePaths.versions, decodeTermVersionPage, initial, { notifyOnError: false });
  const [selected, setSelected] = useState<TermVersionListItem[]>([]);
  const comparable = selected.length === 2 && selected[0].planId === selected[1].planId;
  const compare = () => {
    const [base, target] = [...selected].sort((a, b) => Number(a.versionNo) - Number(b.versionNo));
    void navigate(consoleRoutes.compare(base.versionId, target.versionId));
  };
  return <>
    <Form form={form} name="commercial-version-filter" layout="vertical" initialValues={{ planId }}
      onFinish={(values: VersionFilterValues) => { setSelected([]); page.applyFilters(listFilters({ ...values })); }}>
      <div className="commercial-filters">
        <Form.Item name="planId" label={t('terms.field.planId')} rules={[{ pattern: idPattern, message: t('form.idPattern') }]}><Input allowClear maxLength={19} /></Form.Item>
        <Form.Item name="agreementId" label={t('plan.field.agreementId')} rules={[{ pattern: idPattern, message: t('form.idPattern') }]}><Input allowClear maxLength={19} /></Form.Item>
        <Form.Item name="status" label={t('terms.field.versionStatus')}><Select mode="multiple" allowClear maxTagCount="responsive"
          options={versionStatuses.map(value => ({ value, label: enumLabel(t, 'enum.versionStatus', value) }))} /></Form.Item>
        <div className="commercial-filter-actions"><Button type="primary" htmlType="submit">{t('list.search')}</Button>
          <Button onClick={() => { form.resetFields(); form.setFieldsValue({ planId: undefined }); setSelected([]); page.applyFilters({}); }}>{t('list.reset')}</Button>
          <Button disabled={!comparable} onClick={compare}>{t('terms.compareSelected')}</Button></div>
      </div>
    </Form>
    {selected.length === 2 && !comparable && <CommercialNote tone="warning">{t('terms.compareSamePlan')}</CommercialNote>}
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
      : <Table<TermVersionListItem> rowKey="versionId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
        scroll={{ x: 1460 }} locale={{ emptyText: t('terms.versionsEmpty') }}
        rowSelection={{ selectedRowKeys: selected.map(item => item.versionId), onChange: (_, rows) => setSelected(rows.slice(-2)), hideSelectAll: true }}
        onRow={row => ({ className: 'clickable-row', onDoubleClick: () => void navigate(commercialRoutes.version(row.versionId)) })} columns={[
          { title: t('terms.column.plan'), width: 200, render: (_, row) => <div className="commercial-cell"><span className="mono">{row.planCode}</span>
            <span className="commercial-cell-meta">{enumLabel(t, 'enum.termType', row.termType)}</span></div> },
          { title: t('shared.column.version'), width: 70, render: (_, row) => `v${row.versionNo}` },
          { title: t('shared.column.status'), width: 100, render: (_, row) => <StatusTag status={row.status} kind="versionStatus" /> },
          { title: t('agreement.field.effectiveRange'), width: 250, render: (_, row) => ['DRAFT', 'PENDING_APPROVAL', 'APPROVED'].includes(row.status) ? t('version.field.setOnSchedule')
            : t('shared.period', { from: localTime(row.effectiveFrom), to: row.effectiveTo ? localTime(row.effectiveTo) : t('shared.openEnded') }) },
          { title: t('terms.column.priceCount'), width: 80, render: (_, row) => <Tag>{row.priceCount}</Tag> },
          { title: t('benchmark.below'), width: 90, render: (_, row) => <BelowStandardTag count={row.belowStandardCount} /> },
          { title: t('agreement.party.signing'), width: 110, render: (_, row) => <SignatureTag row={row} /> },
          { title: 'Maker / Checker', width: 200, render: (_, row) => <span className="mono">{row.makerAccountId ?? '—'} / {row.checkerAccountId ?? '—'}</span> },
          { title: t('terms.column.submittedAt'), width: 150, render: (_, row) => localTime(row.submittedTime) },
          { title: t('shared.column.actions'), width: 90, render: (_, row) => <Button size="small" onClick={() => void navigate(commercialRoutes.version(row.versionId))}>{t('terms.open')}</Button> },
        ]} />}
  </>;
}
