import { Form, Input, InputNumber, Select } from 'antd';
import { positiveAmountError } from './reconModel';
import type { CashAccount } from './reconModel';
import { cashAccountLabel } from './useReconData';

export interface ClearingSettingsValues { settlementCashAccountId?: string; unmatchedWaitDays: number; costToleranceAmount: string; cashGraceDays: number }

/** 渠道结算用途、同主体同币种的有效银行账户（到账银行账户的可选范围）。 */
export function settlementAccounts(accounts: readonly CashAccount[], entityNo?: string, currency?: string) {
  return accounts.filter(item => item.accountPurpose === 'CHANNEL_SETTLEMENT' && item.status === 'ACTIVE' && item.entityType === 'OPERATING_ENTITY'
    && (!entityNo || item.entityId === entityNo) && (!currency || item.currency === currency));
}

/** 设置请求体：到账银行账户为空表示解除。 */
export function settingsBody(values: ClearingSettingsValues) {
  return { settlementCashAccountId: values.settlementCashAccountId || null, unmatchedWaitDays: values.unmatchedWaitDays,
    costToleranceAmount: values.costToleranceAmount.trim(), cashGraceDays: values.cashGraceDays };
}

/** 清算账户配置字段：新建抽屉与详情页共用。 */
export function ClearingSettingsFields({ cash, entityNo, currency }: { cash: readonly CashAccount[]; entityNo?: string; currency?: string }) {
  const options = settlementAccounts(cash, entityNo, currency);
  return <div className="finance-form-grid">
    <Form.Item name="settlementCashAccountId" label="到账银行账户" extra={options.length ? '为空时账单不能对平' : '没有同主体、同币种、渠道结算用途的有效银行账户'}>
      <Select allowClear placeholder="选择银行账户" options={options.map(item => ({ value: item.cashAccountId, label: cashAccountLabel(item) }))} /></Form.Item>
    <Form.Item name="unmatchedWaitDays" label="账单行等待天数" rules={[{ required: true, message: '请填写 0–365' }]} extra="期满仍未命中平台流水转为异常">
      <InputNumber min={0} max={365} precision={0} style={{ width: '100%' }} /></Form.Item>
    <Form.Item name="costToleranceAmount" label="渠道费容差" rules={[{ validator: async (_, value?: string) => {
      if (value?.trim() === '0') return; const error = positiveAmountError(value); if (error) throw new Error(error); } }]} extra="实际费用与预估之差超过它开阻塞异常">
      <Input inputMode="decimal" placeholder="0.10" /></Form.Item>
    <Form.Item name="cashGraceDays" label="到账宽限天数" rules={[{ required: true, message: '请填写 0–365' }]}>
      <InputNumber min={0} max={365} precision={0} style={{ width: '100%' }} /></Form.Item>
  </div>;
}
