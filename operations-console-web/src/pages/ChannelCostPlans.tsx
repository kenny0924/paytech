import { useEffect, useMemo, useState } from 'react';
import { Button, Form, Input, Modal, Select, Table, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  canCreateVersion, channelButtons, channelPaths, channelRoutes, componentCount, componentSummary, costVersionActions, costVersionStatusLabels,
  decodeChannel, decodeCostVersion, decodePlan, decodePlanPage, defaultCompareBase, hasButton, isId, isPlanCode, listFilters, localTime, planCodePattern,
  planStatuses, planStatusLabels, shortHash,
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
  const navigate = useNavigate();
  const [form] = Form.useForm<FilterValues>();
  const [creating, setCreating] = useState(false);
  const page = usePaging<CostPlanSummary>(channelPaths.costPlans, decodePlanPage, {}, { notifyOnError: false });
  const canManage = hasButton(resources, channelButtons.costManage);
  return <>
    <div className="page-heading"><h1>渠道成本</h1></div>
    <div className="channel-toolbar">
      <Form form={form} name="channel-cost-filter" layout="vertical" onFinish={(values: FilterValues) => page.applyFilters(listFilters({ ...values }))}>
        <div className="channel-filters">
          <Form.Item name="channelId" label="渠道"><ChannelSelect /></Form.Item>
          <Form.Item name="status" label="方案状态"><Select allowClear placeholder="全部"
            options={planStatuses.map(value => ({ value, label: `${value} · ${planStatusLabels[value]}` }))} /></Form.Item>
          <div className="channel-filter-actions"><Button type="primary" htmlType="submit">查询</Button>
            <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button></div>
        </div>
      </Form>
      {canManage && <Button type="primary" onClick={() => setCreating(true)}>新建方案</Button>}</div>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
      : <Table<CostPlanSummary> rowKey="planId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
        scroll={{ x: 1100 }} locale={{ emptyText: '没有符合条件的方案' }} columns={[
          { title: '方案代码', width: 180, render: (_, row) => <Button type="link" size="small" onClick={() => void navigate(channelRoutes.costPlan(row.planCode))}>
            {row.planCode}</Button> },
          { title: '渠道', width: 240, render: (_, row) => `${row.channelCode} · ${row.channelName}` },
          { title: '状态', width: 110, render: (_, row) => <StatusTag status={row.status} /> },
          { title: '当前版本', width: 90, render: (_, row) => row.currentCostVer ? `v${row.currentCostVer}` : '—' },
          { title: '当前生效', width: 150, render: (_, row) => row.currentEffectiveFrom ? `${localTime(row.currentEffectiveFrom)} 起` : '—' },
          { title: '待审批', width: 80, dataIndex: 'pendingApprovalCount' },
          { title: '更新时间', width: 150, render: (_, row) => localTime(row.updateTime) },
          { title: '操作', width: 160, render: (_, row) => <span className="channel-actions">
            <Button type="link" size="small" onClick={() => void navigate(channelRoutes.costPlan(row.planCode))}>详情</Button>
            {canManage && <Button type="link" size="small" onClick={() => void navigate(channelRoutes.newCostVersion(row.planCode))}>新建版本</Button>}</span> },
        ]} />}
    <ChannelNote>每个渠道只有一个方案；创建方案时校验渠道存在。「待审批」为 PENDING_APPROVAL 版本数；审批人需要「审批」按钮权限，且不能是版本编制人。</ChannelNote>
    {creating && <CreatePlanModal onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={plan => void navigate(channelRoutes.costPlan(plan.planCode))} />}
  </>;
}

