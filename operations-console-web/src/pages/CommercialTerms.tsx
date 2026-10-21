import { useState } from 'react';
import { Button, Form, Input, Select, Table, Tabs, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { CreatePlanModal } from '../commercial/AgreementModals';
import {
  codePattern, commercialButtons, commercialRoutes, hasButton, idPattern, isId, localTime, termTypeLabels, termTypes, versionStatuses, versionStatusLabels,
} from '../commercial/commercial';
import type { TermPlan } from '../commercial/commercial';
import { consolePaths, consoleRoutes, decodeTermPlanPage, decodeTermVersionPage, listFilters } from '../commercial/commercialConsole';
import type { PagingFilters } from '../api/paging';
import type { TermPlanListItem, TermVersionListItem } from '../commercial/commercialConsole';
import { BelowStandardTag, CommercialNote, SignatureTag, StatusTag } from '../commercial/CommercialParts';
import { CreateVersionModal } from '../commercial/TermModals';
import { TermVersionEditor } from '../commercial/TermVersionEditor';
import { VersionCompare } from '../commercial/VersionCompare';
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
  const navigate = useNavigate();
  const [tab, setTab] = useState<'plans' | 'versions'>('plans');
  const [versionFilters, setVersionFilters] = useState<{ planId?: string }>({});
  const [step, setStep] = useState<{ kind: 'plan' } | { kind: 'version'; plan: TermPlan } | null>(null);
  const canManage = hasButton(resources, commercialButtons.termManage);
  const showVersions = (planId: string) => { setVersionFilters({ planId }); setTab('versions'); };
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>价目</h1></div>
    <Tabs activeKey={tab} onChange={key => setTab(key as 'plans' | 'versions')} tabBarExtraContent={canManage && <Button type="primary" onClick={() => setStep({ kind: 'plan' })}>新建条款方案</Button>} items={[
      { key: 'plans', label: '条款方案', children: <PlanList onShowVersions={showVersions} /> },
      { key: 'versions', label: '版本', children: <VersionList key={versionFilters.planId ?? 'all'} planId={versionFilters.planId} /> },
    ]} />
    <CommercialNote>状态流：草稿 → 待审批 → 已批准 → 客户已签署（历史版本免签）→ 已排期 → 生效 → 已替代 / 已到期。在版本页签勾选同一方案的两个版本可进入价格对比。标准价目在「标准价目」页维护。</CommercialNote>
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
  const navigate = useNavigate();
  const [form] = Form.useForm<PlanFilterValues>();
  const page = usePaging<TermPlanListItem, PagingFilters>(consolePaths.plans, decodeTermPlanPage, {}, { notifyOnError: false });
  const versionLink = (versionId: string | null, versionNo: string | null, from: string | null) => versionId
    ? <Button type="link" size="small" onClick={() => void navigate(commercialRoutes.version(versionId))}>v{versionNo} · {localTime(from)}</Button> : '—';
  return <>
    <Form form={form} name="commercial-plan-filter" layout="vertical" onFinish={(values: PlanFilterValues) => page.applyFilters(listFilters({ ...values }))}>
      <div className="commercial-filters">
        <Form.Item name="agreementId" label="合同 ID" rules={[{ pattern: idPattern, message: '正十进制整数' }]}><Input allowClear maxLength={19} /></Form.Item>
        <Form.Item name="clientId" label="Client ID" rules={[{ pattern: idPattern, message: '正十进制整数' }]}><Input allowClear maxLength={19} /></Form.Item>
        <Form.Item name="termType" label="条款类型" rules={[{ pattern: codePattern, message: '大写代码' }]}>
          <Select allowClear options={termTypes.map(value => ({ value, label: termTypeLabels[value] ?? value }))} /></Form.Item>
        <Form.Item name="status" label="方案状态"><Select allowClear options={['DRAFT', 'ACTIVE', 'RETIRED'].map(value => ({ value, label: value }))} /></Form.Item>
        <div className="commercial-filter-actions"><Button type="primary" htmlType="submit">查询</Button>
          <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button></div>
      </div>
    </Form>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
      : <Table<TermPlanListItem> rowKey="planId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
        scroll={{ x: 1200 }} locale={{ emptyText: '没有符合条件的方案' }} columns={[
          { title: '方案', width: 200, render: (_, row) => <div className="commercial-cell"><span className="mono">{row.planCode}</span>
            <span className="commercial-cell-meta">{termTypeLabels[row.termType] ?? row.termType}</span></div> },
          { title: '合同', width: 180, render: (_, row) => row.agreementId
            ? <Button type="link" size="small" onClick={() => void navigate(commercialRoutes.agreement(row.agreementId!))}>{row.agreementId}</Button> : '—' },
          { title: 'Client / Partner', width: 180, render: (_, row) => <span className="mono">{row.clientId ?? row.localPartnerId ?? '—'}</span> },
          { title: '状态', width: 90, render: (_, row) => <StatusTag status={row.status} /> },
          { title: '当前版本', width: 220, render: (_, row) => versionLink(row.activeVersionId, row.activeVersionNo, row.activeEffectiveFrom) },
          { title: '已排期', width: 220, render: (_, row) => versionLink(row.scheduledVersionId, row.scheduledVersionNo, row.scheduledEffectiveFrom) },
          { title: '操作', width: 160, render: (_, row) => <span className="commercial-actions">
            <Button size="small" onClick={() => onShowVersions(row.planId)}>版本</Button>
            <Button size="small" onClick={() => void navigate(consoleRoutes.history('COMMERCIAL_TERM_PLAN', row.planId))}>历史</Button></span> },
        ]} />}
  </>;
}

