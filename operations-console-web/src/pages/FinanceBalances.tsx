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
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
import '../finance/finance.css';

type Outcome = { kind: 'idle' } | { kind: 'loading' } | { kind: 'notFound' } | { kind: 'failed' }
  | { kind: 'result'; query: BalanceFormValues; balance: ClientBalance | PartnerBalance };

/** 余额查询（只读）：Client / Local Partner + Owner ID + entityNo + 币种 → 应付三桶与保证金 / 追偿应收；不提供跨主体或跨币种合计。 */
export function FinanceBalances({ onAuthorizationChanged }: { onAuthorizationChanged: () => void }) {
  const t = useT('finance'), tNav = useT('nav');
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
      if (fields.length) form.setFields(fields); else notify(error, t('balance.queryFailed'));
      setOutcome({ kind: 'failed' });
    }
  }
  const ownerType = Form.useWatch('ownerType', form);
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_BALANCES')}</h1></div>
    <Form form={form} name="finance-balance-query" layout="vertical" initialValues={{ ownerType: 'CLIENT' }} onFinish={values => void search(values)}
      onValuesChange={() => { if (outcome.kind === 'notFound') setOutcome({ kind: 'idle' }); }}>
      <div className="finance-search finance-balance-search">
        <Form.Item name="ownerType" label={t('field.ownerType')}><Radio.Group optionType="button" buttonStyle="solid"
          options={[{ value: 'CLIENT', label: 'Client' }, { value: 'PARTNER', label: 'Local Partner' }]} /></Form.Item>
        <Form.Item name="ownerId" label={t('field.ownerId')} rules={[{ required: true, whitespace: true, message: t('balance.ownerIdRequired') },
          { pattern: decimalIdPattern, message: t('validation.decimalId') }]}>
          <Input maxLength={19} placeholder={ownerType === 'PARTNER' ? 'Local Partner ID' : 'Client ID'} /></Form.Item>
        <Form.Item name="entityNo" label={t('balance.field.entityNo')} normalize={(value: string) => value?.toUpperCase()}
          rules={[{ required: true, whitespace: true, message: t('modals.entityNoRequired') }, { pattern: entityNoPattern, message: t('validation.entityNoPattern') }]}>
          <Input maxLength={5} placeholder="AE001" /></Form.Item>
        <Form.Item name="currency" label={t('field.currency')} rules={[{ required: true, message: t('validation.currencyRequired') }]}>
          <Select loading={currencies.loading} placeholder={t('placeholder.activeCurrency')} options={activeOnly(currencies.items).map(item => ({ value: item.currency, label: item.currency }))} /></Form.Item>
        <div className="configuration-actions"><Button type="primary" htmlType="submit" loading={outcome.kind === 'loading'}>{t('common.search')}</Button></div>
      </div>
    </Form>
    <BalanceResult outcome={outcome} minorUnits={currencies.minorUnits} />
  </div>;
}

function BalanceResult({ outcome, minorUnits }: { outcome: Outcome; minorUnits: ReadonlyMap<string, number> }) {
  const t = useT('finance');
  if (outcome.kind === 'loading') return <div className="finance-empty"><Spin /></div>;
  if (outcome.kind === 'idle' || outcome.kind === 'failed') return <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
    description={t('balance.idle')} /></div>;
  if (outcome.kind === 'notFound') return <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
    description={t('balance.notFound')} /></div>;
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
    <div className="finance-scope"><span className="muted">{t('balance.scope')}</span>
      {query.ownerType === 'CLIENT' ? 'Client' : 'Local Partner'} {query.ownerId} · {query.entityNo} · {balance.currency}</div>
    <FinanceNote tone="warning">{t('balance.note')}</FinanceNote>
  </>;
}
