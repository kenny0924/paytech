import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Empty, Form, Input, InputNumber, Modal, Space, Spin, Table, Tag, Tooltip } from 'antd';
import { session } from '../api/session';
import { RefreshButton } from '../components/RefreshButton';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { accessLevelLabels, scanPolicyCodePattern, sourceSystemLabel } from './fileStorage';
import {
  activationNotice, byteSizeLabel, decodePolicyMutation, bytesToMegabytes, createPolicyBody, decodeScanPolicyDetail, mimeTypeLabel, newRevisionBlocker, policyPath,
  policyStatusLabels, retireBlocker, revisionPath, revisionSlots, revisionStatusLabels, scanPolicyPath, scanTypeLabels, unsupportedMimeTypes,
  updateRevisionBody, versionedReasonBody,
} from './scanPolicy';
import type { PolicyFormValues, ScanPolicyDetail, ScanPolicyList, ScanPolicyRevision, ScanPolicySummary } from './scanPolicy';
import { useStorageCommand } from './useStorageCommand';

const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
const reasonItem = <Form.Item name="reason" label="原因" rules={[{ required: true, whitespace: true, message: '请填写原因' }]}>
  <Input.TextArea rows={2} maxLength={500} showCount /></Form.Item>;
const conflictBanner = (conflict: boolean) => conflict
  ? <Alert type="error" showIcon title="该策略已被他人修改，请关闭后刷新再操作。" style={{ marginTop: 12 }} /> : null;
const statusColors: Readonly<Record<string, string>> = { ACTIVE: 'green', DRAFT: 'gold', RETIRED: 'default' };

/** 允许类型标签；格式检查无法识别的类型标为红色。 */
export function MimeTags({ types, supported }: { types: readonly string[]; supported: readonly string[] }) {
  return <Space size={4} wrap>{types.map(type => <Tag key={type} color={supported.includes(type) ? 'default' : 'red'} title={type}>
    {mimeTypeLabel(type)}{supported.includes(type) ? '' : '（无法识别）'}</Tag>)}</Space>;
}

/** 允许类型复选与最大大小；可识别类型之外的已有类型只读列出，保存前须去掉。 */
function RevisionFields({ supported, current }: { supported: readonly string[]; current: readonly string[] }) {
  const unknown = unsupportedMimeTypes(current, supported);
  return <>
    <Form.Item name="allowedMimeTypes" label="允许类型" rules={[{ required: true, type: 'array', min: 1, message: '至少选择一种允许类型' }]}
      extra={unknown.length ? `${unknown.join('、')} 无法被格式检查识别，保存时会被去掉` : undefined}>
      <Checkbox.Group options={supported.map(type => ({ value: type, label: mimeTypeLabel(type) }))} /></Form.Item>
    <Form.Item name="maxSizeMegabytes" label="最大大小（MB）" extra="1 字节到 100 MB" rules={[{ required: true, message: '请填写最大大小' }]}>
      <InputNumber min={0.01} max={100} precision={2} style={{ width: 200 }} /></Form.Item>
  </>;
}

interface CommandProps { onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void }

/** 新建策略：同时建修订 r1（草稿），激活后才能被写入规则选择。 */
function CreatePolicyModal({ supported, onClose, onDone, onAuthorizationChanged }: CommandProps & { supported: readonly string[] }) {
  const [form] = Form.useForm<PolicyFormValues>(); const command = useStorageCommand(onAuthorizationChanged, form);
  async function submit() {
    const values = await form.validateFields();
    const code = (values.policyCode ?? '').trim();
    const result = await command.run(scanPolicyPath, 'POST', createPolicyBody(values, supported), value => decodePolicyMutation(value, code, 1), '新建策略失败');
    if (result) onDone();
  }
  return <Modal open title="新建检查策略" okText="创建" cancelText="取消" confirmLoading={command.saving} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    <div className="notification-note">新建后为草稿修订 r1，激活后写入规则才能选择；策略代码创建后不可修改。</div>
    <Form form={form} layout="vertical" disabled={command.saving} initialValues={{ allowedMimeTypes: [], maxSizeMegabytes: 10, reason: '' }}>
      <Form.Item name="policyCode" label="策略代码" rules={[{ required: true, whitespace: true, message: '请填写策略代码' },
        { pattern: scanPolicyCodePattern, message: '大写字母开头，只能包含大写字母、数字或下划线' }]}>
        <Input maxLength={64} className="mono" /></Form.Item>
      <RevisionFields supported={supported} current={[]} />
      {reasonItem}
    </Form>
    {conflictBanner(command.conflict)}
  </Modal>;
}

