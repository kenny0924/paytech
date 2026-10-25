import { useState } from 'react';
import { Alert, Button, Checkbox, Form, Input, Modal, Radio, Space, Tag, Tooltip } from 'antd';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { useAdminCommand } from '../qualification/useAdminCommand';
import {
  acquiringQualificationPayload, acquiringQualifications, commandFailureKey, decodeEntity, decodePartner, entityAdminApi, merchantWarning, nextRoles,
  oeProviderLocked, partnerRoles, rolesPayload,
} from './masterData';
import type { LocalPartner, OperatingEntity } from './masterData';

const statusColors: Record<string, string> = { ACTIVE: 'green', PENDING: 'blue', SUSPENDED: 'orange', CLOSED: 'red', TERMINATED: 'red', ENDED: 'default',
  DRAFT: 'default', APPROVED: 'blue', EXPIRED: 'default', WITHDRAWN: 'default' };
/** 状态标签：kind 为 master-data 命名空间的枚举种类（enum.<kind>.<值>）。 */
export function StatusTag({ status, kind }: { status: string; kind: string }) {
  const t = useT('master-data');
  return <Tag color={statusColors[status] ?? 'default'}>{enumLabel(t, 'enum.' + kind, status)}</Tag>;
}

/** 命令失败说明：409 版本冲突附“刷新”按钮，其余按错误码给出原因与服务端详情。 */
function FailureBanner({ failure, onReload }: { failure: ReturnType<typeof useAdminCommand>['failure']; onReload: () => void }) {
  const t = useT('master-data');
  const key = commandFailureKey(failure);
  if (!key) return null;
  const conflict = key === 'command.conflict';
  return <Alert showIcon type={conflict ? 'warning' : 'error'} style={{ marginBottom: 12 }} title={t(key)}
    description={!conflict && failure?.detail ? failure.detail : undefined}
    action={conflict ? <Button size="small" onClick={onReload}>{t('command.refresh')}</Button> : undefined} />;
}

