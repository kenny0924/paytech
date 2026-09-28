import { useRef, useState } from 'react';
import { Alert, App, Button, Form, Input, Modal } from 'antd';
import { ApiError, session } from '../api/session';
import type { GatewayRouteStatusResponseBody, GatewayTargetStatusResponseBody } from '../api/types';
import { gatewayRouteBreakGlassWarning, gatewayStatusCommand, gatewayStatusFailureAction,
  gatewayStatusSavedMessage, isExpectedGatewayStatusResponse } from '../pages/gatewayConfigurationModel';
import type { GatewayStatusCommand, GatewayStatusEntity } from '../pages/gatewayConfigurationModel';
import { useErrorNotification } from '../feedback/useErrorNotification';

interface Props {
  entity: GatewayStatusEntity;
  allowed: boolean;
  onClose: () => void;
  onChanged: () => void;
  onAuthorizationChanged: () => void;
}

export function GatewayStatusModal({ entity, allowed, onClose, onChanged, onAuthorizationChanged }: Props) {
  const { message } = App.useApp();
  const notifyError = useErrorNotification();
  const [form] = Form.useForm<{ reason: string }>();
  const [saving, setSaving] = useState(false);
  const [conflicted, setConflicted] = useState(false);
  const inFlight = useRef(false);
  const command = useRef<GatewayStatusCommand | null>(null);
  const enabling = entity.status === 'DISABLED';
  const subject = entity.kind === 'target' ? '目标服务' : '路由';
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
      void message.success(gatewayStatusSavedMessage);
      onChanged();
    } catch (failure) {
      notifyError(failure, `${subject}${enabling ? '启用' : '停用'}失败`);
      const action = gatewayStatusFailureAction(failure);
      if (action === 'STALE') onChanged();
      if (action === 'CONFLICT') setConflicted(true);
      if (action === 'AUTHORIZATION_CHANGED') onAuthorizationChanged();
    } finally { inFlight.current = false; setSaving(false); }
  }

  return <Modal open title={`${enabling ? '启用' : '停用'}${subject}`} okText={`确认${enabling ? '启用' : '停用'}`}
    cancelText="取消" onOk={() => form.submit()} onCancel={() => { if (!inFlight.current) onClose(); }}
    confirmLoading={saving} okButtonProps={{ danger: !enabling, disabled: saving || conflicted || !allowed }}
    cancelButtonProps={{ disabled: saving }} closable={!saving} keyboard={!saving} mask={{ closable: !saving }}>
    <p><strong>{entity.code}</strong></p>
    {entity.kind === 'target' && !enabling && <Alert type="warning" showIcon className="form-alert"
      title="停用目标服务会从后续运行快照中移除它的全部路由；各路由的配置状态不会改变。" />}
    {entity.kind === 'target' && !enabling && entity.code === 'operations-console-bff' && <Alert type="error" showIcon className="form-alert"
      title="该目标承载 Operations Console。变更应用后，控制台将不可用，请确认 Break-glass 恢复路径可用。" />}
    {entity.kind === 'target' && enabling && <Alert type="info" showIcon className="form-alert"
      title="启用目标服务不会自动启用它下面已停用的路由。" />}
    {entity.kind === 'route' && <Alert type={enabling ? 'info' : 'warning'} showIcon className="form-alert"
      title={enabling ? '启用后仍需目标服务处于启用状态，路由才会进入运行快照。' : '停用会从后续运行快照中移除此路由。'} />}
    {breakGlassWarning && <Alert type="error" showIcon className="form-alert" title={breakGlassWarning} />}
    {conflicted && <Alert type="warning" showIcon className="form-alert"
      title="配置已被其他操作修改，请刷新列表获取最新版本后重新操作。"
      action={<Button onClick={onChanged}>刷新列表</Button>} />}
    <Form form={form} layout="vertical" requiredMark={false} disabled={saving || conflicted || !allowed} onFinish={change}>
      <Form.Item name="reason" label="操作原因" rules={[{ required: true, whitespace: true, message: '请填写操作原因' }]}>
        <Input.TextArea rows={3} maxLength={500} showCount placeholder={`说明${enabling ? '启用' : '停用'}此${subject}的原因`} />
      </Form.Item>
    </Form>
  </Modal>;
}
