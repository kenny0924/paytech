import { Empty } from 'antd';
import type { ConsoleResource } from '../api/types';
import { CommercialNote } from '../commercial/CommercialParts';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/** 产品目录三表（只读）：产品、可开通项、计费事件。 */
const catalogTables = [
  { key: 'products', title: '产品', note: '产品代码、名称、适用业务模式与状态。' },
  { key: 'capabilities', title: '可开通项', note: '所属产品、支付方式、细分维度与默认约束。' },
  { key: 'fee-events', title: '计费事件', note: '计费事件代码、对应 billing 事实类型与可用的条款类型。' },
] as const;

/** 产品目录：只读展示 commercial 目录；目录读接口接入前显示「待接入」。 */
export function CommercialCatalog(_props: Props) {
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>产品目录</h1></div>
    {catalogTables.map(table => <section key={table.key} className="table-panel commercial-worklist">
      <div className="commercial-section-head"><h2>{table.title}</h2></div>
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="待接入：等待 commercial 目录读接口" />
      <div className="commercial-cell-meta">{table.note}</div>
    </section>)}
    <CommercialNote>目录数据以 commercial 初始化数据为准，运营只读；合同订阅、开通项与价格表单的下拉会改为从目录接口获取（I2）。</CommercialNote>
  </div>;
}
