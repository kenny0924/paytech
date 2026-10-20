import { Table, Tabs, Tag } from 'antd';
import type { ConsoleResource } from '../api/types';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import type { CatalogCapability, CatalogFeeEvent, CatalogProduct } from '../commercial/commercialConsole';
import { CommercialNote, StatusTag } from '../commercial/CommercialParts';
import { useCatalogCapabilities, useCatalogFeeEvents, useCatalogProducts } from '../commercial/useCatalog';
import type { CatalogState } from '../commercial/useCatalog';
import { termTypeLabels } from '../commercial/commercial';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/** 目录读取失败时的统一展示。 */
function failed<T>(state: CatalogState<T>) {
  return state.failed ? <QueryFailure failure={undefined} /> : null;
}

/** 产品目录（只读）：产品、可开通项、计费事件三表，数据来自 commercial 目录接口（随产品发版维护）。 */
export function CommercialCatalog({ onAuthorizationChanged }: Props) {
  const products = useCatalogProducts(onAuthorizationChanged);
  const capabilities = useCatalogCapabilities(onAuthorizationChanged);
  const feeEvents = useCatalogFeeEvents(undefined, onAuthorizationChanged);
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>产品目录</h1><Tag>只读 · 随产品发版维护</Tag></div>
    <Tabs items={[
      { key: 'products', label: `产品 ${products.items.length}`, children: failed(products) ?? <Table<CatalogProduct> rowKey="productId" size="middle"
        pagination={false} loading={products.loading} dataSource={products.items} scroll={{ x: 760 }} columns={[
          { title: '产品代码', width: 170, render: (_, row) => <span className="mono">{row.productCode}</span> },
          { title: '名称', dataIndex: 'productName' },
          { title: '适用业务模式', dataIndex: 'businessModeScope', width: 140 },
          { title: '基础产品', width: 120, render: (_, row) => row.baseProduct ? <Tag color="blue">默认具备</Tag> : '可订阅' },
          { title: '状态', width: 100, render: (_, row) => <StatusTag status={row.status} /> },
        ]} /> },
      { key: 'capabilities', label: `可开通项 ${capabilities.items.length}`, children: failed(capabilities) ?? <Table<CatalogCapability>
        rowKey="productCapabilityId" size="middle" pagination={false} loading={capabilities.loading} dataSource={capabilities.items} scroll={{ x: 980 }} columns={[
          { title: '能力码', width: 200, render: (_, row) => <span className="mono">{row.capabilityCode}</span> },
          { title: '名称', dataIndex: 'capabilityName' },
          { title: '产品', dataIndex: 'productCode', width: 140 },
          { title: '种类', dataIndex: 'capabilityKind', width: 160 },
          { title: '支付方式', width: 120, render: (_, row) => row.paymentMethodCode ?? '—' },
          { title: '维度条件', width: 200, render: (_, row) => row.dimensionCode ? `${row.dimensionCode} = ${row.dimensionValue}` : '—' },
          { title: '状态', width: 100, render: (_, row) => <StatusTag status={row.status} /> },
        ]} /> },
      { key: 'fee-events', label: `计费事件 ${feeEvents.items.length}`, children: failed(feeEvents) ?? <Table<CatalogFeeEvent>
        rowKey="productFeeEventId" size="middle" pagination={false} loading={feeEvents.loading} dataSource={feeEvents.items} scroll={{ x: 760 }} columns={[
          { title: '事件代码', width: 200, render: (_, row) => <span className="mono">{row.feeEventCode}</span> },
          { title: '产品', dataIndex: 'productCode', width: 160 },
          { title: '条款类型', width: 200, render: (_, row) => termTypeLabels[row.termType] ?? row.termType },
          { title: '计费事实类型', dataIndex: 'billingFactType' },
        ]} /> },
    ]} />
    <CommercialNote>目录来自 commercial 的产品目录接口，与 commercial 初始化数据一致。合同订阅、开通项与价格编辑的下拉从这里取值：订阅只列启用的非基础产品，价格组件只列当前条款类型登记的计费事件。</CommercialNote>
  </div>;
}
