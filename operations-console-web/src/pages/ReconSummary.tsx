import { useState } from 'react';
import { Button, Form, Input, Table } from 'antd';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { useDetail } from '../finance/WorkflowParts';
import { useT } from '../i18n/react';
import { ageBucketEnum, decodeSummary, reconLabel, reconPaths } from '../reconciliation/reconModel';
import type { ReconSummary as Summary } from '../reconciliation/reconModel';
import { EmptyHint, Money } from '../reconciliation/ReconParts';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface Query { entityNo?: string; currency: string }

/**
 * 在途汇总（F5）：在途渠道账单（未对平 / 已对平未到账）、开放异常按账龄段、未匹配银行流水
 * <li>币种必填，经营主体可选（不填汇总全部）；只读页面，不需要按钮资源。</li>
 */
export function ReconSummary({ onAuthorizationChanged }: Props) {
  const t = useT('recon'), tNav = useT('nav');
  const [form] = Form.useForm<Query>();
  const [query, setQuery] = useState<Query | null>(null);
  const path = query ? `${reconPaths.summary}?${new URLSearchParams({ ...(query.entityNo ? { entityNo: query.entityNo } : {}), currency: query.currency })}` : null;
  const summary = useDetail<Summary>(path, query ? decodeSummary(query.currency) : (value => value as Summary), onAuthorizationChanged, t('summary.loadFailed'));
  const item = summary.item;
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_RECON_SUMMARY')}</h1></div>
    <Form form={form} name="recon-summary" layout="vertical" initialValues={{ currency: 'USD' }}
      onFinish={values => setQuery({ entityNo: values.entityNo?.trim() || undefined, currency: values.currency.trim() })}>
      <div className="finance-search">
        <Form.Item name="entityNo" label={t('field.operatingEntity')} rules={[{ pattern: /^[A-Z]{2}[0-9]{3}$/, message: t('validation.entityNo') }]}><Input allowClear placeholder={t('placeholder.all')} maxLength={5} /></Form.Item>
        <Form.Item name="currency" label={t('field.currency')} rules={[{ required: true, pattern: /^[A-Z]{3}$/, message: t('validation.currency') }]}><Input maxLength={3} /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit" type="primary">{t('common.search')}</Button></div>
      </div>
    </Form>
    {!query ? <div className="finance-empty"><EmptyHint text={t('summary.idle')} /></div>
      : summary.error ? <LoadFailure message={summary.error} onRetry={summary.reload} />
      : item && <div className="recon-summary-cards">
        <section className="recon-summary-card"><h2>{t('summary.inTransitStatements')}</h2>
          <dl className="finance-summary">
            <dt>{t('summary.matching')}</dt><dd>{t('summary.statementCount', { count: item.inTransitStatements.matchingCount })} · <Money value={item.inTransitStatements.matchingNetAmount} /></dd>
            <dt>{t('summary.reconciledUnconfirmed')}</dt><dd>{t('summary.statementCount', { count: item.inTransitStatements.reconciledCount })} · <Money value={item.inTransitStatements.reconciledUnconfirmedAmount} /></dd>
          </dl>
          <Link to="/finance/reconciliation/statements">{t('summary.viewStatements')}</Link></section>
        <section className="recon-summary-card"><h2>{t('summary.openExceptionsByAge')}</h2>
          <Table size="small" rowKey="bucket" pagination={false} dataSource={item.openExceptionsByAge} columns={[
            { title: t('field.age'), render: (_, row) => reconLabel(t, ageBucketEnum, row.bucket) },
            { title: t('summary.count'), align: 'right', dataIndex: 'count' },
            { title: t('summary.blocking'), align: 'right', dataIndex: 'blockingCount' },
          ]} />
          <Link to="/finance/reconciliation/exceptions">{t('summary.goResolve')}</Link></section>
        <section className="recon-summary-card"><h2>{t('summary.unmatchedBankLines')}</h2>
          <dl className="finance-summary">
            <dt>{t('summary.lineCount')}</dt><dd>{item.unmatchedBankLines.count}</dd>
            <dt>{t('summary.creditUnmatched')}</dt><dd><Money value={item.unmatchedBankLines.creditAmount} /></dd>
            <dt>{t('summary.debitUnmatched')}</dt><dd><Money value={item.unmatchedBankLines.debitAmount} /></dd>
          </dl>
          <Link to="/finance/reconciliation/bank">{t('summary.goBank')}</Link></section>
      </div>}
    <FinanceNote>{query ? t('summary.note', { currency: query.currency }) : t('summary.noteNoCurrency')}</FinanceNote>
  </div>;
}
