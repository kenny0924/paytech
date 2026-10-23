import { useRef, useState } from 'react';
import { Alert, App, Button, Form, Input, Modal } from 'antd';
import { ApiError, session } from '../api/session';
import type { GatewayRouteStatusResponseBody, GatewayTargetStatusResponseBody } from '../api/types';
import { gatewayRouteBreakGlassWarning, gatewayStatusCommand, gatewayStatusFailureAction,
  gatewayStatusSavedMessage, isExpectedGatewayStatusResponse } from '../pages/gatewayConfigurationModel';
import type { GatewayStatusCommand, GatewayStatusEntity } from '../pages/gatewayConfigurationModel';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useT } from '../i18n/react';

interface Props {
  entity: GatewayStatusEntity;
  allowed: boolean;
  onClose: () => void;
  onChanged: () => void;
  onAuthorizationChanged: () => void;
}

export function GatewayStatusModal({ entity, allowed, onClose, onChanged, onAuthorizationChanged }: Props) {
  const { message } = App.useApp();
  const t = useT('gateway'), tCommon = useT('common');
  const notifyError = useErrorNotification();
  const [form] = Form.useForm<{ reason: string }>();
  const [saving, setSaving] = useState(false);
  const [conflicted, setConflicted] = useState(false);
  const inFlight = useRef(false);
  const command = useRef<GatewayStatusCommand | null>(null);
  const enabling = entity.status === 'DISABLED';
  const subject = t(entity.kind === 'target' ? 'statusModal.subject.target' : 'statusModal.subject.route');
  const breakGlassWarning = gatewayRouteBreakGlassWarning(entity);

  async function change(values: { reason: string }) {
    if (inFlight.current || conflicted || !allowed) return;
    inFlight.current = true; setSaving(true);
    command.current = gatewayStatusCommand(entity, values.reason, command.current);
    try {
      const current = command.current;
      const result = await session.request<GatewayTargetStatusResponseBody | GatewayRouteStatusResponseBody>(current.path, {
        method: current.method, headers: { 'Idempotency-Key': current.key }, body: current.payload,
      });
      const responseCode = entity.kind === 'target'
        ? (result as GatewayTargetStatusResponseBody)?.targetServiceCode
        : (result as GatewayRouteStatusResponseBody)?.routeCode;
      if (!isExpectedGatewayStatusResponse(entity, responseCode, result?.status, result?.version)) {
        throw new ApiError(502, 'INVALID_RESPONSE');
      }
      void message.success(gatewayStatusSavedMessage());
      onChanged();
    } catch (failure) {
      notifyError(failure, t(enabling ? 'statusModal.enableFailed' : 'statusModal.disableFailed', { subject }));
      const action = gatewayStatusFailureAction(failure);
      if (action === 'STALE') onChanged();
      if (action === 'CONFLICT') setConflicted(true);
      if (action === 'AUTHORIZATION_CHANGED') onAuthorizationChanged();
    } finally { inFlight.current = false; setSaving(false); }
  }

  return <Modal open title={t(enabling ? 'statusModal.enableTitle' : 'statusModal.disableTitle', { subject })}
    okText={t(enabling ? 'statusModal.confirmEnable' : 'statusModal.confirmDisable')}
    cancelText={tCommon('action.cancel')} onOk={() => form.submit()} onCancel={() => { if (!inFlight.current) onClose(); }}
    confirmLoading={saving} okButtonProps={{ danger: !enabling, disabled: saving || conflicted || !allowed }}
    cancelButtonProps={{ disabled: saving }} closable={!saving} keyboard={!saving} mask={{ closable: !saving }}>
    <p><strong>{entity.code}</strong></p>
    {entity.kind === 'target' && !enabling && <Alert type="warning" showIcon className="form-alert"
      title={t('statusModal.targetDisableWarning')} />}
    {entity.kind === 'target' && !enabling && entity.code === 'operations-console-bff' && <Alert type="error" showIcon className="form-alert"
      title={t('statusModal.consoleTargetWarning')} />}
    {entity.kind === 'target' && enabling && <Alert type="info" showIcon className="form-alert"
      title={t('statusModal.targetEnableHint')} />}
    {entity.kind === 'route' && <Alert type={enabling ? 'info' : 'warning'} showIcon className="form-alert"
      title={t(enabling ? 'statusModal.routeEnableHint' : 'statusModal.routeDisableWarning')} />}
    {breakGlassWarning && <Alert type="error" showIcon className="form-alert" title={breakGlassWarning} />}
    {conflicted && <Alert type="warning" showIcon className="form-alert"
      title={t('statusModal.conflict')}
      action={<Button onClick={onChanged}>{tCommon('action.refreshList')}</Button>} />}
    <Form form={form} layout="vertical" requiredMark={false} disabled={saving || conflicted || !allowed} onFinish={change}>
      <Form.Item name="reason" label={tCommon('reason.label')} rules={[{ required: true, whitespace: true, message: tCommon('reason.required') }]}>
        <Input.TextArea rows={3} maxLength={500} showCount placeholder={t(enabling ? 'statusModal.enableReason' : 'statusModal.disableReason', { subject })} />
      </Form.Item>
    </Form>
  </Modal>;
}
