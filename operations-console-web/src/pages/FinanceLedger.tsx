import { Tabs } from 'antd';
import type { ConsoleResource } from '../api/types';
import { useLocation, useNavigate } from 'react-router-dom';
import { ledgerTabSearch, readLedgerTab } from '../finance/finance';
import type { LedgerTab } from '../finance/finance';
import { financeActions, hasAction } from '../finance/financeWorkflow';
import { LedgerDrilldown } from '../finance/LedgerDrilldown';
import { TrialBalanceTab } from '../finance/TrialBalanceTab';
import '../configuration/configuration.css';
import '../finance/finance.css';

/** 账本查询：「账簿下钻」账簿 › 账户 › 流水 › 分录详情；「试算平衡」科目余额与试算记录，有发起试算权限时可发起试算。页签存于 URL ?tab=。 */
export function FinanceLedger({ resources, onAuthorizationChanged }: { resources: ConsoleResource[]; onAuthorizationChanged: () => void }) {
  const location = useLocation(); const navigate = useNavigate();
  const tab = readLedgerTab(new URLSearchParams(location.search));
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>账本查询</h1></div>
    <Tabs activeKey={tab} onChange={next => void navigate({ search: ledgerTabSearch(location.search, next as LedgerTab) })} destroyOnHidden items={[
      { key: 'drilldown', label: '账簿下钻', children: <LedgerDrilldown onAuthorizationChanged={onAuthorizationChanged} /> },
      { key: 'trial', label: '试算平衡', children: <TrialBalanceTab canRun={hasAction(resources, financeActions.ledgerManage)} onAuthorizationChanged={onAuthorizationChanged} /> },
    ]} />
  </div>;
}
