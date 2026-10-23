import type { ReactNode } from 'react';
import { Breadcrumb, Button, Result, Spin } from 'antd';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { LoadFailure } from '../finance/FinanceParts';
import { useT } from '../i18n/react';
import { backTarget } from './reconRoutes';
import type { ReconDetailState } from './useReconDetail';
import '../configuration/configuration.css';
import '../finance/finance.css';
import './recon.css';

interface ListRef { label: string; route: string }

/** 列表 → 详情：带上当前列表地址（含筛选与页码），详情页「返回」据此恢复列表。 */
export function useOpenDetail() {
  const navigate = useNavigate();
  const location = useLocation();
  return (path: string) => void navigate(path, { state: { from: `${location.pathname}${location.search}` } });
}

/** 详情页返回目标：来源是同一列表时回到带筛选的列表，否则回到列表首页。 */
export function useBackTarget(listRoute: string): string {
  const location = useLocation();
  return backTarget(listRoute, (location.state as { from?: unknown } | null)?.from);
}

/**
 * 对账详情页骨架（AC-V1）：面包屑「财务 / 对账 / 列表 / 当前对象」、标题与操作、返回列表；
 * 主键不存在显示「未找到」并可返回列表（AC-01），加载失败显示重试。
 */
export function ReconDetailPage<T>({ list, objectLabel, title, state, actions, children }: {
  list: ListRef; objectLabel: string; title: ReactNode; state: ReconDetailState<T>; actions?: ReactNode; children: (item: T) => ReactNode;
}) {
  const t = useT('recon'), tNav = useT('nav');
  const navigate = useNavigate();
  const back = useBackTarget(list.route);
  const item = state.item;
  return <div className="configuration-page finance-page recon-detail">
    <Breadcrumb className="finance-breadcrumb" items={[
      { title: tNav('resource.OPERATIONS_FINANCE') }, { title: tNav('resource.OPERATIONS_FINANCE_RECON') }, { title: <Link to={back}>{list.label}</Link> },
      { title: <strong>{item ? title : objectLabel}</strong> },
    ]} />
    <div className="page-heading"><h1>{item ? title : objectLabel}</h1>
      <div className="recon-detail-actions">{item && actions}<Button onClick={() => void navigate(back)}>{t('detail.backToList')}</Button></div></div>
    {state.notFound ? <Result status="404" title={t('detail.notFound', { object: objectLabel })} subTitle={t('detail.notFoundHint')}
      extra={<Button type="primary" onClick={() => void navigate(back)}>{t('detail.backTo', { list: list.label })}</Button>} />
      : state.error && !item ? <LoadFailure message={state.error} onRetry={state.reload} />
      : !item ? <div className="content-loading"><Spin /></div>
      : children(item)}
  </div>;
}

/** 详情页小节标题（右侧可放操作）。 */
export function DetailSection({ title, extra, children }: { title: ReactNode; extra?: ReactNode; children: ReactNode }) {
  return <section className="finance-section">
    <div className="finance-section-head"><h2>{title}</h2>{extra && <><span className="finance-spacer" />{extra}</>}</div>
    {children}
  </section>;
}
