import { Empty } from 'antd';
import type { ConsoleResource } from '../api/types';
import { CommercialNote } from '../commercial/CommercialParts';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/** 变更历史：合同、定价画像、条款方案的 BCR 时间线；BCR 读接口接入前显示「待接入」。 */
export function CommercialHistory(_props: Props) {
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>变更历史</h1></div>
    <section className="table-panel commercial-worklist">
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="待接入：等待 commercial 变更历史读接口" />
    </section>
    <CommercialNote>按聚合根（合同 / 画像 / 条款方案）展示操作者、时间、原因、版本号与相邻版本的字段级变化；接入后可从合同、画像与版本详情直接跳转（I2）。</CommercialNote>
  </div>;
}
