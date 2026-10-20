import { Tabs } from 'antd';
import { useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { BillingFactsTab } from '../finance/BillingFactsTab';
import { financeActions, hasAction } from '../finance/financeWorkflow';
import { ManualAdjustmentsTab } from '../finance/ManualAdjustmentsTab';
import '../configuration/configuration.css';
import '../finance/finance.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
type AdjustmentTab = 'manual' | 'billing';

/**
 * 调整：「人工调整」（非 Owner 科目的总账调整）与「计费事实」（影响 Client / Partner 余额的 MANUAL_ADJUSTMENT）两个页签，页签存于 URL ?tab=
 * <li>制单 / 登记 / 作废按 OPERATIONS_FINANCE_ADJUSTMENTS_MANAGE 显示，审批 / 驳回按 …_APPROVE 显示且制单人本人不显示；切换页签清空另一页签的条件。</li>
 */
export function FinanceAdjustments({ resources, actorId, onAuthorizationChanged }: Props) {
  const [params, setParams] = useSearchParams();
  const tab: AdjustmentTab = params.get('tab') === 'billing' ? 'billing' : 'manual';
  const canManage = hasAction(resources, financeActions.adjustmentsManage);
  const canApprove = hasAction(resources, financeActions.adjustmentsApprove);
  const props = { canManage, canApprove, actorId, onAuthorizationChanged };
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>调整</h1></div>
    <Tabs activeKey={tab} onChange={next => setParams({ tab: next }, { replace: true })} destroyOnHidden items={[
      { key: 'manual', label: '人工调整', children: <ManualAdjustmentsTab {...props} /> },
      { key: 'billing', label: '计费事实', children: <BillingFactsTab {...props} /> },
    ]} />
  </div>;
}