/** 新建方案：渠道 + 方案代码；方案初始 DRAFT、无当前版本。 */
function CreatePlanModal({ onCreated, ...base }: Base & { onCreated: (plan: CostPlan) => void }) {
  const [form] = Form.useForm<{ channelId: string; planCode: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const submit = async () => {
    const values = await form.validateFields();
    const plan = await command.run(channelPaths.costPlans, 'POST', values, decodePlan(values.planCode), '方案创建失败');
    if (plan) onCreated(plan);
  };
  return <CommandModal title="新建渠道成本方案" command={command} okText="创建" onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <Form form={form} name="channel-cost-plan-create" layout="vertical">
      <Form.Item name="channelId" label="渠道" rules={[{ required: true, message: '请选择渠道' }]}><ChannelSelect allowClear={false} placeholder="选择渠道" /></Form.Item>
      <Form.Item name="planCode" label="方案代码" normalize={(v?: string) => v?.trim().toUpperCase()}
        rules={[{ required: true, message: '必填' }, { pattern: planCodePattern, message: '大写字母开头，大写字母、数字或下划线，最长 64' }]}>
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
        {canCreateVersion(plan, permissions.canManage) && <Button type="primary" onClick={() => void navigate(channelRoutes.newCostVersion(planCode))}>新建版本</Button>}
        <Button onClick={() => void navigate(channelRoutes.costPlans)}>返回列表</Button></div>
    </div>
    <Summary items={[
      { label: '渠道', value: <Button type="link" size="small" onClick={() => void navigate(channelRoutes.channel(plan.channelId))}>
        {channel ? `${channel.channelCode} · ${channel.channelName}` : plan.channelId}</Button> },
      { label: '当前版本', value: current ? `v${current.costVer} · ${localTime(current.effectiveFrom)} 起` : '—' },
      { label: '待审批', value: plan.versions.filter(v => v.status === 'PENDING_APPROVAL').length },
      { label: '版本数', value: plan.versions.length },
    ]} />
    <Table<CostVersionSummary> rowKey="costVer" size="middle" pagination={false} dataSource={plan.versions} scroll={{ x: 1100 }}
      locale={{ emptyText: '还没有版本' }} columns={[
        { title: '版本', width: 70, render: (_, row) => `v${row.costVer}` },
        { title: '状态', width: 150, render: (_, row) => <StatusTag status={row.status} /> },
        { title: '生效时间', width: 150, render: (_, row) => localTime(row.effectiveFrom) },
        { title: '范围 / 组成', width: 100, render: (_, row) => `${row.scopeCount ?? '—'} / ${row.componentCount ?? '—'}` },
        { title: '编制人', width: 110, render: (_, row) => <span className="mono">{row.makerId}</span> },
        { title: '复核人', width: 110, render: (_, row) => <span className="mono">{row.checkerId ?? '—'}</span> },
        { title: '内容摘要', width: 120, render: (_, row) => <span className="mono" title={row.contentHash}>{shortHash(row.contentHash)}</span> },
        { title: '操作', width: 260, render: (_, row) => {
          const actions = costVersionActions(row, permissions, now);
          const base = defaultCompareBase(plan, row.costVer);
          return <span className="channel-actions">
            <Button type="link" size="small" onClick={() => void navigate(channelRoutes.costVersion(planCode, row.costVer))}>详情</Button>
            {actions.approve && <Button type="link" size="small" onClick={() => setReview({ version: row, mode: 'review' })}>批准</Button>}
            {actions.reject && <Button type="link" size="small" onClick={() => setReview({ version: row, mode: 'reject' })}>驳回</Button>}
            {base && <Button type="link" size="small" onClick={() => void navigate(channelRoutes.costCompare(planCode, base, row.costVer))}>对比 v{base}</Button>}
            {actions.cancel && <Button type="link" size="small" danger onClick={() => setReview({ version: row, mode: 'cancel' })}>作废</Button>}
          </span>;
        } },
      ]} />
    <ChannelNote>状态流转：DRAFT → 提交 PENDING_APPROVAL → 批准（到期即 ACTIVE，否则 APPROVED 等待到期 Job 切换）/ 驳回 REJECTED。只有未到生效时间的 APPROVED 版本可「作废」为 INACTIVE；ACTIVE 版本只能被新版本替换。批准人不能是编制人，批准时确认内容摘要。</ChannelNote>
    {review && review.mode !== 'cancel' && <ReviewModal planCode={planCode} version={review.version} focus={review.mode} onClose={() => setReview(null)}
      onAuthorizationChanged={onAuthorizationChanged} onDone={() => { setReview(null); detail.refresh(); }} />}
    {review?.mode === 'cancel' && <CancelModal planCode={planCode} version={review.version} onClose={() => setReview(null)}
      onAuthorizationChanged={onAuthorizationChanged} onDone={() => { setReview(null); detail.refresh(); }} />}
  </>;
}

/** 批准 / 驳回确认（画板 14）：展示生效时间、编制人与内容摘要；完整 contentHash 随请求提交，与 Owner 保存值不一致时拒绝。 */
function ReviewModal({ planCode, version, focus, onDone, ...base }: Base & { planCode: string; version: CostVersionSummary; focus: 'review' | 'reject'; onDone: () => void }) {
  const command = useChannelCommand(base.onAuthorizationChanged);
  const run = async (action: 'approve' | 'reject') => {
    const result = await command.run(channelPaths.costVersionAction(planCode, version.costVer, action), 'POST', { approvedContentHash: version.contentHash },
      decodeCostVersion(planCode, version.costVer), action === 'approve' ? '批准失败' : '驳回失败');
    if (result) onDone();
  };
  return <CommandModal title={`${focus === 'reject' ? '驳回' : '批准'}版本 v${version.costVer} · ${planCode}`} command={command} onOk={() => undefined}
    onClose={base.onClose} onReload={base.onClose} footer={[
      <Button key="cancel" disabled={command.saving} onClick={base.onClose}>取消</Button>,
      <Button key="reject" danger={focus === 'reject'} type={focus === 'reject' ? 'primary' : 'default'} loading={command.saving}
        disabled={command.conflict} onClick={() => void run('reject')}>驳回</Button>,
      ...(focus === 'review' ? [<Button key="approve" type="primary" loading={command.saving} disabled={command.conflict} onClick={() => void run('approve')}>批准</Button>] : []),
    ]}>
    <Summary items={[
      { label: '生效时间', value: `${localTime(version.effectiveFrom)}（本地）· UTC ${version.effectiveFrom}` },
      { label: '编制人', value: <span className="mono">{version.makerId}</span> },
      { label: '复核人', value: '当前登录操作者' },
      { label: '内容摘要', value: <span className="mono">{version.contentHash}</span> },
    ]} />
    <ChannelNote>批准人不能是编制人。生效时间已到时批准即切换为当前版本，否则进入 APPROVED 等待到期切换；驳回后版本为 REJECTED。</ChannelNote>
  </CommandModal>;
}

/** 作废确认（画板 15）：仅未到期 APPROVED 可作废，终态 INACTIVE；原因必填并写入变更记录。 */
function CancelModal({ planCode, version, onDone, ...base }: Base & { planCode: string; version: CostVersionSummary; onDone: () => void }) {
  const [form] = Form.useForm<{ reason: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const submit = async () => {
    const values = await form.validateFields();
    const result = await command.run(channelPaths.costVersionAction(planCode, version.costVer, 'cancel'), 'POST', { reason: values.reason.trim() },
      decodeCostVersion(planCode, version.costVer), '作废失败');
    if (result) onDone();
  };
  return <CommandModal title={`作废版本 v${version.costVer} · ${planCode}`} command={command} okText="确认作废" danger onOk={() => void submit()}
    onClose={base.onClose} onReload={base.onClose}>
    <ChannelNote tone="warning">只有未到生效时间（{localTime(version.effectiveFrom)}）的 APPROVED 版本可以作废；作废后为终态 INACTIVE，不参与任何时间点的成本命中。</ChannelNote>
    <Form form={form} name="channel-cost-cancel" layout="vertical">
      <Form.Item name="reason" label="作废原因" rules={[{ required: true, whitespace: true, message: '必填' }, { max: 512, message: '最长 512' }]}>
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
    const result = await command.run(channelPaths.costVersionAction(planCode, costVer, 'submit'), 'POST', undefined, decodeCostVersion(planCode, costVer), '提交失败');
    if (result) done();
  };
  return <>
    <div className="page-heading">
      <div className="channel-title"><h1>{planCode} · v{version.costVer}</h1><StatusTag status={version.status} /></div>
      <div className="channel-actions">
        {actions.submit && <Button type="primary" onClick={() => setModal('submit')}>提交审批</Button>}
        {actions.approve && <Button type="primary" onClick={() => setModal('review')}>批准</Button>}
        {actions.reject && <Button onClick={() => setModal('reject')}>驳回</Button>}
        {actions.cancel && <Button danger onClick={() => setModal('cancel')}>作废</Button>}
        <Button onClick={() => void navigate(channelRoutes.costPlan(planCode))}>返回方案</Button></div>
    </div>
    <CommandFeedback command={command} onReload={done} />
    <Summary items={[
      { label: '状态', value: costVersionStatusLabels[version.status] },
      { label: '生效时间', value: localTime(version.effectiveFrom) },
      { label: '失效时间', value: localTime(version.effectiveTo) },
      { label: '范围 / 组成', value: `${version.scopes.length} / ${componentCount(version)}` },
      { label: '编制人', value: <span className="mono">{version.makerId}</span> },
      { label: '复核人', value: <span className="mono">{version.checkerId ?? '—'}</span> },
      { label: '内容摘要', value: <span className="mono" title={version.contentHash}>{shortHash(version.contentHash)}</span> },
    ]} />
    {others.length > 0 && <div className="channel-section-head"><h2>范围与组成</h2><span className="channel-spacer" />
      <Select aria-label="对比基准" style={{ minWidth: 180 }} value={base} placeholder="选择对比基准" onChange={setCompareBase}
        options={others.map(v => ({ value: v.costVer, label: `基准 v${v.costVer} · ${v.status}` }))} />
      <Button disabled={!base} onClick={() => base && void navigate(channelRoutes.costCompare(planCode, base, costVer))}>版本对比</Button></div>}
    <Table<CostScope> rowKey="scopeNo" size="middle" pagination={false} dataSource={version.scopes} scroll={{ x: 1100 }}
      expandable={{ defaultExpandAllRows: true, expandedRowRender: scope => <Table rowKey="componentCode" size="small" pagination={false} dataSource={scope.components}
        columns={[
          { title: '组成代码', dataIndex: 'componentCode', width: 140, render: (code: string) => <span className="mono">{code}</span> },
          { title: '类型', dataIndex: 'componentType', width: 150 },
          { title: '计算基数', dataIndex: 'calculationBase', width: 170 },
          { title: '成本', render: (_, component) => componentSummary(component) },
        ]} /> }} columns={[
        { title: '范围', width: 70, render: (_, scope) => `#${scope.scopeNo}` },
        { title: 'MID', width: 180, render: (_, scope) => <Button type="link" size="small" onClick={() => void navigate(channelRoutes.mid(scope.midId))}>{scope.midId}</Button> },
        { title: 'OE', width: 80, dataIndex: 'entityNo' },
        { title: '国家', width: 70, dataIndex: 'countryCode' },
        { title: '币种', width: 70, dataIndex: 'currency' },
        { title: '支付方式', width: 110, dataIndex: 'paymentMethodCode' },
        { title: '动作', width: 100, dataIndex: 'actionType' },
        { title: '渠道合同引用', width: 160, render: (_, scope) => scope.channelAgreementRef ?? '—' },
        { title: '组成数', width: 80, render: (_, scope) => <Tag>{scope.components.length}</Tag> },
      ]} />
    <div className="channel-section-head"><h2>变更记录</h2></div>
    <ChangeList targetType="CHANNEL_COST_VERSION" targetId={version.costVerId} />
    {modal === 'submit' && <Modal open title={`提交审批 · v${version.costVer}`} okText="提交" cancelText="取消" confirmLoading={command.saving}
      onOk={() => void submit().then(() => setModal(null))} onCancel={() => setModal(null)}>
      <ChannelNote>提交后内容冻结进入待审批；你将记为编制人（Maker），批准人不能是你本人。</ChannelNote></Modal>}
    {(modal === 'review' || modal === 'reject') && <ReviewModal planCode={planCode} version={version} focus={modal} onClose={() => setModal(null)}
      onAuthorizationChanged={onAuthorizationChanged} onDone={done} />}
    {modal === 'cancel' && <CancelModal planCode={planCode} version={version} onClose={() => setModal(null)} onAuthorizationChanged={onAuthorizationChanged} onDone={done} />}
  </>;
}
