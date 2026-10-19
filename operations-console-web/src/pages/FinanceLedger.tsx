import { LedgerDrilldown } from '../finance/LedgerDrilldown';
import '../configuration/configuration.css';
import '../finance/finance.css';

/** 账本查询（只读）：账簿 › 账户 › 流水 › 分录详情。 */
export function FinanceLedger({ onAuthorizationChanged }: { onAuthorizationChanged: () => void }) {
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>账本查询</h1></div>
    <LedgerDrilldown onAuthorizationChanged={onAuthorizationChanged} />
  </div>;
}
