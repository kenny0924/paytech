import { useMemo, useState } from 'react';
import type { Key } from 'react';
import { Alert, Button, Checkbox, Dropdown, Empty, Input, Select, Table, Tag } from 'antd';
import { accountClassLabels, buildAccountTree, chartAccountStatusLabels, expandableKeys, filterAccountTree, referenceStatusLabels } from './finance';
import type { ChartAccount, TreeNode } from './finance';
import { ChartAccountChangesDrawer, ChartAccountFormModal, ToggleChartAccountModal } from './ChartAccountModals';
import { FinanceNote, LoadFailure, SideTag, StatusTag } from './FinanceParts';
import { useChartAccounts, useChartAdoption, useCharts } from './useFinanceData';

/** 当前打开的维护动作：新增（可带上级）、编辑、停用 / 启用、变更记录。 */
type Action = { kind: 'create'; parent?: ChartAccount } | { kind: 'edit' | 'toggle' | 'changes'; account: ChartAccount };

interface Props { canManage: boolean; onAuthorizationChanged: () => void }

/**
 * 科目表：顶部下拉选择科目表，整宽展示所选科目表的科目树（technique 2.5）；币种主数据归 Platform（D41），不在此展示
 * <li>树按编号排序，可展开 / 折叠、按编号 / 名称 / 代码搜索，默认隐藏已停用科目；科目表被账簿采用时显示提示条。</li>
 * <li>新增、编辑、新增下级、停用 / 启用只在维护权限下显示；无维护权限只保留变更记录。</li>
 */
