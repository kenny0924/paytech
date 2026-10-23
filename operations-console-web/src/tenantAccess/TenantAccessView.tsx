import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Form, Input, Modal, Space, Spin, Tag } from 'antd';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useConfigurationForm } from '../configuration/useConfigurationForm';
import { enumLabel, formatDateTime, tOr, translator } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import { decodeMutation, tenantApi } from './tenantAccess';
import type { Member, Reason, Tenant } from './tenantAccess';

const tAccess = translator('access');
/** Local date and time; a missing end means the grant or validity has no end date. */
export const date = (value: string | null) => value ? formatDateTime(value) : tAccess('date.noEnd');

export function useDetail<T>(path: string, decode: (value: unknown) => T) {
  const t = useT('access'); const notify = useErrorNotification(); const [data, setData] = useState<T>(); const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<unknown>(); const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setFailure(undefined);
    session.request<T>(path, { signal: controller.signal }, decode)
      .then(value => { if (!controller.signal.aborted) setData(value); })
      .catch(error => { if (!controller.signal.aborted) { setFailure(error); notify(error, t('detail.loadFailed')); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [path, decode, notify, revision]);
  return { data, loading, failure, refresh: () => setRevision(v => v + 1) };
}
export function QueryFailure({ failure, retry, completed = false }: { failure: unknown; retry?: () => void; completed?: boolean }) {
  const t = useT('access'), tCommon = useT('common');
  const error = failure instanceof ApiError ? failure : undefined;
  const title = completed ? t('queryFailure.completed') : error?.status === 403 ? t('queryFailure.forbidden') : error?.status === 404 ? t('queryFailure.notFound') : t('queryFailure.unavailable');
  return <Alert type={error?.status === 403 ? 'info' : 'warning'} showIcon title={title}
    description={<>{failure instanceof Error && <div>{failure.message}</div>}{error?.requestId && <div className="cell-meta">{t('queryFailure.requestId', { requestId: error.requestId })}</div>}</>}
    action={retry && <Button onClick={retry}>{tCommon('action.retry')}</Button>} />;
}
export function Loading() { return <div className="content-loading"><Spin /></div>; }
/** Platform 返回固定的原因编码与中文说明：按编码翻译，未知编码显示服务端说明。 */
export function reasonText(t: Translate, reason: Reason): string { return tOr(t, 'enum.tenantAccessReason.' + reason.code, reason.message); }
export function Reasons({ reasons }: { reasons: Reason[] }) {
  const t = useT('access');
  return <div className="tenant-reasons">{reasons.map(r => <div key={r.code}>{reasonText(t, r)}</div>)}</div>;
}
export function Effective({ effective, reasons }: { effective: boolean; reasons: Reason[] }) {
  const t = useT('access');
  return <div><Tag color={effective ? 'green' : 'orange'}>{effective ? t('effective.yes') : t('effective.no')}</Tag><Reasons reasons={reasons} /></div>;
}
export function Status({ value }: { value: string }) {
  const t = useT('access');
  return <Tag color={value === 'ACTIVE' ? 'green' : value === 'REVOKED' || value === 'CLOSED' ? 'red' : 'default'}>{enumLabel(t, 'enum.iamState', value)}</Tag>;
}
export function TenantSummary({ tenant, compact = false }: { tenant: Tenant; compact?: boolean }) {
  const t = useT('access');
  return <section className="tenant-summary"><div className="tenant-summary-heading"><div><h2 title={tenant.tenantName}>{tenant.tenantName}</h2>
    <div className="cell-meta">{t('tenant.summaryMeta', { tenantType: enumLabel(t, 'enum.tenantType', tenant.tenantType), tenantId: tenant.tenantId, ownerRefId: tenant.ownerRefId })}</div></div>
    <Tag color={tenant.tenantAccessAllowed ? 'green' : 'orange'}>{tenant.tenantAccessAllowed ? t('tenant.accessMet') : t('tenant.accessNotMet')}</Tag></div>
    <Descriptions size="small" column={{ xs: 1, sm: 2, lg: compact ? 2 : 4 }} items={[
      { key: 'owner', label: t('tenant.ownerStatus'), children: enumLabel(t, 'enum.tenantOwnerStatus', tenant.ownerStatus) },
      { key: 'security', label: t('tenant.securityStatus'), children: enumLabel(t, 'enum.tenantSecurityStatus', tenant.securityStatus) },
      ...(!compact ? [{ key: 'created', label: t('tenant.createdAt'), children: date(tenant.createdAt) }, { key: 'updated', label: t('tenant.updatedAt'), children: date(tenant.updatedAt) }] : []),
    ]} />
    {tenant.restrictionReasons.length > 0 && <Alert showIcon type="warning" title={tenant.restrictionReasons.map(r => reasonText(t, r)).join(t('tenant.reasonSeparator'))}
      description={tenant.ownerStatus !== 'ACTIVE' ? t('tenant.restoreKeepsOwnerStatus') : undefined} />}
  </section>;
}
export function MemberSummary({ member }: { member: Member }) {
  const t = useT('access');
  return <section className="tenant-summary"><h2 className="tenant-ellipsis" title={member.emailMasked || member.accountId}>{member.emailMasked || t('member.accountTitle', { accountId: member.accountId })}</h2>
    <Descriptions column={{ xs: 1, sm: 2, lg: 3 }} size="small" items={[
      { key: 'account', label: t('member.accountId'), children: member.accountId }, { key: 'member', label: t('member.memberId'), children: member.memberId },
      { key: 'type', label: t('member.memberType'), children: enumLabel(t, 'enum.memberType', member.memberType) }, { key: 'accountState', label: t('member.accountStatus'), children: <Status value={member.accountStatus} /> },
      { key: 'memberState', label: t('member.memberStatus'), children: <Status value={member.memberStatus} /> }, { key: 'created', label: t('member.joinedAt'), children: date(member.createdAt) },
    ]} /></section>;
}
export function ChangeSecurity({ tenant, onClose, onSaved, onAuthorizationChanged }: {
  tenant: Tenant; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('access'), tCommon = useT('common');
  const [form] = Form.useForm<{ reason: string }>(); const editor = useConfigurationForm(onAuthorizationChanged, form);
  const target = tenant.securityStatus === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'; const pause = target === 'SUSPENDED';
  async function save(values: { reason: string }) {
    const result = await editor.save(`${tenantApi}/${tenant.tenantId}/security-status`, 'POST',
      { securityStatus: target, expectedVersion: tenant.version, reason: values.reason.trim() }, value => decodeMutation(value, tenant, target));
    if (result) onSaved();
  }
  return <Modal open title={pause ? t('security.suspendTitle') : t('security.restoreTitle')} footer={null} closable={!editor.saving} maskClosable={!editor.saving}
    onCancel={() => { if (!editor.saving) onClose(); }}>
    <p className="tenant-ellipsis" title={tenant.tenantName}>{tenant.tenantName} · {tenant.tenantId}</p>
    <p>{pause ? t('security.suspendHint') : t('security.restoreHint')}</p>
    {!pause && tenant.ownerStatus !== 'ACTIVE' && <Alert showIcon type="warning" title={t('security.ownerStillBlocks', { ownerStatus: enumLabel(t, 'enum.tenantOwnerStatus', tenant.ownerStatus) })} />}
    <Form form={form} layout="vertical" onValuesChange={() => editor.setDirty(true)} onFinish={save}>
      <Form.Item name="reason" label={tCommon('reason.label')} rules={[{ required: true, whitespace: true, message: tCommon('reason.required') }]}>
        <Input.TextArea autoFocus rows={3} maxLength={500} showCount disabled={editor.saving || editor.conflict} /></Form.Item>
      {editor.conflict && <Alert showIcon type="warning" title={t('security.conflict')} />}
      <Space className="tenant-modal-actions"><Button disabled={editor.saving} onClick={onClose}>{tCommon('action.cancel')}</Button>
        <Button type="primary" danger={pause} htmlType="submit" loading={editor.saving} disabled={editor.conflict}>{pause ? t('security.confirmSuspend') : t('security.confirmRestore')}</Button></Space>
    </Form>
  </Modal>;
}
