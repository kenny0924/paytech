import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Select, Tag, Tooltip } from 'antd';
import { session } from '../api/session';
import { Navigate, useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  commercialButtons, commercialPaths, commercialRoutes, coverageGaps, hasButton, joinList, localTime, partnerTermTypes, priceMatrix, versionBasisLabel,
} from './commercial';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import type { VersionAction } from './commercial';
import { belowStandardComponents, relevanceText } from './benchmark';
import { CommercialNote, StatusTag } from './CommercialParts';
import { consolePaths, consoleRoutes, decodeTermVersionPage } from './commercialConsole';
import type { TermVersionListItem } from './commercialConsole';
import { PriceEditorDrawer } from './PriceEditorDrawer';
import { PriceMatrixView } from './PriceMatrixView';
import { CreateVersionModal, FillFromTemplateModal, ScheduleModal, SignatureModal, VersionTransitionModal } from './TermModals';
import { decodeVersionWorkspace, templateRoutes } from './templates';
import type { RelevanceWarning } from './termReview';
import { useCatalogCapabilities, useCatalogDimensions } from './useCatalog';
import { signatureState, signatureStateColors, termVersionActions } from './versionFlow';

interface Props { versionId: string; resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Dialog = { kind: Exclude<VersionAction, 'schedule'> } | { kind: 'schedule' | 'edit' | 'copy' | 'sign' | 'fill' };

/**
 * 条款版本编辑器：版本概要、来源与签署、价格矩阵（按开通项分组，来源 / 破标标记）、覆盖与相关性提示、状态按钮
 * <li>草稿可编辑价格、从标准价目补齐与提交（价目维护）；待审批可批准 / 驳回（价目审批）。</li>
 * <li>已批准后登记客户签署（签署权限），客户已签署或历史免签才能排期；已排期可手工激活；任何状态可新建版本（复制 / 基于标准价目 / 空白）。</li>
 * <li>版本读返回标准价目版本（ownerType=PLATFORM_TEMPLATE）时转到标准价目工作区。</li>
 */
export function TermVersionEditor({ versionId, resources, onAuthorizationChanged }: Props) {
  const t = useT('commercial'), tCommon = useT('common');
  const navigate = useNavigate();
  const decode = useMemo(() => decodeVersionWorkspace(versionId), [versionId]);
  const read = useDetail(commercialPaths.version(versionId), decode);
  const detail = { ...read, data: read.data?.kind === 'AGREEMENT' ? read.data.workspace : undefined };
  const catalog = useCatalogCapabilities(onAuthorizationChanged);
  const definitions = useCatalogDimensions(onAuthorizationChanged).items;
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const [savedWarnings, setSavedWarnings] = useState<RelevanceWarning[]>([]);
  const templateLabel = useTemplateSourceLabel(detail.data?.version.sourceTemplateVersionId ?? null);
  if (read.data?.kind === 'TEMPLATE') return <Navigate replace to={templateRoutes.version(versionId)} />;
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const { version, agreement, prices } = detail.data;
  const actions = termVersionActions(version, { canManage: hasButton(resources, commercialButtons.termManage),
    canApprove: hasButton(resources, commercialButtons.termApprove), canSign: hasButton(resources, commercialButtons.termSign) });
  const gaps = coverageGaps(agreement.capabilities, prices);
  const partner = partnerTermTypes.includes(version.termType);
  const warnings = version.relevanceWarnings.length ? version.relevanceWarnings : savedWarnings;
  const belowStandard = version.belowStandardCount ?? belowStandardComponents(prices);
  const signature = signatureState(version);
  const close = () => setDialog(null);
  const done = () => { setDialog(null); detail.refresh(); };
  const base = { onClose: close, onReload: done, onAuthorizationChanged };
  const eventCount = priceMatrix(prices).events.length;
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>{enumLabel(t, 'enum.termType', version.termType)} · v{version.versionNo}</h1>
      <div className="commercial-actions">
        <Button onClick={() => void navigate(commercialRoutes.terms)}>{t('shared.back')}</Button>
        <Button onClick={detail.refresh} loading={detail.loading}>{tCommon('action.refresh')}</Button>
        <CompareWith versionId={version.versionId} planId={version.planId} />
        <Button onClick={() => void navigate(consoleRoutes.history('COMMERCIAL_TERM_PLAN', version.planId))}>{t('shared.changeHistory')}</Button>
        {actions.editPrices && <Button onClick={() => setDialog({ kind: 'edit' })}>{t('version.action.editPrices')}</Button>}
        {actions.fillFromTemplate && !partner && <Button onClick={() => setDialog({ kind: 'fill' })}>{t('version.action.fillFromTemplate')}</Button>}
        {actions.submit && <Button type="primary" disabled={prices.length === 0} onClick={() => setDialog({ kind: 'submit' })}>{t('versionTransition.submit.title')}</Button>}
        {actions.approve && <Button type="primary" onClick={() => setDialog({ kind: 'approve' })}>{t('versionTransition.approve.ok')}</Button>}
        {actions.reject && <Button danger onClick={() => setDialog({ kind: 'reject' })}>{t('version.action.reject')}</Button>}
        {actions.sign && <Button type="primary" onClick={() => setDialog({ kind: 'sign' })}>{t('version.action.recordClientSigning')}</Button>}
        {actions.schedule && <Button type="primary" onClick={() => setDialog({ kind: 'schedule' })}>{t('version.action.schedule')}</Button>}
        {actions.activate && <Button onClick={() => setDialog({ kind: 'activate' })}>{t('version.action.activateNow')}</Button>}
        {actions.copy && <Button onClick={() => setDialog({ kind: 'copy' })}>{t('version.action.newVersion')}</Button>}
      </div>
    </div>
    <dl className="commercial-summary">
      <div><dt>{t('shared.column.status')}</dt><dd><StatusTag status={version.status} kind="versionStatus" />
        {signature && <Tag color={signatureStateColors[signature]}>{enumLabel(t, 'enum.signatureState', signature)}</Tag>}</dd></div>
      <div><dt>{t('version.field.versionPlan')}</dt><dd className="mono">{version.versionId} / {version.planId}</dd></div>
      <div><dt>{t('version.field.agreement')}</dt><dd><Button type="link" size="small" onClick={() => void navigate(commercialRoutes.agreement(agreement.agreementId))}>
        {agreement.agreementNo}</Button> <StatusTag status={agreement.status} kind="agreementStatus" /></dd></div>
      <div><dt>{partner ? 'Local Partner' : 'Client'}</dt><dd className="mono">{agreement.localPartnerId ?? agreement.clientId ?? '—'}</dd></div>
      <div><dt>{t('version.field.priceableOe')}</dt><dd>{joinList(agreement.entityNos) || '—'}</dd></div>
      <div><dt>{t('shared.column.effective')}</dt><dd>{['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'CLIENT_SIGNED'].includes(version.status) ? t('version.field.setOnSchedule')
        : t('shared.period', { from: localTime(version.effectiveFrom), to: version.effectiveTo ? localTime(version.effectiveTo) : t('shared.openEnded') })}</dd></div>
      <div><dt>{t('shared.column.basis')}</dt><dd>{versionBasisLabel(version.pricingModel, version.feeTaxTreatment)} · {version.roundingMode}</dd></div>
      <div><dt>{t('version.field.source')}</dt><dd>{version.sourceTemplateVersionId ? <Tag color="geekblue">{templateLabel ?? t('source.standardTemplate')}</Tag> : '—'}</dd></div>
      <div><dt>{t('benchmark.below')}</dt><dd>{belowStandard > 0 ? <Tag color="red">{t('benchmark.belowCount', { count: belowStandard })}</Tag> : '—'}</dd></div>
      {version.acceptance && <div><dt>{t('version.field.clientSigning')}</dt><dd><Tooltip title={t('version.field.acceptanceFile', { fileId: version.acceptance.fileId,
        hash: version.acceptance.contentHash.slice(0, 16) })}>
        <span className="mono">{version.acceptance.acceptanceReference}</span></Tooltip> · {localTime(version.acceptance.acceptedAt)}</dd></div>}
      <div><dt>{t('version.field.token')}</dt><dd>{version.changeVersion}</dd></div>
    </dl>
    {version.templateUpdate && <Alert type="info" showIcon className="commercial-banner"
      title={t('version.templateUpdated', { versionNo: version.templateUpdate.latestVersionNo })}
      description={t('version.templateUpdatedHint')} />}
    {!partner && warnings.length > 0 && <Alert type="warning" showIcon className="commercial-banner"
      title={t('version.relevanceWarning', { count: warnings.length })}
      description={<ul className="cc-warning-list">{warnings.map(item => <li key={`${item.entityNo}|${item.currency}|${item.pricingProfileCode}|${item.reasonCode}`}>
        {relevanceText(item)}</li>)}</ul>} />}
    {!partner && gaps.length > 0 && <Alert type="warning" showIcon className="commercial-banner" title={t('version.coverageGaps')}
      description={joinList(gaps, 'clauses')} />}
    {actions.schedule === false && version.status === 'APPROVED' && signature === 'PENDING'
      && <CommercialNote tone="warning">{t('version.unsignedNote')}</CommercialNote>}
    <div className="commercial-section-head"><h2>{t('version.matrix.title')}</h2><Tag>{t('matrix.priceCount', { count: prices.length })}</Tag>
      <Tag>{t('version.matrix.eventCount', { count: eventCount })}</Tag></div>
    <PriceMatrixView prices={prices} settlement={!partner} templateLabel={templateLabel}
      grouping={partner ? undefined : { capabilities: agreement.capabilities, catalog: catalog.items, warnings, definitions }}
      emptyText={actions.editPrices ? t('version.matrix.emptyEditableWithFill') : t('version.matrix.empty')} />
    <CommercialNote>{t('version.matrix.note')}</CommercialNote>
    {dialog?.kind === 'edit' && <PriceEditorDrawer version={version} entityNos={agreement.entityNos} prices={prices} onClose={close}
      grouping={partner ? undefined : { capabilities: agreement.capabilities, catalog: catalog.items }}
      onSaved={result => { setSavedWarnings(result?.relevanceWarnings ?? []); done(); }} onAuthorizationChanged={onAuthorizationChanged} />}
    {(dialog?.kind === 'submit' || dialog?.kind === 'approve' || dialog?.kind === 'reject' || dialog?.kind === 'activate')
      && <VersionTransitionModal version={version} action={dialog.kind} onDone={done} {...base} />}
    {dialog?.kind === 'schedule' && <ScheduleModal version={version} onDone={done} {...base} />}
    {dialog?.kind === 'sign' && version.agreementId && <SignatureModal version={{ ...version, agreementId: version.agreementId }} onDone={done} {...base} />}
    {dialog?.kind === 'fill' && <FillFromTemplateModal version={version} entityNos={agreement.entityNos}
      onDone={result => { setSavedWarnings(result.relevanceWarnings); done(); }} {...base} />}
    {dialog?.kind === 'copy' && <CreateVersionModal planId={version.planId} termType={version.termType} expectedChangeVersion={version.changeVersion}
      agreementId={version.agreementId} copyFromVersionId={version.versionId} entityNos={agreement.entityNos} onClose={close}
      onAuthorizationChanged={onAuthorizationChanged} onCreated={created => { setDialog(null); void navigate(commercialRoutes.version(created.versionId)); }} />}
  </div>;
}

/** 来源模板显示名「模板名 vN」：读取模板版本（复用版本读，带模板方案摘要）；失败时返回 null（页面显示「标准价目」）。 */
function useTemplateSourceLabel(sourceTemplateVersionId: string | null): string | null {
  const t = useT('commercial');
  const [label, setLabel] = useState<string | null>(null);
  useEffect(() => {
    setLabel(null);
    if (!sourceTemplateVersionId) return undefined;
    const controller = new AbortController();
    const signal = controller.signal;
    void (async () => {
      try {
        const read = await session.request(commercialPaths.version(sourceTemplateVersionId), { signal }, decodeVersionWorkspace(sourceTemplateVersionId));
        const name = read.kind === 'TEMPLATE' ? read.workspace.template?.templateName : null;
        if (!signal.aborted) setLabel(`${name ?? t('source.standardTemplate')} v${read.workspace.version.versionNo}`);
      } catch { /* 来源名称只是辅助信息，读取失败不影响编辑器 */ }
    })();
    return () => controller.abort();
  // t 随语言包刷新换新，只用于名称兜底，不应触发重新读取
  }, [sourceTemplateVersionId]);
  return label;
}

/** 与同方案的另一个版本对比：较小版本号为基准；读取方案版本列表失败时不显示。 */
export function CompareWith({ versionId, planId }: { versionId: string; planId: string }) {
  const t = useT('commercial');
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
  return <Select className="commercial-compare-select" placeholder={t('compare.placeholder')} value={null} onChange={compare}
    options={others.map(item => ({ value: item.versionId, label: t('compare.option', { versionNo: item.versionNo, status: enumLabel(t, 'enum.versionStatus', item.status) }) }))} />;
}

