import { useEffect, useMemo, useState } from 'react';
import { Button, Form, Input, Modal, Select, Table, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { useT } from '../i18n/react';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  canCreateVersion, channelButtons, channelPaths, channelRoutes, componentCount, componentSummary, costVersionActions, costVersionStatusLabel,
  decodeChannel, decodeCostVersion, decodePlan, decodePlanPage, defaultCompareBase, hasButton, isId, isPlanCode, listFilters, localTime, planCodePattern,
  planStatuses, planStatusLabel, shortHash,
} from '../channel/channel';
import type { Channel, CostPlan, CostPlanSummary, CostScope, CostVersion, CostVersionSummary } from '../channel/channel';
import { ChangeList, ChannelNote, ChannelSelect, CommandFeedback, CommandModal, StatusTag, Summary } from '../channel/ChannelParts';
import { CostVersionDiff } from '../channel/CostVersionDiff';
import { CostVersionEditor } from '../channel/CostVersionEditor';
import { useChannelCommand } from '../channel/useChannelCommand';
import '../configuration/configuration.css';
import '../channel/channel.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface Base { onClose: () => void; onAuthorizationChanged: () => void }

/**
 * 渠道成本：/channel/cost-plans 方案列表；/:planCode 方案详情与版本列表；/:planCode/versions/new 版本编制；
 * /:planCode/versions/:costVer 版本详情，带 ?compareWith=基准版本 时为版本对比。
 */
export function ChannelCostPlans(props: Props) {
  const location = useLocation();
  const parts = location.pathname.replace(/\/$/, '').split('/').slice(3);
  const compareWith = new URLSearchParams(location.search).get('compareWith') ?? undefined;
  const notFound = <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  let content;
  if (parts.length === 0) content = <PlanList {...props} />;
  else if (!isPlanCode(parts[0])) content = notFound;
  else if (parts.length === 1) content = <PlanDetail key={parts[0]} planCode={parts[0]} {...props} />;
  else if (parts.length === 3 && parts[1] === 'versions' && parts[2] === 'new') content = <NewVersion key={parts[0]} planCode={parts[0]} {...props} />;
  else if (parts.length === 3 && parts[1] === 'versions' && isId(parts[2])) {
    content = compareWith !== undefined
      ? (isId(compareWith) && compareWith !== parts[2] ? <CostVersionDiff key={`${compareWith}-${parts[2]}`} planCode={parts[0]} baseCostVer={compareWith} targetCostVer={parts[2]} /> : notFound)
      : <VersionDetail key={`${parts[0]}-${parts[2]}`} planCode={parts[0]} costVer={parts[2]} {...props} />;
  } else content = notFound;
  return <div className="configuration-page channel-page">{content}</div>;
}

interface FilterValues { channelId?: string; status?: string }

