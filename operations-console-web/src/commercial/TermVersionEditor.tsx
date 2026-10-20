import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Empty, Select, Table, Tag } from 'antd';
import { session } from '../api/session';
import { useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  agreementStatusLabels, commercialButtons, commercialPaths, commercialRoutes, componentSummary, coverageGaps, decodeWorkspace, hasButton, localTime,
  partnerTermTypes, priceMatrix, termTypeLabels, versionActions, versionStatusLabels,
} from './commercial';
import type { MatrixRow, VersionAction } from './commercial';
import { CommercialNote, StatusTag } from './CommercialParts';
import { consolePaths, consoleRoutes, decodeTermVersionPage } from './commercialConsole';
import type { TermVersionListItem } from './commercialConsole';
import { PriceEditorDrawer } from './PriceEditorDrawer';
import { CreateVersionModal, ScheduleModal, VersionTransitionModal } from './TermModals';

interface Props { versionId: string; resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Dialog = { kind: Exclude<VersionAction, 'schedule'> } | { kind: 'schedule' | 'edit' | 'copy' };

/**
 * 条款版本编辑器：版本概要、价格矩阵（OE × 币种 × 画像 × 计费事件）、覆盖提示与状态按钮
 * <li>草稿可编辑价格与提交（价目维护）；待审批可批准 / 驳回（价目审批）；已批准可排期；已排期可手工激活；任何状态可复制为新草稿。</li>
 */
export function TermVersionEditor({ versionId, resources, onAuthorizationChanged }: Props) {
  const navigate = useNavigate();
  const decode = useMemo(() => decodeWorkspace(versionId), [versionId]);
  const detail = useDetail(commercialPaths.version(versionId), decode);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const matrix = useMemo(() => priceMatrix(detail.data?.prices ?? []), [detail.data]);
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const { version, agreement, prices } = detail.data;
  const actions = versionActions(version.status, { canManage: hasButton(resources, commercialButtons.termManage),
    canApprove: hasButton(resources, commercialButtons.termApprove) });
  const gaps = coverageGaps(agreement.capabilities, prices);
  const partner = partnerTermTypes.includes(version.termType);
  const close = () => setDialog(null);
  const done = () => { setDialog(null); detail.refresh(); };
  const base = { onClose: close, onReload: done, onAuthorizationChanged };
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>{termTypeLabels[version.termType] ?? version.termType} · v{version.versionNo}</h1>
      <div className="commercial-actions">
        <Button onClick={() => void navigate(commercialRoutes.terms)}>返回</Button>
        <Button onClick={detail.refresh} loading={detail.loading}>刷新</Button>
        <CompareWith versionId={version.versionId} planId={version.planId} />
        <Button onClick={() => void navigate(consoleRoutes.history('COMMERCIAL_TERM_PLAN', version.planId))}>变更历史</Button>
        {actions.editPrices && <Button onClick={() => setDialog({ kind: 'edit' })}>编辑价格</Button>}
        {actions.submit && <Button type="primary" disabled={prices.length === 0} onClick={() => setDialog({ kind: 'submit' })}>提交审批</Button>}
        {actions.approve && <Button type="primary" onClick={() => setDialog({ kind: 'approve' })}>批准</Button>}
        {actions.reject && <Button danger onClick={() => setDialog({ kind: 'reject' })}>驳回</Button>}
        {actions.schedule && <Button type="primary" onClick={() => setDialog({ kind: 'schedule' })}>排期</Button>}
        {actions.activate && <Button onClick={() => setDialog({ kind: 'activate' })}>立即激活</Button>}
        {actions.copy && <Button onClick={() => setDialog({ kind: 'copy' })}>复制为新版本</Button>}
      </div>
    </div>
    <dl className="commercial-summary">
      <div><dt>状态</dt><dd><StatusTag status={version.status} labels={versionStatusLabels} /></dd></div>
      <div><dt>版本 / 方案</dt><dd className="mono">{version.versionId} / {version.planId}</dd></div>
      <div><dt>合同</dt><dd><Button type="link" size="small" onClick={() => void navigate(commercialRoutes.agreement(agreement.agreementId))}>
        {agreement.agreementNo}</Button> <StatusTag status={agreement.status} labels={agreementStatusLabels} /></dd></div>
      <div><dt>{partner ? 'Local Partner' : 'Client'}</dt><dd className="mono">{agreement.localPartnerId ?? agreement.clientId ?? '—'}</dd></div>
      <div><dt>可定价 OE</dt><dd>{agreement.entityNos.join('、') || '—'}</dd></div>
      <div><dt>生效</dt><dd>{['DRAFT', 'PENDING_APPROVAL', 'APPROVED'].includes(version.status) ? '排期时确定'
        : `${localTime(version.effectiveFrom)} ～ ${version.effectiveTo ? localTime(version.effectiveTo) : '未定'}`}</dd></div>
      <div><dt>口径</dt><dd>{version.pricingModel} · {version.feeTaxTreatment} · {version.roundingMode}</dd></div>
      <div><dt>版本令牌</dt><dd>{version.changeVersion}</dd></div>
    </dl>
    {!partner && gaps.length > 0 && <Alert type="warning" showIcon className="commercial-banner" title="以下开通项币种还没有价格，排期时将被拒绝"
      description={gaps.join('；')} />}
    <div className="commercial-section-head"><h2>价格矩阵</h2><Tag>{prices.length} 条价格</Tag><Tag>{matrix.events.length} 个计费事件</Tag></div>
    <Table<MatrixRow> rowKey="key" size="middle" pagination={false} dataSource={matrix.rows} scroll={{ x: 640 + matrix.events.length * 200 }}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={actions.editPrices ? '还没有价格，点击「编辑价格」添加' : '该版本没有价格'} /> }}
      columns={[
        { title: 'OE', dataIndex: 'entityNo', width: 80, fixed: 'left',
          onCell: (row, index) => ({ rowSpan: index !== undefined && index > 0 && matrix.rows[index - 1].entityNo === row.entityNo ? 0
            : matrix.rows.filter(other => other.entityNo === row.entityNo).length }) },
        { title: '币种', dataIndex: 'currency', width: 70, fixed: 'left' },
        { title: '画像', width: 220, render: (_, row) => <div className="commercial-cell"><span className="mono">{row.pricingProfileCode}</span>
          <span className="commercial-cell-meta">{row.price.paymentMethodCode ?? '—'} · 优先级 {row.priorityNo}
            {row.price.conditions.length ? ` · ${row.price.conditions.map(c => `${c.dimensionCode}=${c.conditionValue}`).join(', ')}` : ''}</span></div> },
        ...matrix.events.map(event => ({ title: event, key: event, width: 200, render: (_: unknown, row: MatrixRow) => row.events[event]
          ? <div className="commercial-cell">{row.events[event].map(component => <span key={component.componentCode}>
            <Tag>{component.componentCode}</Tag>{componentSummary(component)}</span>)}</div>
          : <span className="commercial-cell-meta">—</span> })),
        ...(!partner ? [{ title: '结算', key: 'settlement', width: 150, render: (_: unknown, row: MatrixRow) => row.price.settlement
          ? `T+${row.price.settlement.settlementBusinessDays} · ${row.price.settlement.settlementTimezone}` : '—' }] : []),
      ]} />
    <CommercialNote>价格按 OE、币种、优先级排序；同一画像在不同币种各有一条价格。阶梯组件在单元格内显示档数，编辑价格可查看与修改档位。</CommercialNote>
    {dialog?.kind === 'edit' && <PriceEditorDrawer version={version} scope={agreement} prices={prices} onClose={close} onSaved={done}
      onAuthorizationChanged={onAuthorizationChanged} />}
    {(dialog?.kind === 'submit' || dialog?.kind === 'approve' || dialog?.kind === 'reject' || dialog?.kind === 'activate')
      && <VersionTransitionModal version={version} action={dialog.kind} onDone={done} {...base} />}
    {dialog?.kind === 'schedule' && <ScheduleModal version={version} onDone={done} {...base} />}
    {dialog?.kind === 'copy' && <CreateVersionModal planId={version.planId} termType={version.termType} expectedChangeVersion={version.changeVersion}
      agreementId={version.agreementId} copyFromVersionId={version.versionId} onClose={close} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={created => { setDialog(null); void navigate(commercialRoutes.version(created.versionId)); }} />}
  </div>;
}

