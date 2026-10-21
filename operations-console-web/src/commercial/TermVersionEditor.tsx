import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Select, Tag, Tooltip } from 'antd';
import { session } from '../api/session';
import { Navigate, useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  agreementStatusLabels, commercialButtons, commercialPaths, commercialRoutes, coverageGaps, hasButton, localTime, partnerTermTypes,
  priceMatrix, termTypeLabels, versionBasisLabel, versionStatusLabels,
} from './commercial';
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
import { signatureState, signatureStateColors, signatureStateLabels, termVersionActions } from './versionFlow';

interface Props { versionId: string; resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Dialog = { kind: Exclude<VersionAction, 'schedule'> } | { kind: 'schedule' | 'edit' | 'copy' | 'sign' | 'fill' };

/**
 * 条款版本编辑器：版本概要、来源与签署、价格矩阵（按开通项分组，来源 / 破标标记）、覆盖与相关性提示、状态按钮
 * <li>草稿可编辑价格、从标准价目补齐与提交（价目维护）；待审批可批准 / 驳回（价目审批）。</li>
 * <li>已批准后登记客户签署（签署权限），客户已签署或历史免签才能排期；已排期可手工激活；任何状态可新建版本（复制 / 基于标准价目 / 空白）。</li>
 * <li>版本读返回标准价目版本（ownerType=PLATFORM_TEMPLATE）时转到标准价目工作区。</li>
 */
export function TermVersionEditor({ versionId, resources, onAuthorizationChanged }: Props) {
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
    <div className="page-heading"><h1>{termTypeLabels[version.termType] ?? version.termType} · v{version.versionNo}</h1>
      <div className="commercial-actions">
        <Button onClick={() => void navigate(commercialRoutes.terms)}>返回</Button>
        <Button onClick={detail.refresh} loading={detail.loading}>刷新</Button>
        <CompareWith versionId={version.versionId} planId={version.planId} />
        <Button onClick={() => void navigate(consoleRoutes.history('COMMERCIAL_TERM_PLAN', version.planId))}>变更历史</Button>
        {actions.editPrices && <Button onClick={() => setDialog({ kind: 'edit' })}>编辑价格</Button>}
        {actions.fillFromTemplate && !partner && <Button onClick={() => setDialog({ kind: 'fill' })}>从标准价目补齐</Button>}
        {actions.submit && <Button type="primary" disabled={prices.length === 0} onClick={() => setDialog({ kind: 'submit' })}>提交审批</Button>}
        {actions.approve && <Button type="primary" onClick={() => setDialog({ kind: 'approve' })}>批准</Button>}
        {actions.reject && <Button danger onClick={() => setDialog({ kind: 'reject' })}>驳回</Button>}
        {actions.sign && <Button type="primary" onClick={() => setDialog({ kind: 'sign' })}>登记客户签署</Button>}
        {actions.schedule && <Button type="primary" onClick={() => setDialog({ kind: 'schedule' })}>排期</Button>}
        {actions.activate && <Button onClick={() => setDialog({ kind: 'activate' })}>立即激活</Button>}
        {actions.copy && <Button onClick={() => setDialog({ kind: 'copy' })}>新建版本</Button>}
      </div>
    </div>
    <dl className="commercial-summary">
      <div><dt>状态</dt><dd><StatusTag status={version.status} labels={versionStatusLabels} />
        {signature && <Tag color={signatureStateColors[signature]}>{signatureStateLabels[signature]}</Tag>}</dd></div>
      <div><dt>版本 / 方案</dt><dd className="mono">{version.versionId} / {version.planId}</dd></div>
      <div><dt>合同</dt><dd><Button type="link" size="small" onClick={() => void navigate(commercialRoutes.agreement(agreement.agreementId))}>
        {agreement.agreementNo}</Button> <StatusTag status={agreement.status} labels={agreementStatusLabels} /></dd></div>
      <div><dt>{partner ? 'Local Partner' : 'Client'}</dt><dd className="mono">{agreement.localPartnerId ?? agreement.clientId ?? '—'}</dd></div>
      <div><dt>可定价 OE</dt><dd>{agreement.entityNos.join('、') || '—'}</dd></div>
      <div><dt>生效</dt><dd>{['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'CLIENT_SIGNED'].includes(version.status) ? '排期时确定'
        : `${localTime(version.effectiveFrom)} ～ ${version.effectiveTo ? localTime(version.effectiveTo) : '未定'}`}</dd></div>
      <div><dt>口径</dt><dd>{versionBasisLabel(version.pricingModel, version.feeTaxTreatment)} · {version.roundingMode}</dd></div>
      <div><dt>来源</dt><dd>{version.sourceTemplateVersionId ? <Tag color="geekblue">{templateLabel ?? '标准价目'}</Tag> : '—'}</dd></div>
      <div><dt>破标</dt><dd>{belowStandard > 0 ? <Tag color="red">破标 {belowStandard}</Tag> : '—'}</dd></div>
      {version.acceptance && <div><dt>客户签署</dt><dd><Tooltip title={`文件 ${version.acceptance.fileId} · ${version.acceptance.contentHash.slice(0, 16)}…`}>
        <span className="mono">{version.acceptance.acceptanceReference}</span></Tooltip> · {localTime(version.acceptance.acceptedAt)}</dd></div>}
      <div><dt>版本令牌</dt><dd>{version.changeVersion}</dd></div>
    </dl>
    {version.templateUpdate && <Alert type="info" showIcon className="commercial-banner"
      title={`标准价已更新（v${version.templateUpdate.latestVersionNo}）`}
      description="来源标准价目已有更新版本生效；本版本价格不变。如需跟进，新建版本后「从标准价目补齐」或基于新模板版本生成。" />}
    {!partner && warnings.length > 0 && <Alert type="warning" showIcon className="commercial-banner"
      title={`${warnings.length} 条价格未对应已开通的开通项：可以保存，激活时将被拒绝`}
      description={<ul className="cc-warning-list">{warnings.map(item => <li key={`${item.entityNo}|${item.currency}|${item.pricingProfileCode}|${item.reasonCode}`}>
        {relevanceText(item)}</li>)}</ul>} />}
    {!partner && gaps.length > 0 && <Alert type="warning" showIcon className="commercial-banner" title="以下开通项币种还没有价格，排期时将被拒绝"
      description={gaps.join('；')} />}
    {actions.schedule === false && version.status === 'APPROVED' && signature === 'PENDING'
      && <CommercialNote tone="warning">价格协议尚未签署：登记客户签署后才能排期。</CommercialNote>}
    <div className="commercial-section-head"><h2>价格矩阵</h2><Tag>{prices.length} 条价格</Tag><Tag>{eventCount} 个计费事件</Tag></div>
    <PriceMatrixView prices={prices} settlement={!partner} templateLabel={templateLabel}
      grouping={partner ? undefined : { capabilities: agreement.capabilities, catalog: catalog.items, warnings, definitions }}
      emptyText={actions.editPrices ? '还没有价格，点击「编辑价格」或「从标准价目补齐」添加' : '该版本没有价格'} />
    <CommercialNote>价格按开通项分组，组内按 OE、币种、优先级排序；「破标」表示低于来源标准价目（悬停查看模板值），「无标准可比」表示该组件没有来源或模板没有对应项。分组为页面近似，最终以 commercial 相关性校验为准。</CommercialNote>
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
        if (!signal.aborted) setLabel(`${name ?? '标准价目'} v${read.workspace.version.versionNo}`);
      } catch { /* 来源名称只是辅助信息，读取失败不影响编辑器 */ }
    })();
    return () => controller.abort();
  }, [sourceTemplateVersionId]);
  return label;
}

/** 与同方案的另一个版本对比：较小版本号为基准；读取方案版本列表失败时不显示。 */
export function CompareWith({ versionId, planId }: { versionId: string; planId: string }) {
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
    options={others.map(item => ({ value: item.versionId, label: `对比 v${item.versionNo}（${versionStatusLabels[item.status]}）` }))} />;
}

