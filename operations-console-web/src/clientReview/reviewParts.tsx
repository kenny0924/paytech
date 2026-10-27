import type { ReactNode } from 'react';
import { Alert, Button, Form, Modal, Space } from 'antd';
import type { FormInstance } from 'antd';
import type { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useT } from '../i18n/react';
import { useEffect } from 'react';
import { tOr } from '../i18n/index';
import { decodeCommand, withOnBehalf } from './clientReview';
import { useReviewCommand } from './useReviewCommand';

export const has = (resources: readonly ConsoleResource[], code: string) => resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === code);
export type Tone = 'ok' | 'warn' | 'danger' | 'info' | 'muted';

/** 状态圆点 + 文字；不用整块彩色底。 */
export function StatusDot({ tone, children }: { tone: Tone; children: ReactNode }) {
  return <span className={`obw-dot is-${tone}`}>{children}</span>;
}
const toneOf: Record<string, Tone> = {
  APPROVED: 'ok', ACCEPTED: 'ok', PASS: 'ok', SUCCEEDED: 'ok', RESOLVED: 'ok', WAIVED: 'ok', COMPLETED: 'ok', ACTIVE: 'ok', LOW: 'ok',
  REJECTED: 'danger', FAIL: 'danger', FAILED: 'danger', EXPIRED: 'danger', OPEN: 'warn', HIGH: 'danger', TIMED_OUT: 'danger', REVOKED: 'danger',
  RETURNED: 'warn', RECEIVED: 'warn', PENDING: 'warn', REQUESTED: 'warn', REVIEW: 'warn', UNKNOWN: 'warn', MEDIUM: 'warn', WAITING_EVIDENCE: 'warn',
  PENDING_DEPENDENCY: 'warn', SUBMITTED: 'info', IN_REVIEW: 'info', RUNNING: 'info', STARTED: 'info',
};
/** 常见状态码的语气；未知为灰色。 */
export const tone = (status: string | null | undefined): Tone => (status && toneOf[status]) || 'muted';

/** 422 / 409 的 Owner 原因：已知错误码翻译，否则显示服务端 detail。 */
export function CommandFailure({ failure }: { failure: { status: number; code: string; detail: string } | undefined }) {
  const t = useT('client-review');
  if (!failure || (failure.status !== 409 && failure.status !== 422)) return null;
  const known = tOr(t, 'enum.commandError.' + failure.code, '');
  const title = failure.status === 409 ? t('command.conflict') : t('command.rejected');
  return <Alert showIcon type="warning" style={{ marginBottom: 12 }} title={title}
    description={<>{known || failure.detail || failure.code}<div className="cell-meta">{failure.code}</div></>} />;
}

/**
 * 工作台写命令弹窗：表单内容 + 代操作 PayFac 组成请求体，成功后关闭并刷新
 * <li>请求体由 build 生成后统一附加 onBehalfOfPartnerId；Owner 的 409 / 422 留在弹窗内展示，输入保留。</li>
 */
export function ReviewCommandModal<V extends object>({ title, path, method = 'POST', caseId, onBehalfOfPartnerId, build, success, danger, okText,
  intro, children, onClose, onDone, onAuthorizationChanged, form: external, onFailure, renderFailure }: {
  title: string; path: string; method?: 'POST' | 'PUT'; caseId: string; onBehalfOfPartnerId: string | null; build: (values: V) => object; success: string;
  danger?: boolean; okText?: string; intro?: ReactNode; children?: ReactNode; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void;
  form?: FormInstance<V>;
  /** Owner 拒绝后的回调（例如刷新案件）；每次新失败调用一次。 */
  onFailure?: (failure: ApiError) => void;
  /** 自定义失败展示；返回 undefined 时用默认说明。 */
  renderFailure?: (failure: ApiError) => ReactNode | undefined;
}) {
  const tCommon = useT('common');
  const [own] = Form.useForm<V>(); const form = external ?? own;
  const command = useReviewCommand(onAuthorizationChanged);
  const failure = command.failure;
  useEffect(() => { if (failure) onFailure?.(failure); }, [failure]); // eslint-disable-line react-hooks/exhaustive-deps
  async function submit(values: V) {
    const result = await command.run(path, method, withOnBehalf(build(values), onBehalfOfPartnerId), value => decodeCommand(value, caseId), success);
    if (result) onDone();
  }
  const custom = failure ? renderFailure?.(failure) : undefined;
  return <Modal open title={title} footer={null} width={560} closable={!command.saving} maskClosable={!command.saving} onCancel={() => { if (!command.saving) onClose(); }}>
    {intro}
    <Form form={form} layout="vertical" onFinish={values => void submit(values)}>
      {children}
      {custom ?? <CommandFailure failure={failure} />}
      <div className="obw-modal-foot"><Space><Button disabled={command.saving} onClick={onClose}>{tCommon('action.cancel')}</Button>
        <Button type="primary" danger={danger} htmlType="submit" loading={command.saving}>{okText ?? title}</Button></Space></div>
    </Form>
  </Modal>;
}