/** 方案列表（画板 09）：渠道与方案状态筛选；每个渠道一个方案。 */
function PlanList({ resources, onAuthorizationChanged }: Props) {
  const t = useT('channel'), tNav = useT('nav');
  const navigate = useNavigate();
  const [form] = Form.useForm<FilterValues>();
  const [creating, setCreating] = useState(false);
  const page = usePaging<CostPlanSummary>(channelPaths.costPlans, decodePlanPage, {}, { notifyOnError: false });
  const canManage = hasButton(resources, channelButtons.costManage);
  return <>
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_CHANNEL_COST_PLANS')}</h1></div>
    <div className="channel-toolbar">
      <Form form={form} name="channel-cost-filter" layout="vertical" onFinish={(values: FilterValues) => page.applyFilters(listFilters({ ...values }))}>
        <div className="channel-filters">
          <Form.Item name="channelId" label={t('filter.channel')}><ChannelSelect channelType="PAYMENT" /></Form.Item>
          <Form.Item name="status" label={t('cost.planStatus')}><Select allowClear placeholder={t('filter.all')}
            options={planStatuses.map(value => ({ value, label: `${value} · ${planStatusLabel(value)}` }))} /></Form.Item>
          <div className="channel-filter-actions"><Button type="primary" htmlType="submit">{t('changes.search')}</Button>
            <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('filter.reset')}</Button></div>
        </div>
      </Form>
      {canManage && <Button type="primary" onClick={() => setCreating(true)}>{t('cost.createPlan')}</Button>}</div>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
      : <Table<CostPlanSummary> rowKey="planId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
        scroll={{ x: 1100 }} locale={{ emptyText: t('cost.noMatch') }} columns={[
          { title: t('cost.planCode'), width: 180, render: (_, row) => <Button type="link" size="small" onClick={() => void navigate(channelRoutes.costPlan(row.planCode))}>
            {row.planCode}</Button> },
          { title: t('filter.channel'), width: 240, render: (_, row) => `${row.channelCode} · ${row.channelName}` },
          { title: t('grants.status'), width: 110, render: (_, row) => <StatusTag status={row.status} /> },
          { title: t('cost.currentVersion'), width: 90, render: (_, row) => row.currentCostVer ? `v${row.currentCostVer}` : '—' },
          { title: t('cost.currentEffective'), width: 150, render: (_, row) => row.currentEffectiveFrom ? t('cost.from', { time: localTime(row.currentEffectiveFrom) }) : '—' },
          { title: t('enum.costVersionStatus.PENDING_APPROVAL'), width: 80, dataIndex: 'pendingApprovalCount' },
          { title: t('channels.updatedAt'), width: 150, render: (_, row) => localTime(row.updateTime) },
          { title: t('grants.actions'), width: 160, render: (_, row) => <span className="channel-actions">
            <Button type="link" size="small" onClick={() => void navigate(channelRoutes.costPlan(row.planCode))}>{t('protocol.details')}</Button>
            {canManage && <Button type="link" size="small" onClick={() => void navigate(channelRoutes.newCostVersion(row.planCode))}>{t('cost.createVersion')}</Button>}</span> },
        ]} />}
    {creating && <CreatePlanModal onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={plan => void navigate(channelRoutes.costPlan(plan.planCode))} />}
  </>;
}

