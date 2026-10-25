import { useMemo, useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Empty, Form, Input, Modal, Select, Space, Tag, Timeline } from 'antd';
import { DatePicker } from '../components/DatePicker';
import { Loading, QueryFailure, date, useDetail } from '../tenantAccess/TenantAccessView';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  allowedQualificationTypes, commandFailureKey, decodeHistory, decodeQualification, fromLocalInput, localInputOf, manualDecisionPayload,
  qualificationApi, revocationPayload,
} from './qualification';
import type { Qualification, RoleType } from './qualification';
import { useAdminCommand } from './useAdminCommand';
import { EffectiveStatusTag } from './QualificationParts';

const reasonCodePattern = /^[A-Z][A-Z0-9_]{0,63}$/;

/** 命令失败说明：409 版本冲突附“刷新”按钮，其余按错误码给出原因与服务端详情。 */
function FailureBanner({ failure, onReload }: { failure: ReturnType<typeof useAdminCommand>['failure']; onReload: () => void }) {
  const t = useT('qualification');
  const key = commandFailureKey(failure);
  if (!key) return null;
  const conflict = key === 'command.conflict';
  return <Alert showIcon type={conflict ? 'warning' : 'error'} style={{ marginBottom: 12 }} title={t(key)}
    description={!conflict && failure?.detail ? failure.detail : undefined}
    action={conflict ? <Button size="small" onClick={onReload}>{t('command.refresh')}</Button> : undefined} />;
}

interface DecisionValues { qualificationType: string; validFrom: string; validTo?: string; reasonCode: string; basisNote: string }

/**
 * 手工登记（B10）：先填写类型、有效期、原因码与依据，再在确认步骤复述影响后提交。
 * <li>类型只列该角色允许的类型；existing 为该类型已有的资格行（带 version 作 expectedVersion），首次登记为空。</li>
 */
