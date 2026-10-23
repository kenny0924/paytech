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
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
import '../finance/finance.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
/** 标签栏右侧新建按钮的文案 key（finance 命名空间）。 */
const createLabelKeys: Partial<Record<SetupTab, string>> = { entities: 'setup.createEntity', calendars: 'setup.createCalendar', books: 'setup.createBook' };

/** 账务基础资料：财务主体、日历与期间、账簿与账户、科目表四个标签页，标签存于 URL ?tab=；科目维护入口在科目表标签页内。 */
export function FinanceSetup({ resources, onAuthorizationChanged }: Props) {
  const t = useT('finance'), tNav = useT('nav');
  const canManage = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === manageFinanceSetup);
  const [params, setParams] = useSearchParams();
  const tab = readSetupTab(params);
  const [creating, setCreating] = useState<SetupTab | null>(null);
  const [reloadTokens, setReloadTokens] = useState<Record<SetupTab, number>>({ entities: 0, calendars: 0, books: 0, charts: 0 });
  // 日历与账簿的主体列显示财务主体代码；币种提供账簿筛选与金额精度。
  const entities = useFinanceEntities(tab === 'calendars' || tab === 'books', onAuthorizationChanged);
  const currencies = useCurrencies(onAuthorizationChanged);
  const done = (target: SetupTab) => {
    setCreating(null); setReloadTokens(current => ({ ...current, [target]: current[target] + 1 }));
    if (target === 'entities') entities.reload();
  };
  const createLabelKey = createLabelKeys[tab];
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_SETUP')}</h1></div>
    <Tabs activeKey={tab} onChange={next => setParams({ tab: next }, { replace: true })}
      tabBarExtraContent={canManage && createLabelKey ? <Button type="primary" onClick={() => setCreating(tab)}>{t(createLabelKey)}</Button> : undefined}
      items={[
        { key: 'entities', label: t('setup.tab.entities'), children: <SetupEntitiesTab reloadToken={reloadTokens.entities} /> },
        { key: 'calendars', label: t('setup.tab.calendars'), children: <SetupCalendarsTab canManage={canManage} reloadToken={reloadTokens.calendars}
          entities={entities.byId} onAuthorizationChanged={onAuthorizationChanged} /> },
        { key: 'books', label: t('setup.tab.books'), children: <SetupBooksTab canManage={canManage} reloadToken={reloadTokens.books} entities={entities.byId}
          currencies={currencies.items} minorUnits={currencies.minorUnits} onAuthorizationChanged={onAuthorizationChanged} /> },
        { key: 'charts', label: t('field.chart'), children: <SetupChartsTab canManage={canManage} onAuthorizationChanged={onAuthorizationChanged} /> },
      ]} destroyOnHidden />
    {creating === 'entities' && <CreateEntityModal onClose={() => setCreating(null)} onDone={() => done('entities')} onAuthorizationChanged={onAuthorizationChanged} />}
    {creating === 'calendars' && <CreateCalendarModal onClose={() => setCreating(null)} onDone={() => done('calendars')} onAuthorizationChanged={onAuthorizationChanged} />}
    {creating === 'books' && <CreateBookModal onClose={() => setCreating(null)} onDone={() => done('books')} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
