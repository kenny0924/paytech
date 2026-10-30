import { useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Form, Input, Modal, Radio, Select, Space } from 'antd';
import type { ApiError } from '../api/session';
import { formatAmount } from '../finance/finance';
import { enumLabel, formatDateTime, tOr } from '../i18n/index';
import type { Translate } from '../i18n/index';

/** 时间到分钟（冷却结束等）。 */
const minuteTime: Intl.DateTimeFormatOptions = { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' };
import { useT } from '../i18n/react';
import { balanceSegments, displayState, earliestCoolingEnd, isAccountId, ownerTypes, positive, reviewNoteMaxLength } from './payout';
import type { BalancePosition, BeneficiaryAccount, OwnerType } from './payout';

type Owner = { ownerType: OwnerType; ownerId: string };

/** 账户状态徽标：待核验 / 冷却中至 / 可用 / 已拒绝·原因 / 已停用·原因 / 已关闭。 */
export function AccountStatusBadge({ account }: { account: BeneficiaryAccount }) {
  const t = useT('payout');
  const state = displayState(account);
  const reason = account.reasonCode && (state === 'REJECTED' || state === 'SUSPENDED')
    ? enumLabel(t, state === 'REJECTED' ? 'enum.rejectReason' : 'enum.suspendReason', account.reasonCode) : null;
  const label = state === 'COOLING' && account.coolingUntil ? t('status.coolingUntil', { time: formatDateTime(account.coolingUntil, minuteTime) })
    : enumLabel(t, 'enum.displayState', state);
  return <span className={`payout-badge is-${state.toLowerCase()}`}>{label}{reason && <> · {reason}</>}</span>;
}

/** 银行与末四位，例如 “Emirates NBD 尾号 4321”。 */
export function bankLine(t: Translate, account: Pick<BeneficiaryAccount, 'bankName' | 'accountLast4'>) {
  return t('account.bankLast4', { bank: account.bankName, last4: account.accountLast4 });
}

/** 操作人展示：运营账号用显示名，否则用类型 + 账号 ID。 */
export function actorText(t: Translate, labels: Record<string, string>,
  actorType: string | null | undefined, actorId: string | null | undefined, actorIdSelf?: string) {
  if (!actorId) return enumLabel(t, 'enum.actorType', actorType ?? 'SYSTEM');
  if (actorIdSelf && actorId === actorIdSelf) return t('actor.you');
  const label = labels[actorId];
  if (actorType === 'OPERATIONS' || !actorType) return label ?? t('actor.operations', { id: actorId });
  return t('actor.typed', { type: enumLabel(t, 'enum.actorType', actorType), id: actorId });
}

/** 金额文本（币种主单位字符串，补齐两位）。 */
export const amount = (value: string | null | undefined) => (value === null || value === undefined ? '—' : formatAmount(value, 2).text);

/** 余额卡片：OE × 币种，可用额、分段条、桶数字、保证金 / 待收回与不能提款的原因。 */
export function BalanceCard({ position, accounts }: { position: BalancePosition; accounts: readonly BeneficiaryAccount[] }) {
  const t = useT('payout');
  const segments = balanceSegments(position);
  const recovery = positive(position.recoveryReceivable);
  return <div className="payout-balance-card">
    <div className="payout-balance-head"><strong>{t('balance.cardTitle', { entityNo: position.entityNo, currency: position.currency })}</strong>
      <span className={positive(position.available) ? 'payout-available is-positive' : 'payout-available'}>
        {t('balance.availableHeadline', { amount: amount(position.available) })}</span></div>
    <div className="payout-bar" role="img" aria-label={t('balance.barLabel')}>
      <span className="is-pending" style={{ width: `${segments.pending * 100}%` }} />
      <span className="is-available" style={{ width: `${segments.available * 100}%` }} />
      <span className="is-locked" style={{ width: `${segments.locked * 100}%` }} />
    </div>
    <div className="payout-legend">
      <span><i className="is-pending" />{t('balance.pending', { amount: amount(position.pending) })}</span>
      <span><i className="is-available" />{t('balance.available', { amount: amount(position.available) })}</span>
      <span><i className="is-locked" />{t('balance.locked', { amount: amount(position.locked) })}</span>
    </div>
    <div className="payout-legend is-secondary">
      <span>{t('balance.securityDeposit', { amount: position.securityDeposit === null || position.securityDeposit === undefined ? '—' : amount(position.securityDeposit) })}</span>
      <span className={recovery ? 'is-danger' : undefined}>{t('balance.recoveryReceivable', { amount: amount(position.recoveryReceivable) })}</span>
    </div>
    {position.blockReasons.length > 0 && <ul className="payout-block-reasons">
      {position.blockReasons.map(reason => <li key={reason}>{blockReasonText(t, reason, position, accounts)}</li>)}
    </ul>}
  </div>;
}

/** 不能提款的原因文案；冷却中带最早可用时间，待收回带金额。未知原因显示原始代码。 */
export function blockReasonText(t: Translate, reason: string, position: BalancePosition,
  accounts: readonly BeneficiaryAccount[]): string {
  const params = { currency: position.currency, amount: amount(position.recoveryReceivable) };
  if (reason === 'ACCOUNT_COOLING') {
    const end = earliestCoolingEnd(accounts, position.currency);
    return end ? t('blockReason.coolingUntil', { currency: position.currency, time: formatDateTime(end, minuteTime) })
      : enumLabel(t, 'enum.blockReason', reason);
  }
  return tOr(t, `blockReason.text.${reason}`, enumLabel(t, 'enum.blockReason', reason), params);
}

/** 写命令的业务拒绝：已知错误码翻译，否则显示服务端说明。 */
export function CommandFailure({ failure }: { failure: ApiError | undefined }) {
  const t = useT('payout');
  if (!failure) return null;
  const known = tOr(t, 'error.' + failure.code, '');
  return <Alert showIcon type="warning" className="payout-failure" title={known || failure.detail || failure.message}
    description={<span className="cell-meta">{failure.code}{failure.requestId && ` · ${failure.requestId}`}</span>} />;
}

/**
 * 原因弹窗（O4 / O5）：账户摘要、受控原因单选、可选的给 Client 的说明、影响提示；提交前原因必选
 * <li>reasons 为空时（恢复）只确认；clientNote 给出时显示说明输入（拒绝），hint 为补充提示。</li>
 */
export function ReasonDialog({ title, subject, reasons, reasonPrefix, notice, okText, danger, saving, failure, clientNote, onSubmit, onClose }: {
  title: string; subject: ReactNode; reasons: readonly string[]; reasonPrefix: string; notice: ReactNode; okText: string; danger?: boolean;
  saving: boolean; failure?: ApiError; clientNote?: { hint?: ReactNode };
  onSubmit: (reasonCode: string | null, clientNote: string | null) => void; onClose: () => void;
}) {
  const t = useT('payout'), tCommon = useT('common');
  const [form] = Form.useForm<{ reasonCode: string; clientNote?: string }>();
  return <Modal open title={title} footer={null} width={520} closable={!saving} maskClosable={!saving} onCancel={() => { if (!saving) onClose(); }}>
    <div className="payout-dialog-subject">{subject}</div>
    <Form form={form} layout="vertical" onFinish={values => onSubmit(reasons.length ? values.reasonCode : null, values.clientNote?.trim() || null)}>
      {reasons.length > 0 && <Form.Item name="reasonCode" label={t('reasonDialog.reason')} rules={[{ required: true, message: t('reasonDialog.reasonRequired') }]}>
        <Radio.Group className="payout-reason-options">{reasons.map(reason =>
          <Radio key={reason} value={reason}>{enumLabel(t, reasonPrefix, reason)}</Radio>)}</Radio.Group></Form.Item>}
      {clientNote && <Form.Item name="clientNote" label={t('reasonDialog.clientNote')} extra={clientNote.hint}>
        <Input.TextArea rows={3} maxLength={reviewNoteMaxLength} showCount /></Form.Item>}
      <div className="payout-dialog-notice">{notice}</div>
      <CommandFailure failure={failure} />
      <div className="payout-dialog-foot"><Space><Button disabled={saving} onClick={onClose}>{tCommon('action.cancel')}</Button>
        <Button type="primary" danger={danger} htmlType="submit" loading={saving}>{okText}</Button></Space></div>
    </Form>
  </Modal>;
}

/** 只读键值行。 */
export function Field({ label, children }: { label: ReactNode; children: ReactNode }) {
  return <div className="payout-field"><span className="payout-field-label">{label}</span><span className="payout-field-value">{children}</span></div>;
}

/** 右上角 Owner 筛选：类型 + 编号。 */
export function OwnerFilter({ value, placeholder, onApply }: { value: Owner | null; placeholder: string; onApply: (value: Owner | null) => void }) {
  const t = useT('payout');
  const [ownerType, setOwnerType] = useState<OwnerType>(value?.ownerType ?? 'CLIENT');
  const [ownerId, setOwnerId] = useState(value?.ownerId ?? '');
  const valid = isAccountId(ownerId.trim());
  return <Space.Compact className="payout-owner-filter">
    <Select<OwnerType> value={ownerType} onChange={setOwnerType} options={ownerTypes.map(code => ({ value: code, label: enumLabel(t, 'enum.ownerType', code) }))} />
    <Input.Search allowClear value={ownerId} placeholder={placeholder} maxLength={19} status={ownerId && !valid ? 'error' : undefined}
      onChange={event => { setOwnerId(event.target.value); if (!event.target.value) onApply(null); }}
      onSearch={next => { const id = next.trim(); if (!id) onApply(null); else if (isAccountId(id)) onApply({ ownerType, ownerId: id }); }} />
  </Space.Compact>;
}
