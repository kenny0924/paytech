import { useMemo, useState } from 'react';
import { Alert, Button, Table, Tabs, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  agreementActions, commercialButtons, commercialPaths, commercialRoutes, decodeAgreementOverview, hasButton, joinList, localTime, versionBasisLabel,
} from './commercial';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
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
  const t = useT('commercial'), tCommon = useT('common');
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
        <Button onClick={() => void navigate(commercialRoutes.agreements)}>{t('shared.back')}</Button>
        <Button onClick={detail.refresh} loading={detail.loading}>{tCommon('action.refresh')}</Button>
        <Button onClick={() => void navigate(consoleRoutes.history('AGREEMENT', agreement.agreementId))}>{t('shared.changeHistory')}</Button>
        {actions.submit && <Button type="primary" onClick={() => setDialog({ kind: 'submit' })}>{t('agreement.action.submit')}</Button>}
        {actions.activate && <Button type="primary" onClick={() => setDialog({ kind: 'activate' })}>{t('agreement.action.activate')}</Button>}
        {actions.terminate && <Button danger onClick={() => setDialog({ kind: 'terminate' })}>{t('agreement.action.terminate')}</Button>}
      </div>
    </div>
    <dl className="commercial-summary">
      <div><dt>{t('agreement.field.agreementNo')}</dt><dd className="mono">{agreement.agreementNo}</dd></div>
      <div><dt>{t('agreement.field.type')}</dt><dd>{enumLabel(t, 'enum.agreementType', agreement.agreementType)}</dd></div>
      <div><dt>{t('shared.column.status')}</dt><dd><StatusTag status={agreement.status} kind="agreementStatus" /></dd></div>
      <div><dt>{agreement.agreementType === 'CLIENT' ? 'Client' : 'Local Partner'}</dt><dd className="mono">{agreement.clientId ?? agreement.localPartnerId}</dd></div>
      <div><dt>OE</dt><dd>{joinList(entities) || '—'}</dd></div>
      {agreement.agreementType === 'CLIENT' && <div><dt>{t('agreement.field.mode')}</dt><dd>{agreement.businessMode} · {agreement.fundMode} · {agreement.pricingAuthority}</dd></div>}
      {agreement.referralPartnerId && <div><dt>{t('agreement.field.referralPartner')}</dt><dd className="mono">{agreement.referralPartnerId}</dd></div>}
      <div><dt>{t('agreement.field.effectiveRange')}</dt><dd>{t('shared.period', { from: localTime(agreement.effectiveFrom),
        to: agreement.effectiveTo ? localTime(agreement.effectiveTo) : t('shared.openEnded') })}</dd></div>
      <div><dt>{t('agreement.field.signedActivated')}</dt><dd>{localTime(agreement.signedTime)} / {localTime(agreement.activatedTime)}</dd></div>
      <div><dt>{t('shared.column.version')}</dt><dd>{agreement.changeVersion}</dd></div>
    </dl>
    <Tabs destroyOnHidden items={[
      { key: 'parties', label: t('agreement.detail.tab.parties', { count: agreement.parties.length }), children: <>
        <Table<AgreementParty> rowKey="agreementPartyId" size="middle" pagination={false} dataSource={agreement.parties} scroll={{ x: 900 }} columns={[
          { title: t('agreement.party.role'), width: 140, render: (_, row) => enumLabel(t, 'enum.partyRole', row.partyRole) },
          { title: t('agreement.party.party'), render: (_, row) => <div className="commercial-cell"><span className="mono">{row.partyId}</span><span className="commercial-cell-meta mono">{row.partyProfileId}</span></div> },
          { title: t('agreement.party.signing'), width: 220, render: (_, row) => row.acceptedAt
            ? <div className="commercial-cell"><span><Tag color="green">{row.acceptanceMethod ? enumLabel(t, 'enum.acceptanceMethod', row.acceptanceMethod) : t('agreement.party.signed')}</Tag>{localTime(row.acceptedAt)}</span>
              <span className="commercial-cell-meta mono">{row.acceptanceReference}</span></div>
            : <Tag>{t('agreement.party.unsigned')}</Tag> },
          { title: t('shared.column.actions'), width: 120, render: (_, row) => actions.accept && !row.acceptedAt
            ? <Button size="small" onClick={() => setDialog({ kind: 'accept', party: row })}>{t('agreement.party.recordSigning')}</Button> : null },
        ]} />
        <h3 className="commercial-subtitle">{t('agreement.documents.title')}</h3>
        <Table rowKey="agreementDocumentId" size="small" pagination={false} dataSource={agreement.documents}
          locale={{ emptyText: t('agreement.documents.empty') }} columns={[
            { title: t('agreement.documents.purpose'), dataIndex: 'documentType', width: 200 },
            { title: t('agreement.documents.fileId'), render: (_, row) => <span className="mono">{row.fileId}</span> },
            { title: 'SHA-256', render: (_, row) => <span className="mono">{row.contentHash.slice(0, 16)}…</span> },
          ]} />
      </> },
      { key: 'products', label: t('agreement.detail.tab.products', { count: agreement.capabilities.length }), children: <>
        <div className="commercial-section-head">
          <span className="commercial-cell-meta">{t('agreement.detail.productsHint', { count: agreement.productCodes.length })}</span><span className="commercial-spacer" />
          {actions.capabilities && agreement.productCodes.length > 0 && <Button onClick={() => setDialog({ kind: 'capability' })}>{t('agreement.detail.addCapability')}</Button>}
          {actions.products && <Button onClick={() => setDialog({ kind: 'products' })}>{t('productSections.changeSubscription')}</Button>}</div>
        <ProductCapabilitySections agreement={agreement} products={products.items} catalog={catalog.items} definitions={dimensions.items}
          canAdd={actions.capabilities} canEdit={actions.capabilities} canSubscribe={actions.products}
          onAdd={productCode => setDialog({ kind: 'capability', productCode })} onEdit={capability => setDialog({ kind: 'capability', capability })}
          onSubscribe={() => setDialog({ kind: 'products' })} />
      </> },
      { key: 'terms', label: t('agreement.detail.tab.terms'), children: <>
        <div className="commercial-section-head"><span className="commercial-spacer" />
          {canManageTerms && <Button onClick={() => setDialog({ kind: 'plan' })}>{t('agreement.detail.createPlan')}</Button>}</div>
        {termVersionsStatus === 'UNAVAILABLE' && <Alert type="warning" showIcon className="commercial-banner" title={t('agreement.detail.termsUnavailable')}
          action={<Button size="small" onClick={detail.refresh}>{tCommon('action.retry')}</Button>} />}
        {termVersionsStatus === 'NOT_APPLICABLE' && <CommercialNote>{t('agreement.detail.termsNotApplicable')}</CommercialNote>}
        {termVersionsStatus === 'AVAILABLE' && <Table<AgreementTermVersion> rowKey="versionId" size="middle" pagination={false} dataSource={termVersions}
          locale={{ emptyText: t('agreement.detail.termsEmpty') }} onRow={row => ({ className: 'clickable-row', onClick: () => void navigate(commercialRoutes.version(row.versionId)) })}
          columns={[
            { title: t('shared.column.termType'), width: 160, render: (_, row) => enumLabel(t, 'enum.termType', row.termType) },
            { title: t('shared.column.version'), width: 90, render: (_, row) => `v${row.versionNo}` },
            { title: t('shared.column.status'), width: 110, render: (_, row) => <StatusTag status={row.status} kind="versionStatus" /> },
            { title: t('agreement.detail.timing'), width: 100, render: (_, row) => row.timing === 'CURRENT' ? <Tag color="green">{t('agreement.detail.current')}</Tag> : <Tag color="blue">{enumLabel(t, 'enum.versionStatus', 'SCHEDULED')}</Tag> },
            { title: t('shared.column.effective'), render: (_, row) => t('shared.period', { from: localTime(row.effectiveFrom),
              to: row.effectiveTo ? localTime(row.effectiveTo) : t('shared.openEnded') }) },
            { title: t('shared.column.basis'), width: 200, render: (_, row) => versionBasisLabel(row.pricingModel, row.feeTaxTreatment) },
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
