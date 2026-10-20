import { useState } from 'react';
import { Button, Tabs } from 'antd';
import { useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { manageFinanceSetup, readSetupTab } from '../finance/finance';
import type { SetupTab } from '../finance/finance';
import { CreateBookModal, CreateCalendarModal, CreateEntityModal } from '../finance/FinanceModals';
import { SetupBooksTab } from '../finance/SetupBooksTab';
import { SetupCalendarsTab } from '../finance/SetupCalendarsTab';
import { SetupChartsTab } from '../finance/SetupChartsTab';
import { SetupEntitiesTab } from '../finance/SetupEntitiesTab';
import { useCurrencies, useFinanceEntities } from '../finance/useFinanceData';
import '../configuration/configuration.css';
import '../finance/finance.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const createLabels: Partial<Record<SetupTab, string>> = { entities: '新建财务主体', calendars: '新建日历', books: '新建账簿' };

/** 账务基础资料：财务主体、日历与期间、账簿与账户、科目表与币种四个标签页，标签存于 URL ?tab=；科目维护入口在科目表标签页内。 */
export function FinanceSetup({ resources, onAuthorizationChanged }: Props) {
  const canManage = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === manageFinanceSetup);
  const [params, setParams] = useSearchParams();
  const tab = readSetupTab(params);
  const [creating, setCreating] = useState<SetupTab | null>(null);
  const [reloadTokens, setReloadTokens] = useState<Record<SetupTab, number>>({ entities: 0, calendars: 0, books: 0, charts: 0 });
  // 日历与账簿的主体列显示财务主体代码；币种同时提供账簿筛选、金额精度与只读标签页。
  const entities = useFinanceEntities(tab === 'calendars' || tab === 'books', onAuthorizationChanged);
  const currencies = useCurrencies(onAuthorizationChanged);
  const done = (target: SetupTab) => {
    setCreating(null); setReloadTokens(current => ({ ...current, [target]: current[target] + 1 }));
    if (target === 'entities') entities.reload();
  };
  const createLabel = createLabels[tab];
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>账务基础资料</h1></div>
    <Tabs activeKey={tab} onChange={next => setParams({ tab: next }, { replace: true })}
      tabBarExtraContent={canManage && createLabel ? <Button type="primary" onClick={() => setCreating(tab)}>{createLabel}</Button> : undefined}
      items={[
        { key: 'entities', label: '财务主体', children: <SetupEntitiesTab reloadToken={reloadTokens.entities} /> },
        { key: 'calendars', label: '日历与期间', children: <SetupCalendarsTab canManage={canManage} reloadToken={reloadTokens.calendars}
          entities={entities.byId} onAuthorizationChanged={onAuthorizationChanged} /> },
        { key: 'books', label: '账簿与账户', children: <SetupBooksTab canManage={canManage} reloadToken={reloadTokens.books} entities={entities.byId}
          currencies={currencies.items} minorUnits={currencies.minorUnits} onAuthorizationChanged={onAuthorizationChanged} /> },
        { key: 'charts', label: '科目表与币种', children: <SetupChartsTab canManage={canManage} currencies={currencies} onAuthorizationChanged={onAuthorizationChanged} /> },
      ]} destroyOnHidden />
    {creating === 'entities' && <CreateEntityModal onClose={() => setCreating(null)} onDone={() => done('entities')} onAuthorizationChanged={onAuthorizationChanged} />}
    {creating === 'calendars' && <CreateCalendarModal onClose={() => setCreating(null)} onDone={() => done('calendars')} onAuthorizationChanged={onAuthorizationChanged} />}
    {creating === 'books' && <CreateBookModal onClose={() => setCreating(null)} onDone={() => done('books')} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
