import { useEffect, useMemo, useState } from 'react';
import { Button, Select, Tag } from 'antd';
import { Navigate, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  commercialButtons, commercialPaths, commercialRoutes, hasButton, joinList, localTime, priceMatrix, versionBasisLabel,
} from './commercial';
import type { VersionAction } from './commercial';
import { catalogTermTypeLabel } from './catalogTree';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
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
  const t = useT('commercial'), tCommon = useT('common');
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
    <div className="page-heading"><h1>{plan?.templateName ?? t('source.standardTemplate')} · v{version.versionNo}</h1>
      <div className="commercial-actions">
        <Button onClick={() => void navigate(templateRoutes.list)}>{t('shared.back')}</Button>
        <Button onClick={detail.refresh} loading={detail.loading}>{tCommon('action.refresh')}</Button>
        {versions.length > 1 && <Select className="commercial-compare-select" value={version.versionId}
          onChange={(next: string) => void navigate(templateRoutes.version(next))}
          options={versions.map(item => ({ value: item.versionId, label: t('version.option', { versionNo: item.versionNo, status: enumLabel(t, 'enum.versionStatus', item.status) }) }))} />}
        <Button onClick={() => void navigate(consoleRoutes.history('COMMERCIAL_TERM_PLAN', version.planId))}>{t('shared.changeHistory')}</Button>
        {actions.editPrices && <Button onClick={() => setDialog({ kind: 'edit' })}>{t('version.action.editPrices')}</Button>}
        {actions.submit && <Button type="primary" disabled={prices.length === 0} onClick={() => setDialog({ kind: 'submit' })}>{t('template.action.submit')}</Button>}
        {actions.approve && <Button type="primary" onClick={() => setDialog({ kind: 'approve' })}>{t('template.action.approve')}</Button>}
        {actions.reject && <Button danger onClick={() => setDialog({ kind: 'reject' })}>{t('version.action.reject')}</Button>}
        {actions.schedule && <Button type="primary" onClick={() => setDialog({ kind: 'schedule' })}>{t('version.action.schedule')}</Button>}
        {actions.activate && <Button onClick={() => setDialog({ kind: 'activate' })}>{t('version.action.activateNow')}</Button>}
        {actions.copy && <Button onClick={() => setDialog({ kind: 'copy' })}>{t('version.action.newVersion')}</Button>}
      </div>
    </div>
    <dl className="commercial-summary">
      <div><dt>{t('shared.column.status')}</dt><dd><StatusTag status={version.status} kind="versionStatus" /></dd></div>
      <div><dt>{t('template.field.code')}</dt><dd className="mono">{plan?.planCode ?? version.planId}</dd></div>
      <div><dt>{t('shared.column.termType')}</dt><dd>{catalogTermTypeLabel(version.termType)}</dd></div>
      <div><dt>OE</dt><dd>{joinList(entityNos) || '—'}</dd></div>
      <div><dt>{t('shared.column.effective')}</dt><dd>{['DRAFT', 'PENDING_APPROVAL', 'APPROVED'].includes(version.status) ? t('version.field.setOnSchedule')
        : t('shared.period', { from: localTime(version.effectiveFrom), to: version.effectiveTo ? localTime(version.effectiveTo) : t('shared.openEnded') })}</dd></div>
      <div><dt>{t('shared.column.basis')}</dt><dd>{versionBasisLabel(version.pricingModel, version.feeTaxTreatment)} · {version.roundingMode}</dd></div>
      <div><dt>{t('version.field.token')}</dt><dd>{version.changeVersion}</dd></div>
    </dl>
    <div className="commercial-section-head"><h2>{t('version.matrix.title')}</h2><Tag>{t('matrix.priceCount', { count: prices.length })}</Tag>
      <Tag>{t('version.matrix.eventCount', { count: priceMatrix(prices).events.length })}</Tag></div>
    <PriceMatrixView prices={prices} settlement templateLabel={null}
      emptyText={actions.editPrices ? t('version.matrix.emptyEditable') : t('version.matrix.empty')} />
    <CommercialNote>{t('template.workspace.note')}</CommercialNote>
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