export function SetupChartsTab({ canManage, onAuthorizationChanged }: Props) {
  const charts = useCharts(onAuthorizationChanged);
  const [chosen, setChosen] = useState<string | null>(null);
  const [keyword, setKeyword] = useState(''); const [showInactive, setShowInactive] = useState(false);
  const [expanded, setExpanded] = useState<readonly Key[] | null>(null);
  const [action, setAction] = useState<Action | null>(null); const [reloadToken, setReloadToken] = useState(0);
  // 默认选中第一个科目表。
  const chartCode = chosen ?? charts.items[0]?.chartCode ?? null;
  const accounts = useChartAccounts(chartCode, {}, onAuthorizationChanged, reloadToken);
  const adoption = useChartAdoption(chartCode, onAuthorizationChanged);
  const visible = useMemo(() => accounts.items.filter(item => showInactive || item.accountStatus === 'ACTIVE'), [accounts.items, showInactive]);
  const tree = useMemo(() => filterAccountTree(buildAccountTree(visible), keyword), [visible, keyword]);
  const allKeys = useMemo(() => expandableKeys(tree), [tree]);
  // 默认全部展开；搜索时始终展开命中路径。
  const expandedKeys = keyword.trim() || expanded === null ? allKeys : expanded;
  const done = () => { setAction(null); setReloadToken(value => value + 1); };
  const menu = (row: ChartAccount) => [
    ...(canManage ? [{ key: 'edit', label: '编辑', onClick: () => setAction({ kind: 'edit', account: row }) },
      ...(!row.postingAllowed && row.accountStatus === 'ACTIVE' && row.level < 4
        ? [{ key: 'child', label: '新增下级', onClick: () => setAction({ kind: 'create', parent: row }) }] : []),
      { key: 'toggle', label: row.accountStatus === 'ACTIVE' ? '停用' : '启用', onClick: () => setAction({ kind: 'toggle', account: row }) }] : []),
    { key: 'changes', label: '变更记录', onClick: () => setAction({ kind: 'changes', account: row }) },
  ];
  return <>
    <div className="finance-section-head">
        <Select<string> className="finance-chart-select" value={chartCode ?? undefined} loading={charts.loading} placeholder="选择科目表"
          onChange={code => { setChosen(code); setExpanded(null); }} options={charts.items.map(chart => ({
            value: chart.chartCode, label: <span>{chart.chartCode} · {chart.chartName}{chart.status !== 'ACTIVE' && <span className="muted">（{referenceStatusLabels[chart.status]}）</span>}</span> }))} />
        <span className="cell-meta">{chartCode ? `${visible.length} 个${showInactive ? '' : '启用'}科目` : ''}</span>
        <span className="finance-spacer" />
        <Input.Search allowClear className="finance-tree-search" placeholder="搜索编号 / 名称 / 代码" onSearch={setKeyword}
          onChange={event => { if (!event.target.value) setKeyword(''); }} />
        <Checkbox checked={showInactive} onChange={event => setShowInactive(event.target.checked)}>显示已停用</Checkbox>
        <Button size="small" onClick={() => setExpanded(allKeys)}>全部展开</Button>
        <Button size="small" onClick={() => setExpanded([])}>全部折叠</Button>
        {canManage && chartCode && <Button type="primary" onClick={() => setAction({ kind: 'create' })}>新增科目</Button>}
    </div>
    {charts.error ? <LoadFailure message={charts.error} onRetry={charts.reload} /> : <>
        {adoption.adoptedBy > 0 && <Alert type="info" showIcon className="finance-banner"
          title={`本科目表已被 ${adoption.adoptedBy} 个账簿采用，既有科目的编号与上级不可修改；仍可追加科目、改名、停用与启用。`} />}
        {!chartCode ? <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={charts.loading ? '加载中' : '没有可查看的科目表'} /></div>
          : accounts.error ? <LoadFailure message={accounts.error} onRetry={accounts.reload} /> : <section className="table-panel">
          <Table<TreeNode<ChartAccount>> rowKey="chartAccountId" tableLayout="fixed" dataSource={tree} loading={accounts.loading} pagination={false}
            scroll={{ x: 960 }} expandable={{ expandedRowKeys: [...expandedKeys], onExpandedRowsChange: keys => setExpanded(keys) }}
            rowClassName={row => row.accountStatus === 'INACTIVE' ? 'finance-inactive-row' : row.postingAllowed ? 'finance-posting-row' : ''}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={keyword.trim() ? '没有符合条件的科目' : '该科目表没有科目'} /> }} columns={[
              { title: '编号', width: 170, render: (_, row) => <span className="mono">{row.accountNo}</span> },
              { title: '名称', render: (_, row) => row.postingAllowed ? row.accountName : <strong>{row.accountName}</strong> },
              { title: '代码', width: 260, render: (_, row) => <span className="finance-code">{row.accountCode}</span> },
              { title: '类别', width: 70, render: (_, row) => accountClassLabels[row.accountClass] },
              { title: '方向', width: 64, render: (_, row) => <SideTag side={row.normalSide} /> },
              { title: '类型', width: 70, render: (_, row) => <Tag color={row.postingAllowed ? 'green' : 'default'}>{row.postingAllowed ? '末级' : '汇总'}</Tag> },
              { title: '状态', width: 70, render: (_, row) => <StatusTag status={row.accountStatus} labels={chartAccountStatusLabels} /> },
              { title: '操作', width: 90, render: (_, row) => <Dropdown trigger={['click']} menu={{ items: menu(row) }}>
                <Button size="small">{canManage ? '操作 ▾' : '记录 ▾'}</Button></Dropdown> },
            ]} /></section>}
      </>}
    <FinanceNote>末级科目可开户记账，汇总科目只汇总下级；停用的科目不能新建账户、不能被新规则引用，已有账户与记账不受影响。科目维护不审批、保存即生效，每次变化都记入变更记录。</FinanceNote>
    {chartCode && (action?.kind === 'create' || action?.kind === 'edit') && <ChartAccountFormModal chartCode={chartCode} accounts={accounts.items}
      editing={action.kind === 'edit' ? action.account : undefined} parent={action.kind === 'create' ? action.parent : undefined}
      adopted={adoption.adoptedBy > 0} onClose={() => setAction(null)} onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
    {chartCode && action?.kind === 'toggle' && <ToggleChartAccountModal chartCode={chartCode} account={action.account}
      onClose={() => setAction(null)} onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
    {chartCode && action?.kind === 'changes' && <ChartAccountChangesDrawer chartCode={chartCode} account={action.account} onClose={() => setAction(null)} />}
  </>;
}
