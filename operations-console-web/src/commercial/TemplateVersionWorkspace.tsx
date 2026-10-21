import { useEffect, useMemo, useState } from 'react';
import { Button, Select, Tag } from 'antd';
import { Navigate, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  commercialButtons, commercialPaths, commercialRoutes, hasButton, localTime, priceMatrix, versionBasisLabel, versionStatusLabels,
} from './commercial';
import type { VersionAction } from './commercial';
import { catalogLabel, catalogTermTypeLabels } from './catalogTree';
import { CommercialNote, StatusTag } from './CommercialParts';
import { consolePaths, consoleRoutes, decodeTermVersionPage } from './commercialConsole';
import type { TermVersionListItem } from './commercialConsole';
import { PriceEditorDrawer } from './PriceEditorDrawer';
import { PriceMatrixView } from './PriceMatrixView';
import { CreateVersionModal, ScheduleModal, VersionTransitionModal } from './TermModals';
import { decodeVersionWorkspace, templatePaths, templateRoutes } from './templates';
import { termVersionActions } from './versionFlow';

interface Props { versionId: string; resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Dialog = { kind: Exclude<VersionAction, 'schedule'> } | { kind: 'schedule' | 'edit' | 'copy' };

/**
 * 标准价目版本工作区：复用价格矩阵与编辑价格抽屉（组件表格），没有合同信息与签署步骤。
 * <li>维护（标准价目维护）：编辑价格、提交、排期、激活、新建版本；复核（标准价目复核）：批准 / 驳回，Maker 与 Checker 不同人由 commercial 校验。</li>
 * <li>读取复用版本读（ownerType=PLATFORM_TEMPLATE），全部写命令走模板专用路由；模板只做结构校验，不参与运行时取价。</li>
 * <li>版本读返回合同价目版本时转到价目版本编辑器。</li>
 */
export function TemplateVersionWorkspace({ versionId, resources, onAuthorizationChanged }: Props) {
  const navigate = useNavigate();
  const decode = useMemo(() => decodeVersionWorkspace(versionId), [versionId]);
  const read = useDetail(commercialPaths.version(versionId), decode);
  const detail = { ...read, data: read.data?.kind === 'TEMPLATE' ? read.data.workspace : undefined };
  const versions = usePlanVersions(detail.data?.version.planId, detail.data?.version.changeVersion);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  if (read.data?.kind === 'AGREEMENT') return <Navigate replace to={commercialRoutes.version(versionId)} />;
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const { version, template: plan, prices } = detail.data;
  const actions = termVersionActions(version, { canManage: hasButton(resources, commercialButtons.templateManage),
    canApprove: hasButton(resources, commercialButtons.templateApprove), canSign: false }, true);
  const entityNos = plan?.entityNo ? [plan.entityNo] : [...new Set(prices.map(price => price.entityNo))];
  const close = () => setDialog(null);
  const done = () => { setDialog(null); detail.refresh(); };
  const base = { onClose: close, onReload: done, onAuthorizationChanged };
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>{plan?.templateName ?? '标准价目'} · v{version.versionNo}</h1>
      <div className="commercial-actions">
        <Button onClick={() => void navigate(templateRoutes.list)}>返回</Button>
        <Button onClick={detail.refresh} loading={detail.loading}>刷新</Button>
        {versions.length > 1 && <Select className="commercial-compare-select" value={version.versionId}
          onChange={(next: string) => void navigate(templateRoutes.version(next))}
          options={versions.map(item => ({ value: item.versionId, label: `v${item.versionNo}（${versionStatusLabels[item.status]}）` }))} />}
        <Button onClick={() => void navigate(consoleRoutes.history('COMMERCIAL_TERM_PLAN', version.planId))}>变更历史</Button>
        {actions.editPrices && <Button onClick={() => setDialog({ kind: 'edit' })}>编辑价格</Button>}
        {actions.submit && <Button type="primary" disabled={prices.length === 0} onClick={() => setDialog({ kind: 'submit' })}>提交复核</Button>}
        {actions.approve && <Button type="primary" onClick={() => setDialog({ kind: 'approve' })}>复核通过</Button>}
        {actions.reject && <Button danger onClick={() => setDialog({ kind: 'reject' })}>驳回</Button>}
        {actions.schedule && <Button type="primary" onClick={() => setDialog({ kind: 'schedule' })}>排期</Button>}
        {actions.activate && <Button onClick={() => setDialog({ kind: 'activate' })}>立即激活</Button>}
        {actions.copy && <Button onClick={() => setDialog({ kind: 'copy' })}>新建版本</Button>}
      </div>
    </div>
    <dl className="commercial-summary">
      <div><dt>状态</dt><dd><StatusTag status={version.status} labels={versionStatusLabels} /></dd></div>
      <div><dt>模板编号</dt><dd className="mono">{plan?.planCode ?? version.planId}</dd></div>
      <div><dt>条款类型</dt><dd>{catalogLabel(catalogTermTypeLabels, version.termType)}</dd></div>
      <div><dt>OE</dt><dd>{entityNos.join('、') || '—'}</dd></div>
      <div><dt>生效</dt><dd>{['DRAFT', 'PENDING_APPROVAL', 'APPROVED'].includes(version.status) ? '排期时确定'
        : `${localTime(version.effectiveFrom)} ～ ${version.effectiveTo ? localTime(version.effectiveTo) : '未定'}`}</dd></div>
      <div><dt>口径</dt><dd>{versionBasisLabel(version.pricingModel, version.feeTaxTreatment)} · {version.roundingMode}</dd></div>
      <div><dt>版本令牌</dt><dd>{version.changeVersion}</dd></div>
    </dl>
    <div className="commercial-section-head"><h2>价格矩阵</h2><Tag>{prices.length} 条价格</Tag><Tag>{priceMatrix(prices).events.length} 个计费事件</Tag></div>
    <PriceMatrixView prices={prices} settlement templateLabel={null}
      emptyText={actions.editPrices ? '还没有价格，点击「编辑价格」添加' : '该版本没有价格'} />
    <CommercialNote>标准价目覆盖平台可售的全部支付方式与功能，不受合同开通约束；合同新版本「基于标准价目」生成时只取合同已开通的部分。模板出新版本后，引用旧版本的合同显示「标准价已更新」，合同价格不变。</CommercialNote>
    {dialog?.kind === 'edit' && <PriceEditorDrawer version={version} entityNos={entityNos} prices={prices} savePath={templatePaths.prices(version.versionId)}
      onClose={close} onSaved={done} onAuthorizationChanged={onAuthorizationChanged} />}
    {(dialog?.kind === 'submit' || dialog?.kind === 'approve' || dialog?.kind === 'reject' || dialog?.kind === 'activate')
      && <VersionTransitionModal version={version} action={dialog.kind} path={templatePaths.versionAction(version.versionId, dialog.kind)} onDone={done} {...base} />}
    {dialog?.kind === 'schedule' && <ScheduleModal version={version} template path={templatePaths.versionAction(version.versionId, 'schedule')} onDone={done}
      {...base} />}
    {dialog?.kind === 'copy' && <CreateVersionModal template planId={version.planId} termType={version.termType} expectedChangeVersion={version.changeVersion}
      copyFromVersionId={version.versionId} onClose={close} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={created => { setDialog(null); void navigate(templateRoutes.version(created.versionId)); }} />}
  </div>;
}

/** 同模板方案的版本（切换用）；版本令牌变化后重新读取。 */
function usePlanVersions(planId: string | undefined, revision: string | undefined): TermVersionListItem[] {
  const [versions, setVersions] = useState<TermVersionListItem[]>([]);
  useEffect(() => {
    if (!planId) return undefined;
    const controller = new AbortController();
    session.request(`${consolePaths.versions}?planId=${planId}&page=1&size=50`, { signal: controller.signal }, decodeTermVersionPage)
      .then(page => { if (!controller.signal.aborted) setVersions(page.items); })
      .catch(() => { if (!controller.signal.aborted) setVersions([]); });
    return () => controller.abort();
  }, [planId, revision]);
  return versions;
}
