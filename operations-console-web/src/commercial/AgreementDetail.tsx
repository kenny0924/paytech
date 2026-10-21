import { useMemo, useState } from 'react';
import { Alert, Button, Table, Tabs, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  acceptanceMethodLabels, agreementActions, agreementStatusLabels, agreementTypeLabels, commercialButtons, commercialPaths,
  commercialRoutes, decodeAgreementOverview, hasButton, localTime, partyRoleLabels, termTypeLabels, versionBasisLabel, versionStatusLabels,
} from './commercial';
import type { AgreementCapability, AgreementParty, AgreementTermVersion, TermPlan } from './commercial';
import { AcceptanceModal, CapabilityDrawer, CreatePlanModal, ProductsModal, TerminateModal, TransitionModal } from './AgreementModals';
import { CommercialNote, StatusTag } from './CommercialParts';
import { CreateVersionModal } from './TermModals';
import { consoleRoutes } from './commercialConsole';
import { ProductCapabilitySections } from './ProductCapabilitySections';
import { useCatalogCapabilities, useCatalogDimensions, useCatalogProducts } from './useCatalog';

interface Props { agreementId: string; resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Dialog =
  | { kind: 'submit' | 'activate' | 'terminate' | 'products' | 'plan' }
  | { kind: 'accept'; party: AgreementParty }
  | { kind: 'capability'; capability?: AgreementCapability; productCode?: string }
  | { kind: 'version'; plan: TermPlan };

/** 合同详情：主档、参与方与签署、订阅产品、开通项、当前与已排期条款版本；按钮按合同状态与按钮资源显示。 */
export function AgreementDetail({ agreementId, resources, onAuthorizationChanged }: Props) {
  const navigate = useNavigate();
  const decode = useMemo(() => decodeAgreementOverview(agreementId), [agreementId]);
  const detail = useDetail(commercialPaths.agreement(agreementId), decode);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const dimensions = useCatalogDimensions(onAuthorizationChanged);
  const products = useCatalogProducts(onAuthorizationChanged);
  const catalog = useCatalogCapabilities(onAuthorizationChanged);
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const { agreement, termVersions, termVersionsStatus } = detail.data;
  const canManage = hasButton(resources, commercialButtons.agreementManage);
  const canManageTerms = hasButton(resources, commercialButtons.termManage);
  const actions = agreementActions(agreement.status, canManage);
  const close = () => setDialog(null);
  const done = () => { setDialog(null); detail.refresh(); };
  const entities = agreement.agreementType === 'CLIENT' ? [agreement.entityNo ?? '—'] : agreement.operatingEntityNos;
  const base = { onClose: close, onReload: done, onAuthorizationChanged };
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>{agreement.agreementName}</h1>
      <div className="commercial-actions">
        <Button onClick={() => void navigate(commercialRoutes.agreements)}>返回</Button>
        <Button onClick={detail.refresh} loading={detail.loading}>刷新</Button>
        <Button onClick={() => void navigate(consoleRoutes.history('AGREEMENT', agreement.agreementId))}>变更历史</Button>
        {actions.submit && <Button type="primary" onClick={() => setDialog({ kind: 'submit' })}>提交签署</Button>}
        {actions.activate && <Button type="primary" onClick={() => setDialog({ kind: 'activate' })}>激活</Button>}
        {actions.terminate && <Button danger onClick={() => setDialog({ kind: 'terminate' })}>终止</Button>}
      </div>
    </div>
    <dl className="commercial-summary">
      <div><dt>合同编号</dt><dd className="mono">{agreement.agreementNo}</dd></div>
      <div><dt>类型</dt><dd>{agreementTypeLabels[agreement.agreementType]}</dd></div>
      <div><dt>状态</dt><dd><StatusTag status={agreement.status} labels={agreementStatusLabels} /></dd></div>
      <div><dt>{agreement.agreementType === 'CLIENT' ? 'Client' : 'Local Partner'}</dt><dd className="mono">{agreement.clientId ?? agreement.localPartnerId}</dd></div>
      <div><dt>OE</dt><dd>{entities.join('、') || '—'}</dd></div>
      {agreement.agreementType === 'CLIENT' && <div><dt>模式</dt><dd>{agreement.businessMode} · {agreement.fundMode} · {agreement.pricingAuthority}</dd></div>}
      {agreement.referralPartnerId && <div><dt>推荐伙伴</dt><dd className="mono">{agreement.referralPartnerId}</dd></div>}
      <div><dt>生效区间</dt><dd>{localTime(agreement.effectiveFrom)} ～ {agreement.effectiveTo ? localTime(agreement.effectiveTo) : '未定'}</dd></div>
      <div><dt>签署 / 激活</dt><dd>{localTime(agreement.signedTime)} / {localTime(agreement.activatedTime)}</dd></div>
      <div><dt>版本</dt><dd>{agreement.changeVersion}</dd></div>
    </dl>
    <Tabs destroyOnHidden items={[
      { key: 'parties', label: `参与方与签署（${agreement.parties.length}）`, children: <>
        <Table<AgreementParty> rowKey="agreementPartyId" size="middle" pagination={false} dataSource={agreement.parties} scroll={{ x: 900 }} columns={[
          { title: '角色', width: 140, render: (_, row) => partyRoleLabels[row.partyRole] },
          { title: 'Party / 资料版本', render: (_, row) => <div className="commercial-cell"><span className="mono">{row.partyId}</span><span className="commercial-cell-meta mono">{row.partyProfileId}</span></div> },
          { title: '签署', width: 220, render: (_, row) => row.acceptedAt
            ? <div className="commercial-cell"><span><Tag color="green">{row.acceptanceMethod ? acceptanceMethodLabels[row.acceptanceMethod] : '已签'}</Tag>{localTime(row.acceptedAt)}</span>
              <span className="commercial-cell-meta mono">{row.acceptanceReference}</span></div>
            : <Tag>未签署</Tag> },
          { title: '操作', width: 120, render: (_, row) => actions.accept && !row.acceptedAt
            ? <Button size="small" onClick={() => setDialog({ kind: 'accept', party: row })}>登记签署</Button> : null },
        ]} />
        <h3 className="commercial-subtitle">合同文件</h3>
        <Table rowKey="agreementDocumentId" size="small" pagination={false} dataSource={agreement.documents}
          locale={{ emptyText: '尚无文件（提交签署前需要一份签署文件）' }} columns={[
            { title: '用途', dataIndex: 'documentType', width: 200 },
            { title: '文件 ID', render: (_, row) => <span className="mono">{row.fileId}</span> },
            { title: 'SHA-256', render: (_, row) => <span className="mono">{row.contentHash.slice(0, 16)}…</span> },
          ]} />
      </> },
      { key: 'products', label: `产品与开通（${agreement.capabilities.length}）`, children: <>
        <div className="commercial-section-head">
          <span className="commercial-cell-meta">订阅 {agreement.productCodes.length} 个产品；开通项按归属产品分组</span><span className="commercial-spacer" />
          {actions.capabilities && agreement.productCodes.length > 0 && <Button onClick={() => setDialog({ kind: 'capability' })}>新增开通项</Button>}
          {actions.products && <Button onClick={() => setDialog({ kind: 'products' })}>修改订阅</Button>}</div>
        <ProductCapabilitySections agreement={agreement} products={products.items} catalog={catalog.items} definitions={dimensions.items}
          canAdd={actions.capabilities} canEdit={actions.capabilities} canSubscribe={actions.products}
          onAdd={productCode => setDialog({ kind: 'capability', productCode })} onEdit={capability => setDialog({ kind: 'capability', capability })}
          onSubscribe={() => setDialog({ kind: 'products' })} />
      </> },
      { key: 'terms', label: '条款版本', children: <>
        <div className="commercial-section-head"><span className="commercial-spacer" />
          {canManageTerms && <Button onClick={() => setDialog({ kind: 'plan' })}>新建条款方案</Button>}</div>
        {termVersionsStatus === 'UNAVAILABLE' && <Alert type="warning" showIcon className="commercial-banner" title="条款版本暂时无法加载（费率卡读取失败），合同信息不受影响。"
          action={<Button size="small" onClick={detail.refresh}>重试</Button>} />}
        {termVersionsStatus === 'NOT_APPLICABLE' && <CommercialNote>当前与已排期版本只对生效中的 Client 合同汇总；Partner 条款与草稿版本请到「价目」按合同 ID 筛选方案或版本。</CommercialNote>}
        {termVersionsStatus === 'AVAILABLE' && <Table<AgreementTermVersion> rowKey="versionId" size="middle" pagination={false} dataSource={termVersions}
          locale={{ emptyText: '没有当前或已排期的版本' }} onRow={row => ({ className: 'clickable-row', onClick: () => void navigate(commercialRoutes.version(row.versionId)) })}
          columns={[
            { title: '条款类型', width: 160, render: (_, row) => termTypeLabels[row.termType] ?? row.termType },
            { title: '版本', width: 90, render: (_, row) => `v${row.versionNo}` },
            { title: '状态', width: 110, render: (_, row) => <StatusTag status={row.status} labels={versionStatusLabels} /> },
            { title: '时点', width: 100, render: (_, row) => row.timing === 'CURRENT' ? <Tag color="green">当前</Tag> : <Tag color="blue">已排期</Tag> },
            { title: '生效', render: (_, row) => `${localTime(row.effectiveFrom)} ～ ${row.effectiveTo ? localTime(row.effectiveTo) : '未定'}` },
            { title: '口径', width: 200, render: (_, row) => versionBasisLabel(row.pricingModel, row.feeTaxTreatment) },
          ]} />}
      </> },
    ]} />
    {(dialog?.kind === 'submit' || dialog?.kind === 'activate') && <TransitionModal agreement={agreement} action={dialog.kind} onDone={done} {...base} />}
    {dialog?.kind === 'terminate' && <TerminateModal agreement={agreement} onDone={done} {...base} />}
    {dialog?.kind === 'products' && <ProductsModal agreement={agreement} onDone={done} {...base} />}
    {dialog?.kind === 'accept' && <AcceptanceModal agreement={agreement} party={dialog.party} onDone={done} {...base} />}
    {dialog?.kind === 'capability' && <CapabilityDrawer key={dialog.capability?.agreementCapabilityId ?? `new-${dialog.productCode ?? 'all'}`}
      agreement={agreement} capability={dialog.capability} productCode={dialog.productCode} onEdit={capability => setDialog({ kind: 'capability', capability })} onDone={done} {...base} />}
    {dialog?.kind === 'plan' && <CreatePlanModal agreement={agreement} onClose={close} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={plan => setDialog({ kind: 'version', plan })} />}
    {dialog?.kind === 'version' && <CreateVersionModal planId={dialog.plan.planId} termType={dialog.plan.termType} expectedChangeVersion={dialog.plan.changeVersion}
      agreementId={agreement.agreementId} entityNos={entities} onClose={close} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={version => void navigate(commercialRoutes.version(version.versionId))} />}
  </div>;
}
