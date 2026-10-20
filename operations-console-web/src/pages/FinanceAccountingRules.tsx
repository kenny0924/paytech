import { useMemo, useState } from 'react';
import { Button, Empty, Table, Tag } from 'antd';
import { useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { localTime, replaceQuery } from '../finance/finance';
import {
  decodeRuleBundle, decodeRuleBundleList, financeActions, hasAction, hasVersionInProgress, ruleBundleName, ruleVersionStatusLabels, standardChartCode, workflowPaths,
} from '../finance/financeWorkflow';
import type { RuleBundle, RuleBundleSummary, RuleVersionSummary } from '../finance/financeWorkflow';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { CreateRuleVersionDrawer, RuleVersionDrawer } from '../finance/RuleVersionDrawers';
import { useChartAccounts } from '../finance/useFinanceData';
import { useDetail, WorkflowTag } from '../finance/WorkflowParts';
import '../configuration/configuration.css';
import '../finance/finance.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

const bundleCodePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
const versionPattern = /^[1-9][0-9]{0,18}$/;

/**
 * 记账规则：规则包列表 → 版本列表（状态、生效时间）→ 版本详情（按指令类型分组的规则行）→ 新建 / 提交 / 审批 / 驳回 / 作废
 * <li>规则包与打开的版本存于 URL（?bundle=&ver=）；规则包中文名由代码映射，未知代码显示代码本身。</li>
 * <li>新建需 …_ACCOUNTING_RULES_MANAGE 且没有草稿或待审批版本；审批 / 驳回需 …_APPROVE 且不是制单人。</li>
 */
export function FinanceAccountingRules({ resources, actorId, onAuthorizationChanged }: Props) {
  const [params, setParams] = useSearchParams();
  const canManage = hasAction(resources, financeActions.rulesManage);
  const canApprove = hasAction(resources, financeActions.rulesApprove);
  const bundleParam = params.get('bundle'); const verParam = params.get('ver');
  const bundleCode = bundleParam && bundleCodePattern.test(bundleParam) ? bundleParam : null;
  const bundleVer = bundleCode && verParam && versionPattern.test(verParam) ? verParam : null;
  const go = (updates: Record<string, string | null>) => setParams(new URLSearchParams(replaceQuery(params.toString(), updates)));
  const bundles = useDetail<RuleBundleSummary[]>(workflowPaths.ruleBundles, decodeRuleBundleList, onAuthorizationChanged, '规则包加载失败');
  const decodeBundle = useMemo(() => decodeRuleBundle(bundleCode ?? ''), [bundleCode]);
  const bundle = useDetail<RuleBundle>(bundleCode ? workflowPaths.ruleBundle(bundleCode) : null, decodeBundle, onAuthorizationChanged, '规则包详情加载失败');
  const chartAccounts = useChartAccounts(standardChartCode, {}, onAuthorizationChanged);
  const accountMap = useMemo(() => new Map(chartAccounts.items.map(item => [item.accountCode, item])), [chartAccounts.items]);
  const [creating, setCreating] = useState(false);
  const refresh = () => { bundle.reload(); bundles.reload(); };
  const current = bundle.item;
  const canCreate = canManage && !!current && !hasVersionInProgress(current.versions);
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>记账规则</h1></div>
    {bundles.error ? <LoadFailure message={bundles.error} onRetry={bundles.reload} /> : <section className="table-panel">
      <Table<RuleBundleSummary> rowKey="bundleCode" tableLayout="fixed" dataSource={bundles.item ?? []} loading={bundles.loading} pagination={false}
        rowClassName={row => row.bundleCode === bundleCode ? 'finance-row-selected' : ''}
        onRow={row => ({ onClick: () => go({ bundle: row.bundleCode, ver: null }), className: 'clickable-row' })}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有规则包（规则包由初始化数据定义）" /> }} columns={[
          { title: '规则包', render: (_, row) => <span>{ruleBundleName(row.bundleCode)}<div className="cell-meta mono">{row.bundleCode}</div></span> },
          { title: '状态', width: 100, render: (_, row) => <WorkflowTag status={row.status} /> },
          { title: '当前生效版本', width: 140, render: (_, row) => row.currentBundleVer ?? <span className="muted">无</span> },
          { title: '生效时间', width: 180, render: (_, row) => row.currentEffectiveFrom ? localTime(row.currentEffectiveFrom) : '—' },
        ]} />
    </section>}
    {!bundleCode ? <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择规则包后查看版本" /></div>
      : <section className="finance-section">
        <div className="finance-section-head"><h2>{ruleBundleName(bundleCode)} · 版本</h2><span className="finance-spacer" />
          {canManage && current && (canCreate ? <Button type="primary" onClick={() => setCreating(true)}>新建版本</Button>
            : <Tag color="gold">已有草稿或待审批版本，处理后才能新建</Tag>)}</div>
        {bundle.error ? <LoadFailure message={bundle.error} onRetry={bundle.reload} /> : <div className="table-panel">
          <Table<RuleVersionSummary> rowKey="versionId" tableLayout="fixed" dataSource={current?.versions ?? []} loading={bundle.loading} pagination={false}
            onRow={row => ({ onClick: () => go({ ver: row.bundleVer }), className: 'clickable-row' })}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该规则包还没有版本" /> }} columns={[
              { title: '版本', width: 100, render: (_, row) => <a className="mono">{row.bundleVer}</a> },
              { title: '状态', width: 120, render: (_, row) => <span><WorkflowTag status={row.status} labels={ruleVersionStatusLabels} />
                {row.bundleVer === current?.currentBundleVer && <Tag color="green">当前</Tag>}</span> },
              { title: '生效时间', render: (_, row) => localTime(row.effectiveFrom) },
              { title: '失效时间', render: (_, row) => row.effectiveTo ? localTime(row.effectiveTo) : '—' },
            ]} />
        </div>}
      </section>}
    <FinanceNote>规则只影响生效后的新事实，不重算历史分录。审批时生效时间已过则按审批时间立即生效、旧版本退役；迟到的事实按其业务时间所在的版本记账；未来生效的版本为「待生效」，由定时任务每分钟切换。{!canManage && ' 当前账号没有制单权限。'}{!canApprove && ' 当前账号没有复核权限。'}</FinanceNote>
    {creating && current && <CreateRuleVersionDrawer bundleCode={current.bundleCode} currentBundleVer={current.currentBundleVer} chartAccounts={chartAccounts.items}
      accounts={accountMap} onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={version => { setCreating(false); refresh(); go({ ver: version.bundleVer }); }} />}
    {bundleCode && bundleVer && <RuleVersionDrawer key={`${bundleCode}:${bundleVer}`} bundleCode={bundleCode} bundleVer={bundleVer} accounts={accountMap}
      canManage={canManage} canApprove={canApprove} actorId={actorId} onClose={() => go({ ver: null })} onChanged={refresh} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
