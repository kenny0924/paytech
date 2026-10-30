import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Button, Input, Spin } from 'antd';
import { FileSearchOutlined, FileTextOutlined, RightOutlined, SearchOutlined } from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import { formatAmount } from '../finance/finance';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { formatCountry } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  decodeTaxOrderPage, exampleOrderNo, normalizeOrderNo, readRecentSearches, recentFrom, rememberSearch, taxPaths,
} from '../tax/tax';
import type { RecentSearch, TaxOrderPage } from '../tax/tax';
import { StatusPill, TaxOrderView } from '../tax/TaxOrderView';
import { useTaxDownload } from '../tax/useTaxDownload';
import '../tax/tax.css';

type Outcome = { kind: 'idle' } | { kind: 'loading'; orderNo: string } | { kind: 'notFound'; orderNo: string } | { kind: 'failed'; orderNo: string }
  | { kind: 'result'; page: TaxOrderPage };

/**
 * 财务 → 税务查询（AC-13、AC-14，原型 16—21）：按订单号查看报价与重算、逐行税额、正式登记、申报底稿与税票；页面只读
 * <li>订单号放在地址 ?orderNo=，刷新与浏览器后退保持；进入页面无订单号时显示居中搜索与最近查询（仅存本浏览器）。</li>
 * <li>tax 404 TAX_ORDER_NOT_FOUND 显示"未找到"空状态并保留输入；403 刷新导航权限；其他失败按统一错误提示。</li>
 */
export function FinanceTax({ onAuthorizationChanged }: { onAuthorizationChanged: () => void }) {
  const t = useT('tax'), tNav = useT('nav');
  const notify = useErrorNotification();
  const [params, setParams] = useSearchParams();
  const orderNo = normalizeOrderNo(params.get('orderNo'));
  const [outcome, setOutcome] = useState<Outcome>({ kind: 'idle' });
  const [recent, setRecent] = useState<RecentSearch[]>(() => readRecentSearches());
  const [attempt, setAttempt] = useState(0);
  const pending = useRef<AbortController | null>(null);
  // 回调与翻译函数可能随父组件重渲染变化；查询只随订单号与重试次数触发。
  const latest = useRef({ notify, onAuthorizationChanged, t });
  latest.current = { notify, onAuthorizationChanged, t };
  useEffect(() => {
    pending.current?.abort();
    if (!orderNo) { setOutcome({ kind: 'idle' }); return; }
    const abort = new AbortController(); pending.current = abort;
    setOutcome({ kind: 'loading', orderNo });
    session.request(taxPaths.order(orderNo), { signal: abort.signal }, value => decodeTaxOrderPage(value, orderNo)).then(page => {
      if (abort.signal.aborted) return;
      setRecent(rememberSearch(recentFrom(page)));
      setOutcome({ kind: 'result', page });
    }, error => {
      if (abort.signal.aborted) return;
      if (error instanceof ApiError && error.status === 404 && error.code === 'TAX_ORDER_NOT_FOUND') { setOutcome({ kind: 'notFound', orderNo }); return; }
      if (error instanceof ApiError && error.status === 403) latest.current.onAuthorizationChanged();
      latest.current.notify(error, latest.current.t('lookup.failed'));
      setOutcome({ kind: 'failed', orderNo });
    });
    return () => abort.abort();
  }, [orderNo, attempt]);
  const search = (value: string) => {
    const next = normalizeOrderNo(value);
    if (!next) return false;
    if (next === orderNo) setAttempt(count => count + 1); else setParams({ orderNo: next });
    return true;
  };
  const title = tNav('resource.OPERATIONS_FINANCE_TAX');
  if (outcome.kind === 'idle') return <InitialView title={title} recent={recent} onSearch={search} />;
  const searchBox = <OrderSearch key={orderNo ?? ''} initial={orderNo ?? ''} onSearch={search} />;
  if (outcome.kind === 'result') return <div className="configuration-page tax-page">
    <ResultView page={outcome.page} search={searchBox} onAuthorizationChanged={onAuthorizationChanged} /></div>;
  return <div className="configuration-page tax-page">
    <div className="tax-head"><h1 className="tax-page-title">{title}</h1><div className="tax-head-search">{searchBox}</div></div>
    {outcome.kind === 'loading' && <div className="tax-card tax-empty"><Spin /></div>}
    {outcome.kind === 'notFound' && <EmptyState icon={<FileSearchOutlined />} title={t('notFound.title', { orderNo: outcome.orderNo })}
      description={t('notFound.description')} />}
    {outcome.kind === 'failed' && <EmptyState icon={<FileSearchOutlined />} title={t('lookup.failed')} description={t('lookup.failedDescription')}
      action={<Button onClick={() => setAttempt(count => count + 1)}>{t('lookup.retry')}</Button>} />}
  </div>;
}

