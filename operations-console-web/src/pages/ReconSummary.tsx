import { useState } from 'react';
import { Button, Form, Input, Table } from 'antd';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { useDetail } from '../finance/WorkflowParts';
import { ageBucketLabels, decodeSummary, reconPaths } from '../reconciliation/reconModel';
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
  const [form] = Form.useForm<Query>();
  const [query, setQuery] = useState<Query | null>(null);
  const path = query ? `${reconPaths.summary}?${new URLSearchParams({ ...(query.entityNo ? { entityNo: query.entityNo } : {}), currency: query.currency })}` : null;
  const summary = useDetail<Summary>(path, query ? decodeSummary(query.currency) : (value => value as Summary), onAuthorizationChanged, '在途汇总加载失败');
  const item = summary.item;
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>在途汇总</h1></div>
    <Form form={form} name="recon-summary" layout="vertical" initialValues={{ currency: 'USD' }}
      onFinish={values => setQuery({ entityNo: values.entityNo?.trim() || undefined, currency: values.currency.trim() })}>
      <div className="finance-search">
        <Form.Item name="entityNo" label="经营主体" rules={[{ pattern: /^[A-Z]{2}[0-9]{3}$/, message: '两位大写字母加三位数字' }]}><Input allowClear placeholder="全部" maxLength={5} /></Form.Item>
        <Form.Item name="currency" label="币种" rules={[{ required: true, pattern: /^[A-Z]{3}$/, message: '三位大写币种' }]}><Input maxLength={3} /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit" type="primary">查询</Button></div>
      </div>
    </Form>
    {!query ? <div className="finance-empty"><EmptyHint text="选择币种（可选经营主体）后查看在途汇总" /></div>
      : summary.error ? <LoadFailure message={summary.error} onRetry={summary.reload} />
      : item && <div className="recon-summary-cards">
        <section className="recon-summary-card"><h2>在途渠道账单</h2>
          <dl className="finance-summary">
            <dt>匹配中（未对平）</dt><dd>{item.inTransitStatements.matchingCount} 张 · <Money value={item.inTransitStatements.matchingNetAmount} /></dd>
            <dt>已对平未到账</dt><dd>{item.inTransitStatements.reconciledCount} 张 · <Money value={item.inTransitStatements.reconciledUnconfirmedAmount} /></dd>
          </dl>
          <Link to="/finance/reconciliation/statements">查看渠道账单</Link></section>
        <section className="recon-summary-card"><h2>开放异常（按账龄）</h2>
          <Table size="small" rowKey="bucket" pagination={false} dataSource={item.openExceptionsByAge} columns={[
            { title: '账龄', render: (_, row) => ageBucketLabels[row.bucket] ?? row.bucket },
            { title: '数量', align: 'right', dataIndex: 'count' },
            { title: '阻塞对平', align: 'right', dataIndex: 'blockingCount' },
          ]} />
          <Link to="/finance/reconciliation/exceptions">去处置</Link></section>
        <section className="recon-summary-card"><h2>未匹配银行流水</h2>
          <dl className="finance-summary">
            <dt>条数</dt><dd>{item.unmatchedBankLines.count}</dd>
            <dt>贷方未匹配</dt><dd><Money value={item.unmatchedBankLines.creditAmount} /></dd>
            <dt>借方未匹配</dt><dd><Money value={item.unmatchedBankLines.debitAmount} /></dd>
          </dl>
          <Link to="/finance/reconciliation/bank">去银行对账</Link></section>
      </div>}
    <FinanceNote>金额为 {query?.currency ?? '所选币种'} 主单位；带符号金额贷方为正、借方为负。</FinanceNote>
  </div>;
}