interface VersionFilterValues { planId?: string; agreementId?: string; status?: string[] }

/** 版本列表：方案 / 合同 / 状态（多选）筛选；勾选同一方案的两个版本进入对比（较小版本号为基准）。 */
function VersionList({ planId }: { planId?: string }) {
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
        <Form.Item name="planId" label="方案 ID" rules={[{ pattern: idPattern, message: '正十进制整数' }]}><Input allowClear maxLength={19} /></Form.Item>
        <Form.Item name="agreementId" label="合同 ID" rules={[{ pattern: idPattern, message: '正十进制整数' }]}><Input allowClear maxLength={19} /></Form.Item>
        <Form.Item name="status" label="版本状态"><Select mode="multiple" allowClear maxTagCount="responsive"
          options={versionStatuses.map(value => ({ value, label: versionStatusLabels[value] }))} /></Form.Item>
        <div className="commercial-filter-actions"><Button type="primary" htmlType="submit">查询</Button>
          <Button onClick={() => { form.resetFields(); form.setFieldsValue({ planId: undefined }); setSelected([]); page.applyFilters({}); }}>重置</Button>
          <Button disabled={!comparable} onClick={compare}>对比所选</Button></div>
      </div>
    </Form>
    {selected.length === 2 && !comparable && <CommercialNote tone="warning">只能对比同一方案的两个版本。</CommercialNote>}
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
      : <Table<TermVersionListItem> rowKey="versionId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
        scroll={{ x: 1460 }} locale={{ emptyText: '没有符合条件的版本' }}
        rowSelection={{ selectedRowKeys: selected.map(item => item.versionId), onChange: (_, rows) => setSelected(rows.slice(-2)), hideSelectAll: true }}
        onRow={row => ({ className: 'clickable-row', onDoubleClick: () => void navigate(commercialRoutes.version(row.versionId)) })} columns={[
          { title: '方案', width: 200, render: (_, row) => <div className="commercial-cell"><span className="mono">{row.planCode}</span>
            <span className="commercial-cell-meta">{termTypeLabels[row.termType] ?? row.termType}</span></div> },
          { title: '版本', width: 70, render: (_, row) => `v${row.versionNo}` },
          { title: '状态', width: 100, render: (_, row) => <StatusTag status={row.status} labels={versionStatusLabels} /> },
          { title: '生效区间', width: 250, render: (_, row) => ['DRAFT', 'PENDING_APPROVAL', 'APPROVED'].includes(row.status) ? '排期时确定'
            : `${localTime(row.effectiveFrom)} ～ ${row.effectiveTo ? localTime(row.effectiveTo) : '未定'}` },
          { title: '价格数', width: 80, render: (_, row) => <Tag>{row.priceCount}</Tag> },
          { title: '破标', width: 90, render: (_, row) => <BelowStandardTag count={row.belowStandardCount} /> },
          { title: '签署', width: 110, render: (_, row) => <SignatureTag row={row} /> },
          { title: 'Maker / Checker', width: 200, render: (_, row) => <span className="mono">{row.makerAccountId ?? '—'} / {row.checkerAccountId ?? '—'}</span> },
          { title: '提交时刻', width: 150, render: (_, row) => localTime(row.submittedTime) },
          { title: '操作', width: 90, render: (_, row) => <Button size="small" onClick={() => void navigate(commercialRoutes.version(row.versionId))}>打开</Button> },
        ]} />}
  </>;
}