/** 修改收单资质（B3）：三档单选并附说明，原因必填；选 MERCHANT 时提示将不再承接 Client。 */
export function AcquiringQualificationModal({ entity, onClose, onDone, onReload, onAuthorizationChanged }: {
  entity: OperatingEntity; onClose: () => void; onDone: () => void; onReload: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('master-data'), tCommon = useT('common');
  const [form] = Form.useForm<{ acquiringQualification: string; reason: string }>();
  const selected = Form.useWatch('acquiringQualification', form);
  const command = useAdminCommand(onAuthorizationChanged, t('command.failed'));
  async function submit(values: { acquiringQualification: string; reason: string }) {
    const result = await command.run(`${entityAdminApi}/operating-entities/${entity.entityNo}/acquiring-qualification`, 'PUT',
      acquiringQualificationPayload(entity, values.acquiringQualification, values.reason), value => decodeEntity(value, entity.entityNo), t('acquiring.saved'));
    if (result) onDone();
  }
  return <Modal open width={600} title={t('acquiring.dialogTitle')} footer={null} closable={!command.saving} mask={{ closable: !command.saving }}
    onCancel={() => { if (!command.saving) onClose(); }} destroyOnHidden>
    <p>{t('acquiring.dialogSubject', { entityNo: entity.entityNo, name: entity.legalName ?? entity.entityNo })}</p>
    <FailureBanner failure={command.failure} onReload={onReload} />
    <Form form={form} layout="vertical" initialValues={{ acquiringQualification: entity.acquiringQualification }} onFinish={values => void submit(values)}>
      <Form.Item name="acquiringQualification" label={t('acquiring.tier')} rules={[{ required: true }, { validator: (_, value?: string) =>
        value !== entity.acquiringQualification ? Promise.resolve() : Promise.reject(new Error(t('acquiring.unchanged'))) }]}>
        <Radio.Group className="master-data-tiers">{acquiringQualifications.map(tier => <Radio key={tier} value={tier}>
          <div><strong>{enumLabel(t, 'enum.acquiringQualification', tier)}</strong>{tier === entity.acquiringQualification && <Tag style={{ marginLeft: 8 }}>{t('acquiring.current')}</Tag>}</div>
          <div className="cell-meta">{t(`acquiring.description.${tier}`)}</div></Radio>)}</Radio.Group></Form.Item>
      {merchantWarning(selected) && selected !== entity.acquiringQualification && <Alert showIcon type="warning" style={{ marginBottom: 12 }} title={t('acquiring.merchantWarning')} />}
      <Form.Item name="reason" label={t('field.reason')} rules={[{ required: true, whitespace: true, message: t('field.reasonRequired') }]}>
        <Input.TextArea rows={3} maxLength={500} showCount /></Form.Item>
      <Space><Button disabled={command.saving} onClick={onClose}>{tCommon('action.cancel')}</Button>
        <Button type="primary" htmlType="submit" loading={command.saving}>{tCommon('action.save')}</Button></Space>
    </Form>
  </Modal>;
}

/** 维护 Partner 角色（B6）：多选且不能为空；仍有生效的提供关系时 OE_PROVIDER 不可取消并说明原因。 */
export function RolesModal({ partner, onClose, onDone, onReload, onAuthorizationChanged }: {
  partner: LocalPartner; onClose: () => void; onDone: () => void; onReload: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('master-data'), tCommon = useT('common');
  const locked = oeProviderLocked(partner);
  const [selected, setSelected] = useState<string[]>(partner.roles);
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);
  const command = useAdminCommand(onAuthorizationChanged, t('command.failed'));
  const roles = nextRoles(selected, partner);
  const unchanged = roles.join() === nextRoles(partner.roles, partner).join();
  async function submit() {
    setTouched(true);
    if (roles.length === 0 || !reason.trim() || unchanged) return;
    const result = await command.run(`${entityAdminApi}/local-partners/${partner.localPartnerId}/roles`, 'PUT', rolesPayload(selected, partner, reason),
      value => decodePartner(value, partner.localPartnerId), t('roles.saved'));
    if (result) onDone();
  }
  return <Modal open width={560} title={t('roles.dialogTitle')} footer={null} closable={!command.saving} mask={{ closable: !command.saving }}
    onCancel={() => { if (!command.saving) onClose(); }} destroyOnHidden>
    <p>{t('roles.dialogSubject', { partnerNo: partner.partnerNo, name: partner.legalName ?? partner.partnerNo })}</p>
    <FailureBanner failure={command.failure} onReload={onReload} />
    <Checkbox.Group value={roles} onChange={values => setSelected(values as string[])} className="master-data-roles">
      {partnerRoles.map(role => {
        const disabled = role === 'OE_PROVIDER' && locked;
        const box = <Checkbox key={role} value={role} disabled={disabled}><div><strong>{enumLabel(t, 'enum.partnerRole', role)}</strong></div>
          <div className="cell-meta">{t(`roles.description.${role}`)}</div></Checkbox>;
        return disabled ? <Tooltip key={role} title={t('roles.providerLocked')}>{box}</Tooltip> : box;
      })}
    </Checkbox.Group>
    {locked && <Alert showIcon type="info" style={{ margin: '12px 0' }} title={t('roles.providerLocked')} />}
    {touched && roles.length === 0 && <div className="master-data-error">{t('roles.required')}</div>}
    {touched && unchanged && roles.length > 0 && <div className="master-data-error">{t('roles.unchanged')}</div>}
    <div style={{ marginTop: 12 }}><div>{t('field.reason')}</div>
      <Input.TextArea rows={3} maxLength={500} showCount value={reason} onChange={event => setReason(event.target.value)} status={touched && !reason.trim() ? 'error' : undefined} />
      {touched && !reason.trim() && <div className="master-data-error">{t('field.reasonRequired')}</div>}</div>
    <Space style={{ marginTop: 16 }}><Button disabled={command.saving} onClick={onClose}>{tCommon('action.cancel')}</Button>
      <Button type="primary" loading={command.saving} onClick={() => void submit()}>{tCommon('action.save')}</Button></Space>
  </Modal>;
}
