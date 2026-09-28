import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Form, Input, Modal, Space, Spin, Tag } from 'antd';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useConfigurationForm } from '../configuration/useConfigurationForm';
import { decodeMutation, memberLabels, ownerLabels, securityLabels, stateLabels, tenantApi } from './tenantAccess';
import type { Member, Reason, Tenant } from './tenantAccess';
export const date = (value: string | null) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '长期';

export function useDetail<T>(path: string, decode: (value: unknown) => T) {
  const notify = useErrorNotification(); const [data, setData] = useState<T>(); const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<unknown>(); const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setFailure(undefined);
    session.request<T>(path, { signal: controller.signal }, decode)
      .then(value => { if (!controller.signal.aborted) setData(value); })
      .catch(error => { if (!controller.signal.aborted) { setFailure(error); notify(error, '详情加载失败'); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [path, decode, notify, revision]);
  return { data, loading, failure, refresh: () => setRevision(v => v + 1) };
}
export function QueryFailure({ failure, retry, completed = false }: { failure: unknown; retry?: () => void; completed?: boolean }) {
  const error = failure instanceof ApiError ? failure : undefined;
  const title = completed ? '操作已完成，详情刷新失败' : error?.status === 403 ? '没有查看此内容的权限' : error?.status === 404 ? '目标不存在或不属于当前租户' : '暂时无法加载，请重试';
  return <Alert type={error?.status === 403 ? 'info' : 'warning'} showIcon title={title}
    description={<>{failure instanceof Error && <div>{failure.message}</div>}{error?.requestId && <div className="cell-meta">Request ID：{error.requestId}</div>}</>}
    action={retry && <Button onClick={retry}>重试</Button>} />;
}
export function Loading() { return <div className="content-loading"><Spin /></div>; }
export function Reasons({ reasons }: { reasons: Reason[] }) { return <div className="tenant-reasons">{reasons.map(r => <div key={r.code}>{r.message}</div>)}</div>; }
export function Effective({ effective, reasons }: { effective: boolean; reasons: Reason[] }) {
  return <div><Tag color={effective ? 'green' : 'orange'}>{effective ? '有效' : '未生效'}</Tag><Reasons reasons={reasons} /></div>;
}
export function Status({ value }: { value: string }) { return <Tag color={value === 'ACTIVE' ? 'green' : value === 'REVOKED' || value === 'CLOSED' ? 'red' : 'default'}>{stateLabels[value] || value}</Tag>; }
export function TenantSummary({ tenant, compact = false }: { tenant: Tenant; compact?: boolean }) {
  return <section className="tenant-summary"><div className="tenant-summary-heading"><div><h2 title={tenant.tenantName}>{tenant.tenantName}</h2>
    <div className="cell-meta">{tenant.tenantType === 'CLIENT' ? '客户' : '合作伙伴'} · 租户 {tenant.tenantId} · 所属主体 {tenant.ownerRefId}</div></div>
    <Tag color={tenant.tenantAccessAllowed ? 'green' : 'orange'}>租户访问条件{tenant.tenantAccessAllowed ? '已满足' : '未满足'}</Tag></div>
    <Descriptions size="small" column={{ xs: 1, sm: 2, lg: compact ? 2 : 4 }} items={[
      { key: 'owner', label: '来源业务状态', children: ownerLabels[tenant.ownerStatus] },
      { key: 'security', label: '安全状态', children: securityLabels[tenant.securityStatus] },
      ...(!compact ? [{ key: 'created', label: '创建时间', children: date(tenant.createdAt) }, { key: 'updated', label: '更新时间', children: date(tenant.updatedAt) }] : []),
    ]} />
    {tenant.restrictionReasons.length > 0 && <Alert showIcon type="warning" title={tenant.restrictionReasons.map(r => r.message).join('；')}
      description={tenant.ownerStatus !== 'ACTIVE' ? '恢复安全状态不会改变来源业务状态；两项均为正常时才满足租户访问条件。' : undefined} />}
  </section>;
}
export function MemberSummary({ member }: { member: Member }) {
  return <section className="tenant-summary"><h2 className="tenant-ellipsis" title={member.emailMasked || member.accountId}>{member.emailMasked || `账号 ${member.accountId}`}</h2>
    <Descriptions column={{ xs: 1, sm: 2, lg: 3 }} size="small" items={[
      { key: 'account', label: '账号 ID', children: member.accountId }, { key: 'member', label: '成员 ID', children: member.memberId },
      { key: 'type', label: '成员类型', children: memberLabels[member.memberType] }, { key: 'accountState', label: '账号状态', children: <Status value={member.accountStatus} /> },
      { key: 'memberState', label: '成员状态', children: <Status value={member.memberStatus} /> }, { key: 'created', label: '加入时间', children: date(member.createdAt) },
    ]} /></section>;
}
export function ChangeSecurity({ tenant, onClose, onSaved, onAuthorizationChanged }: {
  tenant: Tenant; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<{ reason: string }>(); const editor = useConfigurationForm(onAuthorizationChanged, form);
  const target = tenant.securityStatus === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'; const pause = target === 'SUSPENDED';
  async function save(values: { reason: string }) {
    const result = await editor.save(`${tenantApi}/${tenant.tenantId}/security-status`, 'POST',
      { securityStatus: target, expectedVersion: tenant.version, reason: values.reason.trim() }, value => decodeMutation(value, tenant, target));
    if (result) onSaved();
  }
  return <Modal open title={pause ? '暂停租户访问' : '恢复租户安全状态'} footer={null} closable={!editor.saving} maskClosable={!editor.saving}
    onCancel={() => { if (!editor.saving) onClose(); }}>
    <p className="tenant-ellipsis" title={tenant.tenantName}>{tenant.tenantName} · {tenant.tenantId}</p>
    <p>{pause ? '仅暂停该租户的后续访问，不影响其他租户或已在途的请求。' : '仅解除该租户的平台安全限制。'}</p>
    {!pause && tenant.ownerStatus !== 'ACTIVE' && <Alert showIcon type="warning" title={`来源业务状态为${ownerLabels[tenant.ownerStatus]}，恢复后仍不能访问。`} />}
    <Form form={form} layout="vertical" onValuesChange={() => editor.setDirty(true)} onFinish={save}>
      <Form.Item name="reason" label="操作原因" rules={[{ required: true, whitespace: true, message: '请填写操作原因' }]}>
        <Input.TextArea autoFocus rows={3} maxLength={500} showCount disabled={editor.saving || editor.conflict} /></Form.Item>
      {editor.conflict && <Alert showIcon type="warning" title="租户已有更新，填写的原因已保留。请关闭弹窗，刷新详情后核对。" />}
      <Space className="tenant-modal-actions"><Button disabled={editor.saving} onClick={onClose}>取消</Button>
        <Button type="primary" danger={pause} htmlType="submit" loading={editor.saving} disabled={editor.conflict}>{pause ? '确认暂停' : '确认恢复'}</Button></Space>
    </Form>
  </Modal>;
}
