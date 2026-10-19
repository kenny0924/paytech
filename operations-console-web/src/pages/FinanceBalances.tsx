import { useRef, useState } from 'react';
import { Button, Empty, Form, Input, Radio, Select, Spin } from 'antd';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import {
  activeOnly, balanceCards, balanceErrorTargets, balanceFieldName, balanceRequestPath, decimalIdPattern, decodeClientBalance,
  decodePartnerBalance, entityNoPattern, financeErrorTarget, ownerAccountNotFoundMessage,
} from '../finance/finance';
import type { BalanceFormValues, ClientBalance, PartnerBalance } from '../finance/finance';
import { Amount, FinanceNote } from '../finance/FinanceParts';
import { useCurrencies } from '../finance/useFinanceData';
import '../configuration/configuration.css';
import '../finance/finance.css';

type Outcome = { kind: 'idle' } | { kind: 'loading' } | { kind: 'notFound' } | { kind: 'failed' }
  | { kind: 'result'; query: BalanceFormValues; balance: ClientBalance | PartnerBalance };

/** 余额查询（只读）：Client / Local Partner + Owner ID + entityNo + 币种 → 应付三桶与保证金 / 追偿应收；不提供跨主体或跨币种合计。 */
export function FinanceBalances({ onAuthorizationChanged }: { onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm<BalanceFormValues>();
  const notify = useErrorNotification();
  const currencies = useCurrencies(onAuthorizationChanged);
  const [outcome, setOutcome] = useState<Outcome>({ kind: 'idle' });
  const pending = useRef<AbortController | null>(null);
  async function search(values: BalanceFormValues) {
    pending.current?.abort();
    const abort = new AbortController(); pending.current = abort;
    setOutcome({ kind: 'loading' });
    const ownerId = values.ownerId.trim(); const entityNo = values.entityNo.trim();
    try {
      const balance = await session.request<ClientBalance | PartnerBalance>(balanceRequestPath(values), { signal: abort.signal },
        value => values.ownerType === 'CLIENT' ? decodeClientBalance(value, ownerId, entityNo, values.currency)
          : decodePartnerBalance(value, ownerId, entityNo, values.currency));
      if (!abort.signal.aborted) setOutcome({ kind: 'result', query: { ...values, ownerId, entityNo }, balance });
    } catch (error) {
      if (abort.signal.aborted) return;
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      // OWNER_ACCOUNT_NOT_FOUND 挂在 Owner ID 并带上 Owner 类型与查询范围（画板 11）。
      const target = financeErrorTarget(error, ['ownerId'], balanceErrorTargets);
      if (target?.field) {
        form.setFields([{ name: target.field as keyof BalanceFormValues, errors: [ownerAccountNotFoundMessage(values)] }]);
        setOutcome({ kind: 'notFound' });
        return;
      }
      const fields = error instanceof ApiError ? error.fieldErrors.flatMap(item => {
        const name = balanceFieldName(item.field) as keyof BalanceFormValues | null; return name ? [{ name, errors: [item.message] }] : [];
      }) : [];
      if (fields.length) form.setFields(fields); else notify(error, '余额查询失败');
      setOutcome({ kind: 'failed' });
    }
  }
  const ownerType = Form.useWatch('ownerType', form);
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>余额查询</h1></div>
    <Form form={form} name="finance-balance-query" layout="vertical" initialValues={{ ownerType: 'CLIENT' }} onFinish={values => void search(values)}
      onValuesChange={() => { if (outcome.kind === 'notFound') setOutcome({ kind: 'idle' }); }}>
      <div className="finance-search finance-balance-search">
        <Form.Item name="ownerType" label="Owner 类型"><Radio.Group optionType="button" buttonStyle="solid"
          options={[{ value: 'CLIENT', label: 'Client' }, { value: 'PARTNER', label: 'Local Partner' }]} /></Form.Item>
        <Form.Item name="ownerId" label="Owner ID" rules={[{ required: true, whitespace: true, message: '请填写 Owner ID' },
          { pattern: decimalIdPattern, message: '必须是正十进制整数字符串' }]}>
          <Input maxLength={19} placeholder={ownerType === 'PARTNER' ? 'Local Partner ID' : 'Client ID'} /></Form.Item>
        <Form.Item name="entityNo" label="经营主体 entityNo" normalize={(value: string) => value?.toUpperCase()}
          rules={[{ required: true, whitespace: true, message: '请填写 entityNo' }, { pattern: entityNoPattern, message: '必须是两位大写国家码加三位序号，如 AE001' }]}>
          <Input maxLength={5} placeholder="AE001" /></Form.Item>
        <Form.Item name="currency" label="币种" rules={[{ required: true, message: '请选择币种' }]}>
          <Select loading={currencies.loading} placeholder="启用币种" options={activeOnly(currencies.items).map(item => ({ value: item.currency, label: item.currency }))} /></Form.Item>
        <div className="configuration-actions"><Button type="primary" htmlType="submit" loading={outcome.kind === 'loading'}>查询</Button></div>
      </div>
    </Form>
    <BalanceResult outcome={outcome} minorUnits={currencies.minorUnits} />
  </div>;
}

function BalanceResult({ outcome, minorUnits }: { outcome: Outcome; minorUnits: ReadonlyMap<string, number> }) {
  if (outcome.kind === 'loading') return <div className="finance-empty"><Spin /></div>;
  if (outcome.kind === 'idle' || outcome.kind === 'failed') return <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
    description="选择 Owner 类型并填写 Owner ID、entityNo 与币种后查询" /></div>;
  if (outcome.kind === 'notFound') return <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
    description="该 Owner 在此经营主体与币种下没有账户，请确认 Owner ID、entityNo 与币种后重新查询" /></div>;
  const { query, balance } = outcome;
  const minorUnit = minorUnits.get(balance.currency);
  return <>
    <div className="finance-balance-cards">
      {balanceCards(balance).map(card => <div key={card.key} className="finance-balance-card">
        <div className="finance-balance-label">{card.label}{card.code && <div>{card.code}</div>}</div>
        <div className="finance-balance-amount"><Amount value={card.amount.amount} minorUnit={minorUnit} suffix={card.amount.currency} /></div>
        <div className="cell-meta">{card.note}</div>
      </div>)}
    </div>
    <div className="finance-scope"><span className="muted">查询范围</span>
      {query.ownerType === 'CLIENT' ? 'Client' : 'Local Partner'} {query.ownerId} · {query.entityNo} · {balance.currency}</div>
    <FinanceNote tone="warning">Available 不等于可出款资格：出款需经 Risk 四个检查点与 Finance 本地门禁。每次只查一个 Owner + 经营主体 + 币种，不提供跨主体或跨币种合计；Partner 不显示保证金字段。</FinanceNote>
  </>;
}
