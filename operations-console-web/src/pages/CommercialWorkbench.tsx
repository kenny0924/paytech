import { Button, Card, Empty } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import { commercialPaths, commercialRoutes, localTime } from '../commercial/commercial';
import { dashboardMetricLabels, decodeDashboard } from '../commercial/commercialPreview';
import type { DashboardMetric } from '../commercial/commercialPreview';
import { CommercialNote } from '../commercial/CommercialParts';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/** 卡片对应的入口页：版本类去价目，合同类去合同。 */
const metricTargets: Record<DashboardMetric['metricCode'], string> = {
  PENDING_APPROVAL_VERSIONS: commercialRoutes.terms, SCHEDULED_VERSIONS: commercialRoutes.terms,
  PENDING_SIGNATURE_AGREEMENTS: commercialRoutes.agreements, ACTIVE_AGREEMENTS: commercialRoutes.agreements,
};

/** 待办清单：commercial 按状态列表接口接入后显示条目（I2）。 */
const worklists = [
  { key: 'approval', title: '待审批版本', note: '提交审批（PENDING_APPROVAL）的条款版本；审批人可直接批准或驳回。' },
  { key: 'scheduled', title: '已排期待生效', note: '已排期（SCHEDULED）且生效时间在未来的版本，按生效时间排序。' },
  { key: 'signature', title: '待签署合同', note: '已提交签署（PENDING_SIGNATURE）的合同，显示各参与方签署进度。' },
] as const;

/** 商务工作台：指标卡片与三个待办清单；commercial 列表接口接入前卡片显示「待接入」，不以 0 冒充没有待办。 */
export function CommercialWorkbench(_props: Props) {
  const navigate = useNavigate();
  const dashboard = useDetail(commercialPaths.dashboard, decodeDashboard);
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>商务工作台</h1>
      <div className="commercial-actions">
        <Button onClick={() => void navigate(commercialRoutes.rateCards)}>费率预览</Button>
        <Button onClick={() => void navigate(commercialRoutes.priceSimulator)}>价格试算</Button>
      </div>
    </div>
    {dashboard.loading && !dashboard.data ? <Loading /> : dashboard.failure || !dashboard.data
      ? <QueryFailure failure={dashboard.failure} retry={dashboard.refresh} />
      : <>
        <div className="commercial-kpis">{dashboard.data.metrics.map(metric =>
          <Card key={metric.metricCode} size="small" hoverable className="commercial-kpi" onClick={() => void navigate(metricTargets[metric.metricCode])}>
            <div className="commercial-kpi-label">{dashboardMetricLabels[metric.metricCode]}</div>
            <div className={`commercial-kpi-value${metric.count === null ? ' is-pending' : ''}`}>{metric.count ?? '待接入'}</div>
          </Card>)}
        </div>
        <div className="commercial-cell-meta">统计时点 {localTime(dashboard.data.asOf)}</div>
      </>}
    <div className="commercial-worklists">{worklists.map(list =>
      <section key={list.key} className="table-panel commercial-worklist">
        <div className="commercial-section-head"><h2>{list.title}</h2></div>
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="待接入：等待 commercial 按状态列表接口" />
        <div className="commercial-cell-meta">{list.note}</div>
      </section>)}
    </div>
    <CommercialNote>工作台的数量与清单来自 commercial 的合同 / 版本按状态列表（本需求 H 线新增，I2 接入）。接入前请从合同详情或按版本 ID 进入处理。</CommercialNote>
  </div>;
}
