import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Empty, Form, Input, InputNumber, Modal, Space, Spin, Table, Tag, Tooltip } from 'antd';
import { session } from '../api/session';
import { RefreshButton } from '../components/RefreshButton';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { formatDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import { accessLevelLabel, joinList, scanPolicyCodePattern, sourceSystemLabel } from './fileStorage';
import {
  activationNotice, byteSizeLabel, decodePolicyMutation, bytesToMegabytes, createPolicyBody, decodeScanPolicyDetail, mimeTypeLabel, newRevisionBlocker, policyPath,
  policyStatusLabel, retireBlocker, revisionPath, revisionSlots, revisionStatusLabel, scanPolicyPath, scanTypeLabel, unsupportedMimeTypes,
  updateRevisionBody, versionedReasonBody,
} from './scanPolicy';
import type { PolicyFormValues, ScanPolicyDetail, ScanPolicyList, ScanPolicyRevision, ScanPolicySummary } from './scanPolicy';
import { useStorageCommand } from './useStorageCommand';

/** 原因：必填，写入审计。 */
function ReasonItem() {
  const t = useT('file');
  return <Form.Item name="reason" label={t('common.reason')} rules={[{ required: true, whitespace: true, message: t('common.reasonRequired') }]}>
    <Input.TextArea rows={2} maxLength={500} showCount /></Form.Item>;
}
function ConflictBanner({ conflict }: { conflict: boolean }) {
  const t = useT('file');
  return conflict ? <Alert type="error" showIcon title={t('policy.conflict')} style={{ marginTop: 12 }} /> : null;
}
const statusColors: Readonly<Record<string, string>> = { ACTIVE: 'green', DRAFT: 'gold', RETIRED: 'default' };

/** 允许类型标签；格式检查无法识别的类型标为红色。 */
export function MimeTags({ types, supported }: { types: readonly string[]; supported: readonly string[] }) {
  const t = useT('file');
  return <Space size={4} wrap>{types.map(type => <Tag key={type} color={supported.includes(type) ? 'default' : 'red'} title={type}>
    {supported.includes(type) ? mimeTypeLabel(type) : t('policy.mimeUnrecognized', { type: mimeTypeLabel(type) })}</Tag>)}</Space>;
}

/** 允许类型复选与最大大小；可识别类型之外的已有类型只读列出，保存前须去掉。 */
function RevisionFields({ supported, current }: { supported: readonly string[]; current: readonly string[] }) {
  const unknown = unsupportedMimeTypes(current, supported); const t = useT('file');
  return <>
    <Form.Item name="allowedMimeTypes" label={t('policy.allowedTypes')} rules={[{ required: true, type: 'array', min: 1, message: t('policy.allowedTypesRequired') }]}
      extra={unknown.length ? t('policy.unknownTypesRemoved', { types: joinList(unknown) }) : undefined}>
      <Checkbox.Group options={supported.map(type => ({ value: type, label: mimeTypeLabel(type) }))} /></Form.Item>
    <Form.Item name="maxSizeMegabytes" label={t('policy.maxSizeMb')} extra={t('policy.maxSizeHint')} rules={[{ required: true, message: t('policy.maxSizeRequired') }]}>
      <InputNumber min={0.01} max={100} precision={2} style={{ width: 200 }} /></Form.Item>
  </>;
}

interface CommandProps { onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void }

/** 新建策略：同时建修订 r1（草稿），激活后才能被写入规则选择。 */
function CreatePolicyModal({ supported, onClose, onDone, onAuthorizationChanged }: CommandProps & { supported: readonly string[] }) {
  const [form] = Form.useForm<PolicyFormValues>(); const command = useStorageCommand(onAuthorizationChanged, form);
  const t = useT('file'), tCommon = useT('common');
  async function submit() {
    const values = await form.validateFields();
    const code = (values.policyCode ?? '').trim();
    const result = await command.run(scanPolicyPath, 'POST', createPolicyBody(values, supported), value => decodePolicyMutation(value, code, 1), t('policy.createFailed'));
    if (result) onDone();
  }
  return <Modal open title={t('policy.createTitle')} okText={t('policy.createOk')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    <div className="notification-note">{t('policy.createNote')}</div>
    <Form form={form} layout="vertical" disabled={command.saving} initialValues={{ allowedMimeTypes: [], maxSizeMegabytes: 10, reason: '' }}>
      <Form.Item name="policyCode" label={t('policy.code')} rules={[{ required: true, whitespace: true, message: t('policy.codeRequired') },
        { pattern: scanPolicyCodePattern, message: t('policy.codePattern') }]}>
        <Input maxLength={64} className="mono" /></Form.Item>
      <RevisionFields supported={supported} current={[]} />
      <ReasonItem />
    </Form>
    <ConflictBanner conflict={command.conflict} />
  </Modal>;
}

/** 只要原因的确认弹窗：新建修订、废弃草稿、激活与停用。 */
function ReasonModal({ title, failureTitle, okText, danger = false, notice, path, method, body, code, revisionNo, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  title: string; failureTitle: string; okText: string; danger?: boolean; notice?: string; path: string; method: 'POST' | 'DELETE'; body: (reason: string) => unknown; code: string;
  revisionNo?: number;
}) {
  const [form] = Form.useForm<{ reason: string }>(); const command = useStorageCommand(onAuthorizationChanged, form);
  const tCommon = useT('common');
  async function submit() {
    const { reason } = await form.validateFields();
    const result = await command.run(path, method, body(reason), value => decodePolicyMutation(value, code, revisionNo), failureTitle);
    if (result) onDone();
  }
  return <Modal open title={title} okText={okText} cancelText={tCommon('action.cancel')} confirmLoading={command.saving}
    okButtonProps={{ danger, disabled: command.conflict }} onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden>
    {notice && <Alert type="info" showIcon title={notice} style={{ marginBottom: 16 }} />}
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict}><ReasonItem /></Form>
    <ConflictBanner conflict={command.conflict} />
  </Modal>;
}

/** 时间线中的草稿：就地编辑允许类型与大小，保存、废弃或激活。 */
function DraftEditor({ detail, draft, supported, canManage, onChanged, onAuthorizationChanged }: {
  detail: ScanPolicyDetail; draft: ScanPolicyRevision; supported: readonly string[]; canManage: boolean; onChanged: () => void;
  onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<PolicyFormValues>(); const command = useStorageCommand(onAuthorizationChanged, form);
  const t = useT('file');
  const [confirm, setConfirm] = useState<'discard' | 'activate' | null>(null);
  const active = revisionSlots(detail).active;
  async function save() {
    const values = await form.validateFields();
    const result = await command.run(revisionPath(detail.policyCode, draft.revisionNo), 'PUT', updateRevisionBody(values, draft, supported),
      value => decodePolicyMutation(value, detail.policyCode, draft.revisionNo), t('policy.saveDraftFailed'));
    if (result) onChanged();
  }
  const megabytes = Form.useWatch('maxSizeMegabytes', form) as number | undefined;
  return <div className="scan-policy-draft">
    <Form form={form} layout="vertical" disabled={!canManage || command.saving || command.conflict} initialValues={{
      allowedMimeTypes: draft.allowedMimeTypes.filter(type => supported.includes(type)), maxSizeMegabytes: bytesToMegabytes(draft.maxSizeBytes), reason: '' }}>
      <RevisionFields supported={supported} current={draft.allowedMimeTypes} />
      {active && megabytes !== undefined && <div className="muted scan-policy-compare">{t('policy.compare', { revisionNo: active.revisionNo, before: byteSizeLabel(active.maxSizeBytes), after: megabytes })}</div>}
      {canManage && <ReasonItem />}
    </Form>
    {canManage && <Space className="scan-policy-draft-actions">
      <Button danger disabled={command.saving} onClick={() => setConfirm('discard')}>{t('policy.discardDraft')}</Button>
      <Button loading={command.saving} disabled={command.conflict} onClick={() => void save()}>{t('policy.saveDraft')}</Button>
      <Button type="primary" disabled={command.saving} onClick={() => setConfirm('activate')}>{t('policy.activate', { revisionNo: draft.revisionNo })}</Button>
    </Space>}
    {canManage && <div className="muted">{t('policy.activateNote')}</div>}
    <ConflictBanner conflict={command.conflict} />
    {confirm === 'discard' && <ReasonModal title={t('policy.discardTitle', { revisionNo: draft.revisionNo })}
      failureTitle={t('policy.discardFailed', { revisionNo: draft.revisionNo })} okText={t('policy.confirmDiscard')} danger path={revisionPath(detail.policyCode, draft.revisionNo)}
      method="DELETE" body={reason => versionedReasonBody(draft, reason)} code={detail.policyCode} revisionNo={draft.revisionNo} notice={t('policy.discardNotice')}
      onClose={() => setConfirm(null)} onDone={() => { setConfirm(null); onChanged(); }} onAuthorizationChanged={onAuthorizationChanged} />}
    {confirm === 'activate' && <ReasonModal title={t('policy.activateTitle', { code: detail.policyCode, revisionNo: draft.revisionNo })}
      failureTitle={t('policy.activateFailed', { code: detail.policyCode, revisionNo: draft.revisionNo })} okText={t('policy.confirmActivate')}
      path={`${revisionPath(detail.policyCode, draft.revisionNo)}/activation`} method="POST" body={reason => versionedReasonBody(draft, reason)}
      code={detail.policyCode} revisionNo={draft.revisionNo} notice={activationNotice(detail, draft)}
      onClose={() => setConfirm(null)} onDone={() => { setConfirm(null); onChanged(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 右侧策略详情：修订时间线（倒序）、只读检查要求与引用的写入规则。 */
function PolicyDetail({ summary, supported, canManage, onChanged, onAuthorizationChanged }: {
  summary: ScanPolicySummary; supported: readonly string[]; canManage: boolean; onChanged: () => void; onAuthorizationChanged: () => void;
}) {
  const notify = useErrorNotification(); const t = useT('file'), tCommon = useT('common');
  const [detail, setDetail] = useState<ScanPolicyDetail>(); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  const [dialog, setDialog] = useState<'revision' | 'retire' | null>(null);
  useEffect(() => {
    const abort = new AbortController(); setDetail(undefined); setFailed(false);
    session.request(policyPath(summary.policyCode), { signal: abort.signal }, value => decodeScanPolicyDetail(value, summary.policyCode))
      .then(value => { if (!abort.signal.aborted) setDetail(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, t('policy.detailLoadFailed')); } });
    return () => abort.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [summary.policyCode, summary.updateTime, reload, notify]);
  const changed = () => { setDialog(null); setReload(value => value + 1); onChanged(); };
  if (failed) return <Alert type="warning" showIcon title={t('policy.detailUnavailable')} action={<Button onClick={() => setReload(v => v + 1)}>{tCommon('action.retry')}</Button>} />;
  if (!detail) return <div className="content-loading"><Spin /></div>;
  const { active, draft } = revisionSlots(detail);
  const revisionBlocker = newRevisionBlocker(detail); const retire = retireBlocker(detail);
  const requirements = (active ?? draft ?? detail.revisions[0])?.requirements ?? [];
  return <div className="scan-policy-detail">
    <div className="scan-policy-detail-head"><div><div className="mono scan-policy-title">{detail.policyCode}</div>
      <div className="muted">{t('policy.referencedBy', { count: detail.routes.length })}</div></div>
      {canManage && <Space>
        <Tooltip title={revisionBlocker}><Button disabled={!!revisionBlocker} onClick={() => setDialog('revision')}>{t('policy.newRevision')}</Button></Tooltip>
        <Tooltip title={retire}><Button danger disabled={!!retire} onClick={() => setDialog('retire')}>{t('policy.retire')}</Button></Tooltip>
      </Space>}</div>
    {(draft || detail.routes.length > 0) && <div className="notification-note">
      {draft ? t('policy.draftAndRetireNote', { revisionNo: draft.revisionNo }) : t('policy.retireNote')}</div>}
    <h3 className="scan-policy-subtitle">{t('policy.revisions')}</h3>
    <div className="scan-policy-timeline">{detail.revisions.map(revision => <div key={revision.revisionNo} className={`scan-policy-revision is-${revision.status.toLowerCase()}`}>
      <div className="scan-policy-revision-head"><strong>r{revision.revisionNo}</strong>
        <Tag color={statusColors[revision.status]}>{revision.status === 'DRAFT' && canManage ? t('policy.draftEditing') : revisionStatusLabel(revision.status)}</Tag>
        <span className="muted">{t('policy.revisionTimes', { createdAt: formatDateTime(revision.createTime), updatedAt: formatDateTime(revision.updateTime) })}</span></div>
      {revision.status === 'DRAFT'
        ? <DraftEditor key={`${revision.revisionNo}:${revision.version}`} detail={detail} draft={revision} supported={supported} canManage={canManage}
          onChanged={changed} onAuthorizationChanged={onAuthorizationChanged} />
        : <div className="scan-policy-revision-body"><MimeTags types={revision.allowedMimeTypes} supported={supported} />
          <span>{t('policy.maxSize', { size: byteSizeLabel(revision.maxSizeBytes) })}</span></div>}
    </div>)}</div>
    <h3 className="scan-policy-subtitle">{t('policy.requirements')}</h3>
    <Table rowKey={row => `${row.scannerCode}/${row.scanType}`} size="small" pagination={false} dataSource={requirements} columns={[
      { title: t('policy.column.scanner'), render: (_, row) => <span className="mono">{row.scannerCode}</span> },
      { title: t('policy.column.scanType'), render: (_, row) => scanTypeLabel(row.scanType) },
      { title: t('policy.column.minimumVersion'), dataIndex: 'minimumScannerVersion', width: 90 },
      { title: t('policy.column.acceptedResults'), render: (_, row) => <span className="mono">{joinList(row.acceptedResultCodes)}</span> },
    ]} />
    <h3 className="scan-policy-subtitle">{t('policy.referencingRoutes')}</h3>
    {detail.routes.length ? <div className="scan-policy-routes">{detail.routes.map(route => <div key={`${route.sourceSystem}/${route.accessLevel}`}>
      <span className="mono">{sourceSystemLabel(route.sourceSystem)}</span> <Tag>{accessLevelLabel(route.accessLevel)}</Tag>
      <span className="muted">→ {route.instanceCode} / {route.bucketName}</span></div>)}</div>
      : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('policy.noReferencingRoutes')} />}
    {dialog === 'revision' && active && <ReasonModal title={t('policy.newRevisionTitle', { code: detail.policyCode })}
      failureTitle={t('policy.newRevisionFailed', { code: detail.policyCode })} okText={t('policy.newRevisionOk')} path={`${policyPath(detail.policyCode)}/revisions`}
      method="POST" body={reason => ({ reason: reason.trim() })} code={detail.policyCode}
      notice={t('policy.newRevisionNotice', { revisionNo: active.revisionNo })}
      onClose={() => setDialog(null)} onDone={changed} onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog === 'retire' && active && <ReasonModal title={t('policy.retireTitle', { code: detail.policyCode })}
      failureTitle={t('policy.retireFailed', { code: detail.policyCode })} okText={t('policy.confirmRetire')} danger
      path={`${policyPath(detail.policyCode)}/retirement`} method="POST" body={reason => versionedReasonBody(active, reason)} code={detail.policyCode}
      notice={t('policy.retireNotice', { revisionNo: active.revisionNo })}
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
  const [selected, setSelected] = useState<string>(); const [creating, setCreating] = useState(false); const t = useT('file');
  const supported = policies.supportedMimeTypes;
  const current = policies.items.find(item => item.policyCode === selected) ?? policies.items[0];
  return <div className="scan-policy-layout">
    <section className="table-panel">
      <div className="table-toolbar"><span className="table-count"><span>{t('policy.count', { count: policies.items.length })}</span>
        <RefreshButton onClick={refresh} loading={loading} failed={failed} /></span>
        {canManage && <Button type="primary" disabled={!supported.length} onClick={() => setCreating(true)}>{t('policy.create')}</Button>}</div>
      <Table<ScanPolicySummary> rowKey="policyCode" tableLayout="fixed" dataSource={policies.items} loading={loading} pagination={false} scroll={{ x: 680 }}
        rowClassName={row => row.policyCode === current?.policyCode ? 'is-selected clickable-row' : 'clickable-row'}
        onRow={row => ({ onClick: () => setSelected(row.policyCode) })} columns={[
          { title: t('policy.code'), width: 210, ellipsis: true, render: (_, row) => <span className="mono">{row.policyCode}</span> },
          { title: t('policy.column.currentRevision'), width: 84, render: (_, row) => <div>{row.activeRevisionNo ? `r${row.activeRevisionNo}` : '—'}
            {row.draftRevisionNo && <div className="cell-meta">{t('policy.draftRevision', { revisionNo: row.draftRevisionNo })}</div>}</div> },
          { title: t('policy.allowedTypes'), render: (_, row) => <div><MimeTags types={row.allowedMimeTypes} supported={supported} />
            {unsupportedMimeTypes(row.allowedMimeTypes, supported).length > 0 && <div className="notification-missing">{t('policy.hasUnrecognized')}</div>}</div> },
          { title: t('policy.column.maxSize'), width: 84, render: (_, row) => byteSizeLabel(row.maxSizeBytes) },
          { title: t('policy.column.routeCount'), width: 76, render: (_, row) => t('policy.routeCount', { count: row.routeCount }) },
          { title: t('policy.column.status'), width: 76, fixed: 'right', render: (_, row) => <Tag color={statusColors[row.status]}>{policyStatusLabel(row.status)}</Tag> },
        ]} />
      <div className="notification-note">{t('policy.supportedNote', { types: joinList(supported.map(mimeTypeLabel)) || '—' })}</div>
    </section>
    <section className="scan-policy-side">{current
      ? <PolicyDetail key={current.policyCode} summary={current} supported={supported} canManage={canManage} onChanged={refresh}
        onAuthorizationChanged={onAuthorizationChanged} />
      : <Empty description={loading ? t('policy.loading') : t('policy.empty')} />}</section>
    {creating && <CreatePolicyModal supported={supported} onClose={() => setCreating(false)}
      onDone={() => { setCreating(false); refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