export function ManualDecisionModal({ subject, existing, items, onClose, onDone, onReload, onAuthorizationChanged }: {
  subject: { roleType: RoleType; roleId: string; displayName: string | null }; existing: Qualification | null; items: Qualification[];
  onClose: () => void; onDone: (q: Qualification) => void; onReload: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('qualification'), tCommon = useT('common');
  const [form] = Form.useForm<DecisionValues>();
  const command = useAdminCommand(onAuthorizationChanged, t('command.failed'));
  const [draft, setDraft] = useState<DecisionValues | null>(null);
  const types = allowedQualificationTypes(subject.roleType);
  const initial = useMemo(() => ({ qualificationType: existing?.qualificationType ?? (types.length === 1 ? types[0] : undefined),
    validFrom: localInputOf(new Date()) }), [existing, types]);
  const rowOf = (type: string) => existing?.qualificationType === type ? existing : items.find(i => i.qualificationType === type) ?? null;
  async function submit() {
    if (!draft) return;
    const row = rowOf(draft.qualificationType);
    const payload = manualDecisionPayload({ roleType: subject.roleType, roleId: subject.roleId, qualificationType: draft.qualificationType,
      validFrom: fromLocalInput(draft.validFrom)!, validTo: fromLocalInput(draft.validTo), reasonCode: draft.reasonCode, basisNote: draft.basisNote }, row);
    if (!payload) return;
    const result = await command.run(`${qualificationApi}/qualifications/manual-decisions`, 'POST', payload,
      value => decodeQualification(value), t('decision.saved'));
    if (result) onDone(result);
  }
  const name = subject.displayName ?? subject.roleId;
  return <Modal open width={600} title={t('decision.title')} footer={null} closable={!command.saving} mask={{ closable: !command.saving }}
    onCancel={() => { if (!command.saving) onClose(); }} destroyOnHidden>
    <p>{t('decision.subject', { role: enumLabel(t, 'enum.roleType', subject.roleType), name, roleId: subject.roleId })}</p>
    <FailureBanner failure={command.failure} onReload={onReload} />
    <div hidden={draft !== null}>
      <Form form={form} layout="vertical" initialValues={initial} onFinish={values => setDraft(values)}>
        <Form.Item name="qualificationType" label={t('field.qualificationType')} rules={[{ required: true, message: t('decision.typeRequired') }]}>
          <Select options={types.map(value => ({ value, label: enumLabel(t, 'enum.qualificationType', value) }))} disabled={existing !== null} /></Form.Item>
        <div className="qualification-form-grid">
          <Form.Item name="validFrom" label={t('field.validFrom')} rules={[{ required: true, message: t('decision.validFromRequired') }]}><DatePicker showTime /></Form.Item>
          <Form.Item name="validTo" label={t('decision.validToOptional')} dependencies={['validFrom']} rules={[({ getFieldValue }) => ({ validator: (_, value?: string) => {
            const from = fromLocalInput(getFieldValue('validFrom')); const to = fromLocalInput(value);
            return !to || !from || to.getTime() > from.getTime() ? Promise.resolve() : Promise.reject(new Error(t('decision.validToAfter')));
          } })]}><DatePicker showTime /></Form.Item>
        </div>
        <Form.Item name="reasonCode" label={t('field.reasonCode')} rules={[{ required: true, pattern: reasonCodePattern, message: t('decision.reasonCodeInvalid') }]}>
          <Input maxLength={64} placeholder="UNDERWRITING_DONE" /></Form.Item>
        <Form.Item name="basisNote" label={t('field.basisNote')} extra={t('decision.basisNoteHint')} rules={[{ required: true, whitespace: true, message: t('decision.basisNoteRequired') }]}>
          <Input.TextArea rows={3} maxLength={1000} showCount /></Form.Item>
        <Space><Button onClick={onClose}>{tCommon('action.cancel')}</Button><Button type="primary" htmlType="submit">{t('decision.next')}</Button></Space>
      </Form>
    </div>
    {draft && <div>
      <Descriptions size="small" column={1} items={[
        { key: 'type', label: t('field.qualificationType'), children: enumLabel(t, 'enum.qualificationType', draft.qualificationType) },
        { key: 'valid', label: t('field.validity'), children: t('field.validityRange', { from: date(fromLocalInput(draft.validFrom)!.toISOString()),
          to: date(fromLocalInput(draft.validTo)?.toISOString() ?? null) }) },
        { key: 'reason', label: t('field.reasonCode'), children: <span className="qualification-mono">{draft.reasonCode}</span> },
        { key: 'basis', label: t('field.basisNote'), children: draft.basisNote },
        ...(rowOf(draft.qualificationType) ? [{ key: 'current', label: t('decision.current'), children: <EffectiveStatusTag status={rowOf(draft.qualificationType)!.effectiveStatus} /> }] : []),
      ]} />
      <Alert showIcon type="info" style={{ margin: '12px 0' }} title={t('decision.impact')} description={t('decision.impactExisting')} />
      <Space><Button disabled={command.saving} onClick={() => setDraft(null)}>{t('decision.back')}</Button>
        <Button type="primary" loading={command.saving} onClick={() => void submit()}>{t('decision.confirm')}</Button></Space>
    </div>}
  </Modal>;
}

/** 撤销（B11）：必须填写原因码与依据；历史快照不会被改写。 */
export function RevokeModal({ qualification, subjectName, onClose, onDone, onReload, onAuthorizationChanged }: {
  qualification: Qualification; subjectName: string | null; onClose: () => void; onDone: (q: Qualification) => void; onReload: () => void;
  onAuthorizationChanged: () => void;
}) {
  const t = useT('qualification'), tCommon = useT('common');
  const command = useAdminCommand(onAuthorizationChanged, t('command.failed'));
  async function submit(values: { reasonCode: string; basisNote: string }) {
    const result = await command.run(`${qualificationApi}/qualifications/${qualification.qualificationId}/revocation`, 'POST',
      revocationPayload(qualification, values.reasonCode, values.basisNote), value => decodeQualification(value, qualification.qualificationId), t('revoke.saved'));
    if (result) onDone(result);
  }
  return <Modal open width={560} title={t('revoke.title')} footer={null} closable={!command.saving} mask={{ closable: !command.saving }}
    onCancel={() => { if (!command.saving) onClose(); }} destroyOnHidden>
    <p>{t('revoke.subject', { type: enumLabel(t, 'enum.qualificationType', qualification.qualificationType), name: subjectName ?? qualification.roleId, roleId: qualification.roleId })}</p>
    <Alert showIcon type="warning" style={{ marginBottom: 12 }} title={t('revoke.warning')} description={t('revoke.historyNotice')} />
    <FailureBanner failure={command.failure} onReload={onReload} />
    <Form layout="vertical" onFinish={values => void submit(values)}>
      <Form.Item name="reasonCode" label={t('field.reasonCode')} rules={[{ required: true, pattern: reasonCodePattern, message: t('decision.reasonCodeInvalid') }]}>
        <Input maxLength={64} placeholder="UNDERWRITING_WITHDRAWN" /></Form.Item>
      <Form.Item name="basisNote" label={t('field.basisNote')} rules={[{ required: true, whitespace: true, message: t('revoke.basisNoteRequired') }]}>
        <Input.TextArea rows={3} maxLength={1000} showCount /></Form.Item>
      <Space><Button disabled={command.saving} onClick={onClose}>{tCommon('action.cancel')}</Button>
        <Button type="primary" danger htmlType="submit" loading={command.saving}>{t('revoke.confirm')}</Button></Space>
    </Form>
  </Modal>;
}

/** 历史（B9）：按发生时间的状态变化时间线，含来源、操作人与依据。 */
export function HistoryDrawer({ qualification, subjectName, onClose }: { qualification: Qualification; subjectName: string | null; onClose: () => void }) {
  const t = useT('qualification');
  const decode = useMemo(() => (value: unknown) => decodeHistory(value, qualification.qualificationId), [qualification.qualificationId]);
  const history = useDetail(`${qualificationApi}/qualifications/${qualification.qualificationId}/history`, decode);
  const items = history.data?.items.toSorted((a, b) => b.occurredAt.localeCompare(a.occurredAt)) ?? [];
  return <Drawer open size="min(560px, 100vw)" rootClassName="qualification-page" destroyOnHidden onClose={onClose}
    title={t('history.title', { type: enumLabel(t, 'enum.qualificationType', qualification.qualificationType), name: subjectName ?? qualification.roleId })}>
    {history.loading && !history.data ? <Loading />
      : history.failure || !history.data ? <QueryFailure failure={history.failure} retry={history.refresh} />
      : items.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('history.empty')} />
      : <Timeline items={items.map(item => ({ key: item.changeId, color: item.toStatus === 'APPROVED' ? 'green' : 'red', content: <div className="qualification-history-item">
        <div><strong>{item.fromStatus ? t('history.transition', { from: enumLabel(t, 'enum.status', item.fromStatus), to: enumLabel(t, 'enum.status', item.toStatus) })
          : enumLabel(t, 'enum.status', item.toStatus)}</strong> <Tag>{enumLabel(t, 'enum.triggerType', item.triggerType)}</Tag>
          <Tag>{enumLabel(t, 'enum.decisionSource', item.decisionSource)}</Tag></div>
        <div className="cell-meta">{t('history.meta', { time: date(item.occurredAt), actor: item.actorId ?? item.decidedBy ?? t('history.system'), version: item.aggregateVersion })}</div>
        <div className="cell-meta">{t('field.validityRange', { from: date(item.validFrom), to: date(item.validTo) })}</div>
        {item.reasonCode && <div className="cell-meta">{t('history.reason', { reasonCode: item.reasonCode })}</div>}
        {item.basisNote && <div className="qualification-basis">{item.basisNote}</div>}
      </div> }))} />}
  </Drawer>;
}
