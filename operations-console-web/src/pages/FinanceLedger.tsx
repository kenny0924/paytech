import { Tabs } from 'antd';
import type { ConsoleResource } from '../api/types';
import { useLocation, useNavigate } from 'react-router-dom';
import { ledgerTabSearch, readLedgerTab } from '../finance/finance';
import type { LedgerTab } from '../finance/finance';
import { financeActions, hasAction } from '../finance/financeWorkflow';
import { LedgerDrilldown } from '../finance/LedgerDrilldown';
import { TrialBalanceTab } from '../finance/TrialBalanceTab';
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
import '../finance/finance.css';

/** 账本查询：「账簿下钻」账簿 › 账户 › 流水 › 分录详情；「试算平衡」科目余额与试算记录，有发起试算权限时可发起试算。页签存于 URL ?tab=。 */
export function FinanceLedger({ resources, onAuthorizationChanged }: { resources: ConsoleResource[]; onAuthorizationChanged: () => void }) {
  const t = useT('finance'), tNav = useT('nav');
  const location = useLocation(); const navigate = useNavigate();
  const tab = readLedgerTab(new URLSearchParams(location.search));
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_LEDGER')}</h1></div>
    <Tabs activeKey={tab} onChange={next => void navigate({ search: ledgerTabSearch(location.search, next as LedgerTab) })} destroyOnHidden items={[
      { key: 'drilldown', label: t('ledger.tab.drilldown'), children: <LedgerDrilldown onAuthorizationChanged={onAuthorizationChanged} /> },
      { key: 'trial', label: t('ledger.tab.trial'), children: <TrialBalanceTab canRun={hasAction(resources, financeActions.ledgerManage)} onAuthorizationChanged={onAuthorizationChanged} /> },
    ]} />
  </div>;
}