/** 新建方案：渠道 + 方案代码；方案初始 DRAFT、无当前版本。 */
function CreatePlanModal({ onCreated, ...base }: Base & { onCreated: (plan: CostPlan) => void }) {
  const t = useT('channel');
  const [form] = Form.useForm<{ channelId: string; planCode: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const submit = async () => {
    const values = await form.validateFields();
    const plan = await command.run(channelPaths.costPlans, 'POST', values, decodePlan(values.planCode), t('cost.createPlanFailed'));
    if (plan) onCreated(plan);
  };
  return <CommandModal title={t('cost.createPlanTitle')} command={command} okText={t('channels.createOk')} onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <Form form={form} name="channel-cost-plan-create" layout="vertical">
      <Form.Item name="channelId" label={t('filter.channel')} rules={[{ required: true, message: t('mids.channelRequired') }]}><ChannelSelect allowClear={false} placeholder={t('mids.selectChannel')} channelType="PAYMENT" /></Form.Item>
      <Form.Item name="planCode" label={t('cost.planCode')} normalize={(v?: string) => v?.trim().toUpperCase()}
        rules={[{ required: true, message: t('form.required') }, { pattern: planCodePattern, message: t('cost.planCodePattern') }]}>
        <Input maxLength={64} placeholder="PAYERMAX_STD" /></Form.Item>
    </Form>
  </CommandModal>;
}

/** 读取方案所属渠道（显示编码与名称）。 */
function usePlanChannel(channelId: string | undefined) {
  const [channel, setChannel] = useState<Channel>();
  useEffect(() => {
    if (!channelId) return;
    const controller = new AbortController();
    session.request(channelPaths.channel(channelId), { signal: controller.signal }, value => decodeChannel(value, channelId)).then(setChannel).catch(() => undefined);
    return () => controller.abort();
  }, [channelId]);
  return channel;
}

type ReviewTarget = { version: CostVersionSummary; mode: 'review' | 'reject' | 'cancel' } | null;

/** 方案详情（画板 10）：信息条与版本列表；批准 / 驳回只给审批权限，作废只出现在未到期的 APPROVED 行。 */
function PlanDetail({ planCode, resources, onAuthorizationChanged }: Props & { planCode: string }) {
  const t = useT('channel');
  const navigate = useNavigate();
  const detail = useDetail(channelPaths.costPlan(planCode), useMemo(() => decodePlan(planCode), [planCode]));
  const channel = usePlanChannel(detail.data?.channelId);
  const [review, setReview] = useState<ReviewTarget>(null);
  const permissions = { canManage: hasButton(resources, channelButtons.costManage), canApprove: hasButton(resources, channelButtons.costApprove) };
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const plan = detail.data;
  const current = plan.versions.find(v => v.costVer === plan.currentCostVer);
  const now = Date.now();
  return <>
    <div className="page-heading">
      <div className="channel-title"><h1>{plan.planCode}</h1><StatusTag status={plan.status} /></div>
      <div className="channel-actions">
        {canCreateVersion(plan, permissions.canManage) && <Button type="primary" onClick={() => void navigate(channelRoutes.newCostVersion(planCode))}>{t('cost.createVersion')}</Button>}
        <Button onClick={() => void navigate(channelRoutes.costPlans)}>{t('channels.backToList')}</Button></div>
    </div>
    <Summary items={[
      { label: t('filter.channel'), value: <Button type="link" size="small" onClick={() => void navigate(channelRoutes.channel(plan.channelId))}>
        {channel ? `${channel.channelCode} · ${channel.channelName}` : plan.channelId}</Button> },
      { label: t('cost.currentVersion'), value: current ? t('cost.versionFrom', { version: current.costVer, time: localTime(current.effectiveFrom) }) : '—' },
      { label: t('enum.costVersionStatus.PENDING_APPROVAL'), value: plan.versions.filter(v => v.status === 'PENDING_APPROVAL').length },
      { label: t('cost.versionCount'), value: plan.versions.length },
    ]} />
    <Table<CostVersionSummary> rowKey="costVer" size="middle" pagination={false} dataSource={plan.versions} scroll={{ x: 1100 }}
      locale={{ emptyText: t('cost.versionsEmpty') }} columns={[
        { title: t('grants.version'), width: 70, render: (_, row) => `v${row.costVer}` },
        { title: t('grants.status'), width: 150, render: (_, row) => <StatusTag status={row.status} /> },
        { title: t('cost.effectiveFrom'), width: 150, render: (_, row) => localTime(row.effectiveFrom) },
        { title: t('cost.scopesComponents'), width: 100, render: (_, row) => `${row.scopeCount ?? '—'} / ${row.componentCount ?? '—'}` },
        { title: t('cost.maker'), width: 110, render: (_, row) => <span className="mono">{row.makerId}</span> },
        { title: t('cost.checker'), width: 110, render: (_, row) => <span className="mono">{row.checkerId ?? '—'}</span> },
        { title: t('cost.contentHash'), width: 120, render: (_, row) => <span className="mono" title={row.contentHash}>{shortHash(row.contentHash)}</span> },
        { title: t('grants.actions'), width: 260, render: (_, row) => {
          const actions = costVersionActions(row, permissions, now);
          const base = defaultCompareBase(plan, row.costVer);
          return <span className="channel-actions">
            <Button type="link" size="small" onClick={() => void navigate(channelRoutes.costVersion(planCode, row.costVer))}>{t('protocol.details')}</Button>
            {actions.approve && <Button type="link" size="small" onClick={() => setReview({ version: row, mode: 'review' })}>{t('cost.approve')}</Button>}
            {actions.reject && <Button type="link" size="small" onClick={() => setReview({ version: row, mode: 'reject' })}>{t('cost.reject')}</Button>}
            {base && <Button type="link" size="small" onClick={() => void navigate(channelRoutes.costCompare(planCode, base, row.costVer))}>{t('cost.compareWith', { version: base })}</Button>}
            {actions.cancel && <Button type="link" size="small" danger onClick={() => setReview({ version: row, mode: 'cancel' })}>{t('cost.void')}</Button>}
          </span>;
        } },
      ]} />
    <ChannelNote>{t('cost.lifecycleNote')}</ChannelNote>
    {review && review.mode !== 'cancel' && <ReviewModal planCode={planCode} version={review.version} focus={review.mode} onClose={() => setReview(null)}
      onAuthorizationChanged={onAuthorizationChanged} onDone={() => { setReview(null); detail.refresh(); }} />}
    {review?.mode === 'cancel' && <CancelModal planCode={planCode} version={review.version} onClose={() => setReview(null)}
      onAuthorizationChanged={onAuthorizationChanged} onDone={() => { setReview(null); detail.refresh(); }} />}
  </>;
}

/** 批准 / 驳回确认（画板 14）：展示生效时间、编制人与内容摘要；完整 contentHash 随请求提交，与 Owner 保存值不一致时拒绝。 */
function ReviewModal({ planCode, version, focus, onDone, ...base }: Base & { planCode: string; version: CostVersionSummary; focus: 'review' | 'reject'; onDone: () => void }) {
  const t = useT('channel'), tCommon = useT('common');
  const command = useChannelCommand(base.onAuthorizationChanged);
  const run = async (action: 'approve' | 'reject') => {
    const result = await command.run(channelPaths.costVersionAction(planCode, version.costVer, action), 'POST', { approvedContentHash: version.contentHash },
      decodeCostVersion(planCode, version.costVer), action === 'approve' ? t('cost.approveFailed') : t('cost.rejectFailed'));
    if (result) onDone();
  };
  return <CommandModal title={t(focus === 'reject' ? 'cost.rejectTitle' : 'cost.approveTitle', { version: version.costVer, planCode })} command={command} onOk={() => undefined}
    onClose={base.onClose} onReload={base.onClose} footer={[
      <Button key="cancel" disabled={command.saving} onClick={base.onClose}>{tCommon('action.cancel')}</Button>,
      <Button key="reject" danger={focus === 'reject'} type={focus === 'reject' ? 'primary' : 'default'} loading={command.saving}
        disabled={command.conflict} onClick={() => void run('reject')}>{t('cost.reject')}</Button>,
      ...(focus === 'review' ? [<Button key="approve" type="primary" loading={command.saving} disabled={command.conflict} onClick={() => void run('approve')}>{t('cost.approve')}</Button>] : []),
    ]}>
    <Summary items={[
      { label: t('cost.effectiveFrom'), value: t('cost.effectiveLocalUtc', { local: localTime(version.effectiveFrom), utc: version.effectiveFrom }) },
      { label: t('cost.maker'), value: <span className="mono">{version.makerId}</span> },
      { label: t('cost.checker'), value: t('cost.currentOperator') },
      { label: t('cost.contentHash'), value: <span className="mono">{version.contentHash}</span> },
    ]} />
    <ChannelNote>{t('cost.reviewNote')}</ChannelNote>
  </CommandModal>;
}

/** 作废确认（画板 15）：仅未到期 APPROVED 可作废，终态 INACTIVE；原因必填并写入变更记录。 */
function CancelModal({ planCode, version, onDone, ...base }: Base & { planCode: string; version: CostVersionSummary; onDone: () => void }) {
  const t = useT('channel');
  const [form] = Form.useForm<{ reason: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const submit = async () => {
    const values = await form.validateFields();
    const result = await command.run(channelPaths.costVersionAction(planCode, version.costVer, 'cancel'), 'POST', { reason: values.reason.trim() },
      decodeCostVersion(planCode, version.costVer), t('cost.voidFailed'));
    if (result) onDone();
  };
  return <CommandModal title={t('cost.voidTitle', { version: version.costVer, planCode })} command={command} okText={t('cost.voidOk')} danger onOk={() => void submit()}
    onClose={base.onClose} onReload={base.onClose}>
    <ChannelNote tone="warning">{t('cost.voidNote', { time: localTime(version.effectiveFrom) })}</ChannelNote>
    <Form form={form} name="channel-cost-cancel" layout="vertical">
      <Form.Item name="reason" label={t('cost.voidReason')} rules={[{ required: true, whitespace: true, message: t('form.required') }, { max: 512, message: t('form.maxLength', { max: 512 }) }]}>
        <Input.TextArea rows={3} maxLength={512} showCount /></Form.Item>
    </Form>
  </CommandModal>;
}

/** 版本编制入口：读取方案与当前版本（作为初稿来源）后展示编制器。 */
function NewVersion({ planCode, onAuthorizationChanged }: Props & { planCode: string }) {
  const navigate = useNavigate();
  const detail = useDetail(channelPaths.costPlan(planCode), useMemo(() => decodePlan(planCode), [planCode]));
  const [source, setSource] = useState<CostVersion | null>();
  const currentCostVer = detail.data?.currentCostVer;
  useEffect(() => {
    if (detail.data === undefined) return;
    if (!currentCostVer) { setSource(null); return; }
    const controller = new AbortController();
    session.request(channelPaths.costVersion(planCode, currentCostVer), { signal: controller.signal }, decodeCostVersion(planCode, currentCostVer))
      .then(setSource).catch(() => { if (!controller.signal.aborted) setSource(null); });
    return () => controller.abort();
  }, [detail.data, currentCostVer, planCode]);
  if ((detail.loading && !detail.data) || (detail.data && source === undefined)) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  return <CostVersionEditor planCode={planCode} channelId={detail.data.channelId} source={source ?? undefined} onAuthorizationChanged={onAuthorizationChanged}
    onCancel={() => void navigate(channelRoutes.costPlan(planCode))} onSaved={version => void navigate(channelRoutes.costVersion(planCode, version.costVer))} />;
}

/** 版本详情：范围与组成；按状态显示提交 / 批准 / 驳回 / 作废，以及与默认基准的对比入口。 */
function VersionDetail({ planCode, costVer, resources, onAuthorizationChanged }: Props & { planCode: string; costVer: string }) {
  const t = useT('channel'), tCommon = useT('common');
  const navigate = useNavigate();
  const detail = useDetail(channelPaths.costVersion(planCode, costVer), useMemo(() => decodeCostVersion(planCode, costVer), [planCode, costVer]));
  const planDetail = useDetail(channelPaths.costPlan(planCode), useMemo(() => decodePlan(planCode), [planCode]));
  const command = useChannelCommand(onAuthorizationChanged);
  const [modal, setModal] = useState<'review' | 'reject' | 'cancel' | 'submit' | null>(null);
  const [compareBase, setCompareBase] = useState<string>();
  const permissions = { canManage: hasButton(resources, channelButtons.costManage), canApprove: hasButton(resources, channelButtons.costApprove) };
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const version = detail.data;
  const actions = costVersionActions(version, permissions, Date.now());
  const others = planDetail.data?.versions.filter(v => v.costVer !== costVer) ?? [];
  const base = compareBase ?? (planDetail.data ? defaultCompareBase(planDetail.data, costVer) ?? undefined : undefined);
  const done = () => { setModal(null); detail.refresh(); planDetail.refresh(); };
  const submit = async () => {
    const result = await command.run(channelPaths.costVersionAction(planCode, costVer, 'submit'), 'POST', undefined, decodeCostVersion(planCode, costVer), t('cost.submitFailed'));
    if (result) done();
  };
  return <>
    <div className="page-heading">
      <div className="channel-title"><h1>{planCode} · v{version.costVer}</h1><StatusTag status={version.status} /></div>
      <div className="channel-actions">
        {actions.submit && <Button type="primary" onClick={() => setModal('submit')}>{t('cost.submitForApproval')}</Button>}
        {actions.approve && <Button type="primary" onClick={() => setModal('review')}>{t('cost.approve')}</Button>}
        {actions.reject && <Button onClick={() => setModal('reject')}>{t('cost.reject')}</Button>}
        {actions.cancel && <Button danger onClick={() => setModal('cancel')}>{t('cost.void')}</Button>}
        <Button onClick={() => void navigate(channelRoutes.costPlan(planCode))}>{t('diff.backToPlan')}</Button></div>
    </div>
    <CommandFeedback command={command} onReload={done} />
    <Summary items={[
      { label: t('grants.status'), value: costVersionStatusLabel(version.status) },
      { label: t('cost.effectiveFrom'), value: localTime(version.effectiveFrom) },
      { label: t('cost.effectiveTo'), value: localTime(version.effectiveTo) },
      { label: t('cost.scopesComponents'), value: `${version.scopes.length} / ${componentCount(version)}` },
      { label: t('cost.maker'), value: <span className="mono">{version.makerId}</span> },
      { label: t('cost.checker'), value: <span className="mono">{version.checkerId ?? '—'}</span> },
      { label: t('cost.contentHash'), value: <span className="mono" title={version.contentHash}>{shortHash(version.contentHash)}</span> },
    ]} />
    {others.length > 0 && <div className="channel-section-head"><h2>{t('cost.scopesAndComponents')}</h2><span className="channel-spacer" />
      <Select aria-label={t('cost.compareBase')} style={{ minWidth: 180 }} value={base} placeholder={t('cost.selectCompareBase')} onChange={setCompareBase}
        options={others.map(v => ({ value: v.costVer, label: t('cost.baseOption', { version: v.costVer, status: v.status }) }))} />
      <Button disabled={!base} onClick={() => base && void navigate(channelRoutes.costCompare(planCode, base, costVer))}>{t('cost.compare')}</Button></div>}
    <Table<CostScope> rowKey="scopeNo" size="middle" pagination={false} dataSource={version.scopes} scroll={{ x: 1100 }}
      expandable={{ defaultExpandAllRows: true, expandedRowRender: scope => <Table rowKey="componentCode" size="small" pagination={false} dataSource={scope.components}
        columns={[
          { title: t('costEditor.field.componentCode'), dataIndex: 'componentCode', width: 140, render: (code: string) => <span className="mono">{code}</span> },
          { title: t('capability.type'), dataIndex: 'componentType', width: 150 },
          { title: t('costEditor.field.calculationBase'), dataIndex: 'calculationBase', width: 170 },
          { title: t('cost.cost'), render: (_, component) => componentSummary(component) },
        ]} /> }} columns={[
        { title: t('cost.scope'), width: 70, render: (_, scope) => `#${scope.scopeNo}` },
        { title: 'MID', width: 180, render: (_, scope) => <Button type="link" size="small" onClick={() => void navigate(channelRoutes.mid(scope.midId))}>{scope.midId}</Button> },
        { title: 'OE', width: 80, dataIndex: 'entityNo' },
        { title: t('costEditor.country'), width: 70, dataIndex: 'countryCode' },
        { title: t('costEditor.currency'), width: 70, dataIndex: 'currency' },
        { title: t('capability.paymentMethod'), width: 110, dataIndex: 'paymentMethodCode' },
        { title: t('costEditor.action'), width: 100, dataIndex: 'actionType' },
        { title: t('costEditor.agreementRef'), width: 160, render: (_, scope) => scope.channelAgreementRef ?? '—' },
        { title: t('cost.componentCount'), width: 80, render: (_, scope) => <Tag>{scope.components.length}</Tag> },
      ]} />
    <div className="channel-section-head"><h2>{t('workspace.tab.changes')}</h2></div>
    <ChangeList targetType="CHANNEL_COST_VERSION" targetId={version.costVerId} />
    {modal === 'submit' && <Modal open title={t('cost.submitTitle', { version: version.costVer })} okText={t('cost.submitOk')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving}
      onOk={() => void submit().then(() => setModal(null))} onCancel={() => setModal(null)}>
      <ChannelNote>{t('cost.submitNote')}</ChannelNote></Modal>}
    {(modal === 'review' || modal === 'reject') && <ReviewModal planCode={planCode} version={version} focus={modal} onClose={() => setModal(null)}
      onAuthorizationChanged={onAuthorizationChanged} onDone={done} />}
    {modal === 'cancel' && <CancelModal planCode={planCode} version={version} onClose={() => setModal(null)} onAuthorizationChanged={onAuthorizationChanged} onDone={done} />}
  </>;
}