/** 只要原因的确认弹窗：新建修订、废弃草稿、激活与停用。 */
function ReasonModal({ title, okText, danger = false, notice, path, method, body, code, revisionNo, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  title: string; okText: string; danger?: boolean; notice?: string; path: string; method: 'POST' | 'DELETE'; body: (reason: string) => unknown; code: string;
  revisionNo?: number;
}) {
  const [form] = Form.useForm<{ reason: string }>(); const command = useStorageCommand(onAuthorizationChanged, form);
  async function submit() {
    const { reason } = await form.validateFields();
    const result = await command.run(path, method, body(reason), value => decodePolicyMutation(value, code, revisionNo), `${title}失败`);
    if (result) onDone();
  }
  return <Modal open title={title} okText={okText} cancelText="取消" confirmLoading={command.saving}
    okButtonProps={{ danger, disabled: command.conflict }} onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden>
    {notice && <Alert type="info" showIcon title={notice} style={{ marginBottom: 16 }} />}
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict}>{reasonItem}</Form>
    {conflictBanner(command.conflict)}
  </Modal>;
}

/** 时间线中的草稿：就地编辑允许类型与大小，保存、废弃或激活。 */
function DraftEditor({ detail, draft, supported, canManage, onChanged, onAuthorizationChanged }: {
  detail: ScanPolicyDetail; draft: ScanPolicyRevision; supported: readonly string[]; canManage: boolean; onChanged: () => void;
  onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<PolicyFormValues>(); const command = useStorageCommand(onAuthorizationChanged, form);
  const [confirm, setConfirm] = useState<'discard' | 'activate' | null>(null);
  const active = revisionSlots(detail).active;
  async function save() {
    const values = await form.validateFields();
    const result = await command.run(revisionPath(detail.policyCode, draft.revisionNo), 'PUT', updateRevisionBody(values, draft, supported),
      value => decodePolicyMutation(value, detail.policyCode, draft.revisionNo), '保存草稿失败');
    if (result) onChanged();
  }
  const megabytes = Form.useWatch('maxSizeMegabytes', form) as number | undefined;
  return <div className="scan-policy-draft">
    <Form form={form} layout="vertical" disabled={!canManage || command.saving || command.conflict} initialValues={{
      allowedMimeTypes: draft.allowedMimeTypes.filter(type => supported.includes(type)), maxSizeMegabytes: bytesToMegabytes(draft.maxSizeBytes), reason: '' }}>
      <RevisionFields supported={supported} current={draft.allowedMimeTypes} />
      {active && megabytes !== undefined && <div className="muted scan-policy-compare">与 r{active.revisionNo} 相比：{byteSizeLabel(active.maxSizeBytes)} → {megabytes} MB</div>}
      {canManage && reasonItem}
    </Form>
    {canManage && <Space className="scan-policy-draft-actions">
      <Button danger disabled={command.saving} onClick={() => setConfirm('discard')}>废弃草稿</Button>
      <Button loading={command.saving} disabled={command.conflict} onClick={() => void save()}>保存草稿</Button>
      <Button type="primary" disabled={command.saving} onClick={() => setConfirm('activate')}>激活 r{draft.revisionNo}</Button>
    </Space>}
    {canManage && <div className="muted">激活使用草稿当前已保存的内容；有未保存的修改请先保存。</div>}
    {conflictBanner(command.conflict)}
    {confirm === 'discard' && <ReasonModal title={`废弃草稿 r${draft.revisionNo}`} okText="确认废弃" danger path={revisionPath(detail.policyCode, draft.revisionNo)}
      method="DELETE" body={reason => versionedReasonBody(draft, reason)} code={detail.policyCode} revisionNo={draft.revisionNo} notice="草稿从未被上传引用，废弃后不可恢复。"
      onClose={() => setConfirm(null)} onDone={() => { setConfirm(null); onChanged(); }} onAuthorizationChanged={onAuthorizationChanged} />}
    {confirm === 'activate' && <ReasonModal title={`激活 ${detail.policyCode} r${draft.revisionNo}`} okText="确认激活"
      path={`${revisionPath(detail.policyCode, draft.revisionNo)}/activation`} method="POST" body={reason => versionedReasonBody(draft, reason)}
      code={detail.policyCode} revisionNo={draft.revisionNo} notice={activationNotice(detail, draft)}
      onClose={() => setConfirm(null)} onDone={() => { setConfirm(null); onChanged(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 右侧策略详情：修订时间线（倒序）、只读检查要求与引用的写入规则。 */
function PolicyDetail({ summary, supported, canManage, onChanged, onAuthorizationChanged }: {
  summary: ScanPolicySummary; supported: readonly string[]; canManage: boolean; onChanged: () => void; onAuthorizationChanged: () => void;
}) {
  const notify = useErrorNotification();
  const [detail, setDetail] = useState<ScanPolicyDetail>(); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  const [dialog, setDialog] = useState<'revision' | 'retire' | null>(null);
  useEffect(() => {
    const abort = new AbortController(); setDetail(undefined); setFailed(false);
    session.request(policyPath(summary.policyCode), { signal: abort.signal }, value => decodeScanPolicyDetail(value, summary.policyCode))
      .then(value => { if (!abort.signal.aborted) setDetail(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, '策略详情加载失败'); } });
    return () => abort.abort();
  }, [summary.policyCode, summary.updateTime, reload, notify]);
  const changed = () => { setDialog(null); setReload(value => value + 1); onChanged(); };
  if (failed) return <Alert type="warning" showIcon title="策略详情暂时无法加载" action={<Button onClick={() => setReload(v => v + 1)}>重试</Button>} />;
  if (!detail) return <div className="content-loading"><Spin /></div>;
  const { active, draft } = revisionSlots(detail);
  const revisionBlocker = newRevisionBlocker(detail); const retire = retireBlocker(detail);
  const requirements = (active ?? draft ?? detail.revisions[0])?.requirements ?? [];
  return <div className="scan-policy-detail">
    <div className="scan-policy-detail-head"><div><div className="mono scan-policy-title">{detail.policyCode}</div>
      <div className="muted">被 {detail.routes.length} 条写入规则引用</div></div>
      {canManage && <Space>
        <Tooltip title={revisionBlocker}><Button disabled={!!revisionBlocker} onClick={() => setDialog('revision')}>新建修订</Button></Tooltip>
        <Tooltip title={retire}><Button danger disabled={!!retire} onClick={() => setDialog('retire')}>停用</Button></Tooltip>
      </Space>}</div>
    {(draft || detail.routes.length > 0) && <div className="notification-note">
      {draft ? `已有草稿 r${draft.revisionNo}，先激活或废弃后才能新建修订；` : ''}被写入规则引用时不能停用。</div>}
    <h3 className="scan-policy-subtitle">修订</h3>
    <div className="scan-policy-timeline">{detail.revisions.map(revision => <div key={revision.revisionNo} className={`scan-policy-revision is-${revision.status.toLowerCase()}`}>
      <div className="scan-policy-revision-head"><strong>r{revision.revisionNo}</strong>
        <Tag color={statusColors[revision.status]}>{revisionStatusLabels[revision.status]}{revision.status === 'DRAFT' && canManage ? ' · 编辑中' : ''}</Tag>
        <span className="muted">创建 {date(revision.createTime)} · 更新 {date(revision.updateTime)}</span></div>
      {revision.status === 'DRAFT'
        ? <DraftEditor key={`${revision.revisionNo}:${revision.version}`} detail={detail} draft={revision} supported={supported} canManage={canManage}
          onChanged={changed} onAuthorizationChanged={onAuthorizationChanged} />
        : <div className="scan-policy-revision-body"><MimeTags types={revision.allowedMimeTypes} supported={supported} />
          <span>最大 {byteSizeLabel(revision.maxSizeBytes)}</span></div>}
    </div>)}</div>
    <h3 className="scan-policy-subtitle">检查要求（只读）</h3>
    <Table rowKey={row => `${row.scannerCode}/${row.scanType}`} size="small" pagination={false} dataSource={requirements} columns={[
      { title: '检查器', render: (_, row) => <span className="mono">{row.scannerCode}</span> },
      { title: '类型', render: (_, row) => scanTypeLabels[row.scanType] ?? row.scanType },
      { title: '最低版本', dataIndex: 'minimumScannerVersion', width: 90 },
      { title: '通过结果', render: (_, row) => <span className="mono">{row.acceptedResultCodes.join('、')}</span> },
    ]} />
    <h3 className="scan-policy-subtitle">引用的写入规则</h3>
    {detail.routes.length ? <div className="scan-policy-routes">{detail.routes.map(route => <div key={`${route.sourceSystem}/${route.accessLevel}`}>
      <span className="mono">{sourceSystemLabel(route.sourceSystem)}</span> <Tag>{accessLevelLabels[route.accessLevel]}</Tag>
      <span className="muted">→ {route.instanceCode} / {route.bucketName}</span></div>)}</div>
      : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有写入规则引用该策略" />}
    {dialog === 'revision' && active && <ReasonModal title={`新建修订 · ${detail.policyCode}`} okText="新建" path={`${policyPath(detail.policyCode)}/revisions`}
      method="POST" body={reason => ({ reason: reason.trim() })} code={detail.policyCode}
      notice={`以生效修订 r${active.revisionNo} 为模板创建草稿，激活前不影响上传。`}
      onClose={() => setDialog(null)} onDone={changed} onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog === 'retire' && active && <ReasonModal title={`停用 ${detail.policyCode}`} okText="确认停用" danger
      path={`${policyPath(detail.policyCode)}/retirement`} method="POST" body={reason => versionedReasonBody(active, reason)} code={detail.policyCode}
      notice={`停用生效修订 r${active.revisionNo}，之后写入规则不能再选择该策略；已开始的上传仍按原修订检查。`}
      onClose={() => setDialog(null)} onDone={changed} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/**
 * 存储管理「检查策略」页签：左侧策略列表，右侧选中策略的修订时间线
 * <li>展示内容取生效修订，没有生效修订时取草稿；含格式检查无法识别的类型时提示。</li>
 * <li>没有维护按钮时只读。</li>
 */
export function ScanPolicyTab({ policies, loading, failed, refresh, canManage, onAuthorizationChanged }: {
  policies: ScanPolicyList; loading: boolean; failed: boolean; refresh: () => void; canManage: boolean; onAuthorizationChanged: () => void;
}) {
  const [selected, setSelected] = useState<string>(); const [creating, setCreating] = useState(false);
  const supported = policies.supportedMimeTypes;
  const current = policies.items.find(item => item.policyCode === selected) ?? policies.items[0];
  return <div className="scan-policy-layout">
    <section className="table-panel">
      <div className="table-toolbar"><span className="table-count"><span>共 {policies.items.length} 个策略</span>
        <RefreshButton onClick={refresh} loading={loading} failed={failed} /></span>
        {canManage && <Button type="primary" disabled={!supported.length} onClick={() => setCreating(true)}>＋ 新建策略</Button>}</div>
      <Table<ScanPolicySummary> rowKey="policyCode" tableLayout="fixed" dataSource={policies.items} loading={loading} pagination={false} scroll={{ x: 760 }}
        rowClassName={row => row.policyCode === current?.policyCode ? 'is-selected clickable-row' : 'clickable-row'}
        onRow={row => ({ onClick: () => setSelected(row.policyCode) })} columns={[
          { title: '策略代码', width: 240, ellipsis: true, render: (_, row) => <span className="mono">{row.policyCode}</span> },
          { title: '当前修订', width: 100, render: (_, row) => <div>{row.activeRevisionNo ? `r${row.activeRevisionNo}` : '—'}
            {row.draftRevisionNo && <div className="cell-meta">草稿 r{row.draftRevisionNo}</div>}</div> },
          { title: '允许类型', width: 200, render: (_, row) => <div><MimeTags types={row.allowedMimeTypes} supported={supported} />
            {unsupportedMimeTypes(row.allowedMimeTypes, supported).length > 0 && <div className="notification-missing">⚠ 含格式检查无法识别的类型</div>}</div> },
          { title: '最大大小', width: 90, render: (_, row) => byteSizeLabel(row.maxSizeBytes) },
          { title: '引用规则', width: 80, render: (_, row) => `${row.routeCount} 条` },
          { title: '状态', width: 80, render: (_, row) => <Tag color={statusColors[row.status]}>{policyStatusLabels[row.status]}</Tag> },
        ]} />
      <div className="notification-note">格式检查可识别：{supported.map(mimeTypeLabel).join('、') || '—'}。选择其他类型的文件将无法通过检查。
        同一策略最多一个生效修订和一个草稿；已开始的上传按开始时的修订检查。</div>
    </section>
    <section className="scan-policy-side">{current
      ? <PolicyDetail key={current.policyCode} summary={current} supported={supported} canManage={canManage} onChanged={refresh}
        onAuthorizationChanged={onAuthorizationChanged} />
      : <Empty description={loading ? '加载中' : '还没有检查策略'} />}</section>
    {creating && <CreatePolicyModal supported={supported} onClose={() => setCreating(false)}
      onDone={() => { setCreating(false); refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
