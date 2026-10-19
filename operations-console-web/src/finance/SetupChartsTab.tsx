import { useState } from 'react';
import { Checkbox, Empty, Table, Tag } from 'antd';
import { referenceStatusLabels } from './finance';
import type { ChartAccount, ChartOfAccounts, Currency } from './finance';
import { FinanceNote, LoadFailure, SideTag, StatusTag } from './FinanceParts';
import { useChartAccounts, useCharts } from './useFinanceData';
import type { ListState } from './useFinanceData';

/** 科目表与币种（只读）：左栏科目表与币种，右栏所选科目表的科目；任何权限都不显示新建。 */
export function SetupChartsTab({ currencies, onAuthorizationChanged }: { currencies: ListState<Currency>; onAuthorizationChanged: () => void }) {
  const charts = useCharts(onAuthorizationChanged);
  const [chosen, setChosen] = useState<string | null>(null);
  const [postingOnly, setPostingOnly] = useState(false);
  // 默认选中第一个科目表。
  const chartCode = chosen ?? charts.items[0]?.chartCode ?? null;
  const accounts = useChartAccounts(chartCode, postingOnly || undefined, onAuthorizationChanged);
  return <>
    <div className="finance-charts">
      <div className="finance-charts-side">
        <h2>科目表</h2>
        {charts.error ? <LoadFailure message={charts.error} onRetry={charts.reload} /> : <section className="table-panel">
          <Table<ChartOfAccounts> rowKey="chartCode" size="small" dataSource={charts.items} loading={charts.loading} pagination={false}
            rowClassName={row => row.chartCode === chartCode ? 'finance-row-selected' : 'clickable-row'} onRow={row => ({ onClick: () => setChosen(row.chartCode) })}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有科目表" /> }} columns={[
              { title: '代码', dataIndex: 'chartCode' },
              { title: '名称', dataIndex: 'chartName' },
              { title: '状态', width: 80, render: (_, row) => <StatusTag status={row.status} labels={referenceStatusLabels} /> },
            ]} /></section>}
        <div className="cell-meta finance-side-meta">科目表被账簿采用后，科目的父子关系与可过账属性不可修改。</div>
        <h2>币种</h2>
        {currencies.error ? <LoadFailure message={currencies.error} onRetry={currencies.reload} /> : <section className="table-panel">
          <Table<Currency> rowKey="currency" size="small" dataSource={currencies.items} loading={currencies.loading} pagination={false}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有币种" /> }} columns={[
              { title: '代码', dataIndex: 'currency' },
              { title: '小数位', dataIndex: 'minorUnit', width: 70 },
              { title: '舍入', dataIndex: 'roundingMode' },
              { title: '状态', width: 80, render: (_, row) => <StatusTag status={row.status} labels={referenceStatusLabels} /> },
            ]} /></section>}
      </div>
      <div className="finance-charts-main">
        <div className="finance-section-head">
          <h2>科目{chartCode ? ` · ${chartCode}（${accounts.items.length} 个${postingOnly ? '可过账' : ''}科目）` : ''}</h2>
          <span className="finance-spacer" />
          <Checkbox checked={postingOnly} onChange={event => setPostingOnly(event.target.checked)}>只看可过账科目</Checkbox>
        </div>
        {!chartCode ? <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有可查看的科目表" /></div>
          : accounts.error ? <LoadFailure message={accounts.error} onRetry={accounts.reload} /> : <section className="table-panel">
          <Table<ChartAccount> rowKey="chartAccountId" tableLayout="fixed" dataSource={accounts.items} loading={accounts.loading} pagination={false} scroll={{ x: 640 }}
            rowClassName={row => row.postingAllowed ? 'finance-posting-row' : ''}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该科目表没有科目" /> }} columns={[
              { title: '科目代码', width: 240, render: (_, row) => <span className="finance-code">{row.accountCode}</span> },
              { title: '科目名称', dataIndex: 'accountName' },
              { title: '类别', dataIndex: 'accountClass', width: 110 },
              { title: '方向', width: 70, render: (_, row) => <SideTag side={row.normalSide} /> },
              { title: '可过账', width: 80, render: (_, row) => <Tag color={row.postingAllowed ? 'green' : 'default'}>{row.postingAllowed ? '是' : '否'}</Tag> },
            ]} /></section>}
      </div>
    </div>
    <FinanceNote>科目表、科目与币种本期只读，来自 Finance 初始化基线；新增科目、新增币种与启停随第三批记账规则配置一起提供。</FinanceNote>
  </>;
}