/** 结果页：税票下载状态只在结果页存在。 */
function ResultView({ page, search, onAuthorizationChanged }: { page: TaxOrderPage; search: ReactNode; onAuthorizationChanged: () => void }) {
  const download = useTaxDownload(page.orderNo, onAuthorizationChanged);
  return <TaxOrderView page={page} search={search} onDownload={documentId => void download.open(documentId)} downloading={download.pending} />;
}

/** 右上角常驻搜索框：回车查询；格式不合法时标红不发请求。 */
function OrderSearch({ initial, onSearch }: { initial: string; onSearch: (value: string) => boolean }) {
  const t = useT('tax');
  const [value, setValue] = useState(initial);
  const [invalid, setInvalid] = useState(false);
  return <Input className="tax-search" allowClear value={value} status={invalid ? 'error' : undefined} maxLength={64}
    prefix={<SearchOutlined aria-hidden="true" />} placeholder={t('search.placeholder')} aria-label={t('search.label')}
    onChange={event => { setValue(event.target.value); setInvalid(false); }}
    onPressEnter={() => setInvalid(!onSearch(value))} />;
}

/** 进入页面（原型 19）：居中大搜索框、能看到什么、最近查询。 */
function InitialView({ title, recent, onSearch }: { title: string; recent: RecentSearch[]; onSearch: (value: string) => boolean }) {
  const t = useT('tax');
  const [value, setValue] = useState('');
  const [invalid, setInvalid] = useState(false);
  const submit = () => setInvalid(!onSearch(value));
  return <div className="configuration-page tax-page">
    <div className="page-heading"><h1>{title}</h1></div>
    <div className="tax-initial">
      <span className="tax-initial-icon" aria-hidden="true"><FileTextOutlined /></span>
      <h2>{t('initial.title')}</h2>
      <p>{t('initial.description')}</p>
      <div className="tax-initial-search">
        <Input size="large" value={value} status={invalid ? 'error' : undefined} maxLength={64} prefix={<SearchOutlined aria-hidden="true" />}
          placeholder={t('search.placeholderFull', { example: exampleOrderNo })} aria-label={t('search.label')}
          onChange={event => { setValue(event.target.value); setInvalid(false); }} onPressEnter={submit} />
        <Button type="primary" size="large" onClick={submit}>{t('search.submit')}</Button>
      </div>
      {invalid && <div className="tax-initial-error" role="alert">{t('search.invalid')}</div>}
      {recent.length > 0 && <RecentList recent={recent} onOpen={orderNo => onSearch(orderNo)} />}
    </div>
  </div>;
}

/** 最近查询：订单号、Client 与国家、税额、状态与进入箭头。 */
function RecentList({ recent, onOpen }: { recent: RecentSearch[]; onOpen: (orderNo: string) => void }) {
  const t = useT('tax');
  return <section className="tax-card tax-recent">
    <div className="tax-card-head"><h2>{t('recent.title')}</h2><span className="tax-card-extra">{t('recent.localOnly')}</span></div>
    <ul>{recent.map(item => <li key={item.orderNo}>
      <button type="button" className="tax-recent-row" onClick={() => onOpen(item.orderNo)}>
        <span className="tax-recent-main"><span className="tax-recent-order">{item.orderNo}</span>
          <span className="tax-recent-meta">{t('recent.meta', { client: item.buyerClientName || t('header.client', { clientId: item.buyerClientId }),
            place: item.pageStatus === 'NOT_MANAGED' ? item.businessMode : formatCountry(item.countryCode) })}</span></span>
        <span className="tax-recent-amount">{recentAmount(t, item)}</span>
        <StatusPill status={item.pageStatus} compact />
        <RightOutlined aria-hidden="true" className="tax-recent-arrow" />
      </button>
    </li>)}</ul>
  </section>;
}

/** 最近查询的税额：自报 / 计算 / 无可用税额。 */
function recentAmount(t: ReturnType<typeof useT>, item: RecentSearch): string {
  if (!item.taxAmount) return '—';
  const amount = formatAmount(item.taxAmount).text;
  return t(item.taxAmountSource === 'DECLARED' ? 'recent.declared' : 'recent.tax', { amount, currency: item.currency });
}

/** 空状态卡（原型 20）。 */
function EmptyState({ icon, title, description, action }: { icon: ReactNode; title: string; description: string; action?: ReactNode }) {
  return <div className="tax-card tax-empty">
    <span className="tax-empty-icon" aria-hidden="true">{icon}</span>
    <h2>{title}</h2><p>{description}</p>{action}
  </div>;
}