/** 与同方案的另一个版本对比：较小版本号为基准；读取方案版本列表失败时不显示。 */
function CompareWith({ versionId, planId }: { versionId: string; planId: string }) {
  const navigate = useNavigate();
  const [versions, setVersions] = useState<TermVersionListItem[]>([]);
  useEffect(() => {
    const controller = new AbortController();
    session.request(`${consolePaths.versions}?planId=${planId}&page=1&size=50`, { signal: controller.signal }, decodeTermVersionPage)
      .then(page => { if (!controller.signal.aborted) setVersions(page.items); })
      .catch(() => { if (!controller.signal.aborted) setVersions([]); });
    return () => controller.abort();
  }, [planId]);
  const current = versions.find(item => item.versionId === versionId);
  const others = versions.filter(item => item.versionId !== versionId);
  if (!current || others.length === 0) return null;
  const compare = (otherId: string) => {
    const other = others.find(item => item.versionId === otherId)!;
    const [base, target] = Number(other.versionNo) < Number(current.versionNo) ? [other, current] : [current, other];
    void navigate(consoleRoutes.compare(base.versionId, target.versionId));
  };
  return <Select className="commercial-compare-select" placeholder="与版本对比" value={null} onChange={compare}
    options={others.map(item => ({ value: item.versionId, label: `对比 v${item.versionNo}（${item.status}）` }))} />;
}
